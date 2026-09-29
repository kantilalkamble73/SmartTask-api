package com.smarttask.service;

import com.smarttask.dto.TaskRequest;
import com.smarttask.entity.Priority;
import com.smarttask.entity.Recurrence;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rule-based natural language parser for the quick-add bar.
 *
 * "Submit report friday high 2h #work"  ->  title=Submit report, due=next Friday,
 *                                            priority=HIGH, estimate=2h, category=Work
 *
 * Supported tokens: dates (today, tomorrow, next week, weekday names, in 3 days,
 * yyyy-MM-dd), priority (critical/urgent/asap, high/important, low, medium, p1-p4, !!, !!!),
 * duration (2h, 1.5 hours, 45min), category (#tag) and recurrence (daily, weekly,
 * monthly, every monday). Anything left over becomes the title.
 */
@Service
public class QuickAddParser {

    private static final int CI = Pattern.CASE_INSENSITIVE;
    private static final String PRE = "(?:(?:by|due|on|for)\\s+)?";
    private static final String DAYS = "(monday|tuesday|wednesday|thursday|friday|saturday|sunday)";

    private static final Pattern TAG = Pattern.compile("(?<!\\w)#([\\w-]+)");
    private static final Pattern EVERY_DAY_NAME = Pattern.compile("\\bevery\\s+" + DAYS + "\\b", CI);
    private static final Pattern RECUR_DAILY = Pattern.compile("\\b(every\\s*day|daily)\\b", CI);
    private static final Pattern RECUR_WEEKLY = Pattern.compile("\\b(every\\s*week|weekly)\\b", CI);
    private static final Pattern RECUR_MONTHLY = Pattern.compile("\\b(every\\s*month|monthly)\\b", CI);

    private static final Pattern ISO_DATE = Pattern.compile("\\b" + PRE + "(\\d{4}-\\d{2}-\\d{2})\\b", CI);
    private static final Pattern IN_N = Pattern.compile("\\bin\\s+(\\d+)\\s+(day|days|week|weeks)\\b", CI);
    private static final Pattern TOMORROW = Pattern.compile("\\b" + PRE + "(tomorrow|tmrw|tmr)\\b", CI);
    private static final Pattern TODAY = Pattern.compile("\\b" + PRE + "(today|tonight|eod)\\b", CI);
    private static final Pattern NEXT_WEEK = Pattern.compile("\\b" + PRE + "next\\s+week\\b", CI);
    private static final Pattern NEXT_MONTH = Pattern.compile("\\b" + PRE + "next\\s+month\\b", CI);
    private static final Pattern WEEKEND = Pattern.compile("\\b" + PRE + "(?:this\\s+)?weekend\\b", CI);
    private static final Pattern FULL_DAY = Pattern.compile("\\b(?:(next|on|by|due|for)\\s+)?" + DAYS + "\\b", CI);
    private static final Pattern ABBR_DAY = Pattern.compile("\\b(next|on|by)\\s+(mon|tues|tue|wed|thurs|thur|thu|fri|sat|sun)\\b", CI);

    private static final Pattern HOURS = Pattern.compile("\\b(\\d+(?:\\.\\d+)?)\\s*(?:h|hr|hrs|hour|hours)\\b", CI);
    private static final Pattern MINUTES = Pattern.compile("\\b(\\d+)\\s*(?:m|min|mins|minute|minutes)\\b", CI);

    private static final Pattern PRIORITY_P = Pattern.compile("\\bp([1-4])\\b", CI);
    private static final Pattern CRITICAL = Pattern.compile("\\b(critical|urgent|asap)\\b", CI);
    private static final Pattern HIGH = Pattern.compile("\\b(high|important)\\b", CI);
    private static final Pattern LOW = Pattern.compile("\\b(low|minor)\\b", CI);
    private static final Pattern MEDIUM = Pattern.compile("\\bmedium\\b", CI);

    /** Never throws for odd input: an unparseable string simply yields an empty title. */
    public TaskRequest parse(String input) {
        TaskRequest req = new TaskRequest();
        req.setTitle("");
        if (input == null || input.isBlank()) return req;

        String rest = " " + input.trim() + " ";
        LocalDate date = null;
        Matcher m;

        // ---- category ----
        m = TAG.matcher(rest);
        if (m.find()) {
            req.setCategory(capitalize(m.group(1).replace('-', ' ')));
            rest = m.replaceFirst(" ");
        }

        // ---- recurrence ----
        Recurrence rec = null;
        m = EVERY_DAY_NAME.matcher(rest);
        if (m.find()) {
            rec = Recurrence.WEEKLY;
            date = nextWeekday(dayOf(m.group(1)), false);
            rest = m.replaceFirst(" ");
        }
        if (rec == null && (m = RECUR_DAILY.matcher(rest)).find()) {
            rec = Recurrence.DAILY;
            rest = m.replaceFirst(" ");
        }
        if (rec == null && (m = RECUR_WEEKLY.matcher(rest)).find()) {
            rec = Recurrence.WEEKLY;
            rest = m.replaceFirst(" ");
        }
        if (rec == null && (m = RECUR_MONTHLY.matcher(rest)).find()) {
            rec = Recurrence.MONTHLY;
            rest = m.replaceFirst(" ");
        }
        if (rec != null) req.setRecurrence(rec);

        // ---- due date ----
        if (date == null && (m = ISO_DATE.matcher(rest)).find()) {
            try {
                date = LocalDate.parse(m.group(1));
                rest = m.replaceFirst(" ");
            } catch (DateTimeParseException ignored) {
                // leave the text in the title
            }
        }
        if (date == null && (m = IN_N.matcher(rest)).find()) {
            int n = Integer.parseInt(m.group(1));
            date = m.group(2).toLowerCase().startsWith("week")
                    ? LocalDate.now().plusWeeks(n) : LocalDate.now().plusDays(n);
            rest = m.replaceFirst(" ");
        }
        if (date == null && (m = TOMORROW.matcher(rest)).find()) {
            date = LocalDate.now().plusDays(1);
            rest = m.replaceFirst(" ");
        }
        if (date == null && (m = TODAY.matcher(rest)).find()) {
            date = LocalDate.now();
            rest = m.replaceFirst(" ");
        }
        if (date == null && (m = NEXT_WEEK.matcher(rest)).find()) {
            date = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
            rest = m.replaceFirst(" ");
        }
        if (date == null && (m = NEXT_MONTH.matcher(rest)).find()) {
            date = LocalDate.now().plusMonths(1);
            rest = m.replaceFirst(" ");
        }
        if (date == null && (m = WEEKEND.matcher(rest)).find()) {
            date = LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));
            rest = m.replaceFirst(" ");
        }
        if (date == null && (m = FULL_DAY.matcher(rest)).find()) {
            boolean next = "next".equalsIgnoreCase(m.group(1));
            date = nextWeekday(dayOf(m.group(2)), next);
            rest = m.replaceFirst(" ");
        }
        if (date == null && (m = ABBR_DAY.matcher(rest)).find()) {
            boolean next = "next".equalsIgnoreCase(m.group(1));
            date = nextWeekday(dayOf(m.group(2)), next);
            rest = m.replaceFirst(" ");
        }
        req.setDueDate(date);

        // ---- estimate ----
        if ((m = HOURS.matcher(rest)).find()) {
            req.setEstimatedHours(Double.parseDouble(m.group(1)));
            rest = m.replaceFirst(" ");
        } else if ((m = MINUTES.matcher(rest)).find()) {
            double hrs = Integer.parseInt(m.group(1)) / 60.0;
            req.setEstimatedHours(Math.round(hrs * 100.0) / 100.0);
            rest = m.replaceFirst(" ");
        }

        // ---- priority ----
        Priority priority = null;
        if (rest.contains("!!!")) {
            priority = Priority.CRITICAL;
            rest = rest.replace("!!!", " ");
        } else if (rest.contains("!!")) {
            priority = Priority.HIGH;
            rest = rest.replace("!!", " ");
        }
        if (priority == null && (m = PRIORITY_P.matcher(rest)).find()) {
            priority = switch (m.group(1)) {
                case "1" -> Priority.CRITICAL;
                case "2" -> Priority.HIGH;
                case "3" -> Priority.MEDIUM;
                default -> Priority.LOW;
            };
            rest = m.replaceFirst(" ");
        }
        if (priority == null && (m = CRITICAL.matcher(rest)).find()) {
            priority = Priority.CRITICAL;
            rest = m.replaceFirst(" ");
        }
        if (priority == null && (m = HIGH.matcher(rest)).find()) {
            priority = Priority.HIGH;
            rest = m.replaceFirst(" ");
        }
        if (priority == null && (m = LOW.matcher(rest)).find()) {
            priority = Priority.LOW;
            rest = m.replaceFirst(" ");
        }
        if (priority == null && (m = MEDIUM.matcher(rest)).find()) {
            priority = Priority.MEDIUM;
            rest = m.replaceFirst(" ");
        }
        req.setPriority(priority);

        // ---- whatever is left is the title ----
        String title = rest.replaceAll("\\s+", " ").trim();
        title = title.replaceAll("^[\\-,:;]+\\s*|\\s*[\\-,:;]+$", "");
        title = title.replaceAll("(?i)\\s+(by|due|on|at|for|in|this|next)$", "").trim();
        req.setTitle(capitalize(title));
        return req;
    }

    private static LocalDate nextWeekday(DayOfWeek day, boolean forceNextWeek) {
        LocalDate d = LocalDate.now().with(TemporalAdjusters.next(day));
        return forceNextWeek ? d.plusWeeks(1) : d;
    }

    private static DayOfWeek dayOf(String s) {
        return switch (s.toLowerCase().substring(0, 3)) {
            case "mon" -> DayOfWeek.MONDAY;
            case "tue" -> DayOfWeek.TUESDAY;
            case "wed" -> DayOfWeek.WEDNESDAY;
            case "thu" -> DayOfWeek.THURSDAY;
            case "fri" -> DayOfWeek.FRIDAY;
            case "sat" -> DayOfWeek.SATURDAY;
            default -> DayOfWeek.SUNDAY;
        };
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
