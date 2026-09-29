package com.smarttask.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.smarttask.dto.TaskResponse;
import com.smarttask.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Generates downloadable reports of the user's tasks: a styled PDF (for
 * sharing / printing) and a plain CSV (for spreadsheets). Both include the
 * smart engine's output (score, quadrant) alongside the raw task fields.
 */
@Service
@RequiredArgsConstructor
public class ExportService {

    private final TaskService taskService;

    private static final Font TITLE_FONT = new Font(Font.HELVETICA, 20, Font.BOLD, new Color(28, 30, 38));
    private static final Font SUB_FONT = new Font(Font.HELVETICA, 10, Font.NORMAL, new Color(138, 142, 156));
    private static final Font HEAD_FONT = new Font(Font.HELVETICA, 9, Font.BOLD, Color.WHITE);
    private static final Font CELL_FONT = new Font(Font.HELVETICA, 9, Font.NORMAL, new Color(28, 30, 38));
    private static final Color BRAND = new Color(54, 84, 244);

    public byte[] generatePdfReport(User user) {
        List<TaskResponse> tasks = taskService.getAllTasksForUser(user);
        Document doc = new Document(PageSize.A4, 32, 32, 40, 36);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter.getInstance(doc, out);
            doc.open();

            Paragraph title = new Paragraph("Smart Task Report", TITLE_FONT);
            doc.add(title);
            String name = user.getFullName() != null && !user.getFullName().isBlank() ? user.getFullName() : user.getUsername();
            doc.add(new Paragraph(name + "  ·  generated " + java.time.LocalDate.now().format(DateTimeFormatter.ofPattern("MMM d, yyyy")), SUB_FONT));
            doc.add(Chunk.NEWLINE);

            long done = tasks.stream().filter(t -> "DONE".equals(t.getStatus().name())).count();
            long open = tasks.size() - done;
            long overdue = tasks.stream().filter(TaskResponse::isOverdue).count();

            PdfPTable summary = new PdfPTable(3);
            summary.setWidthPercentage(100);
            summary.addCell(summaryCell("Total tasks", String.valueOf(tasks.size())));
            summary.addCell(summaryCell("Open", String.valueOf(open)));
            summary.addCell(summaryCell("Overdue", String.valueOf(overdue)));
            doc.add(summary);
            doc.add(Chunk.NEWLINE);

            PdfPTable table = new PdfPTable(new float[]{3.2f, 1.1f, 1.1f, 1.3f, 1.4f, 0.9f});
            table.setWidthPercentage(100);
            for (String h : new String[]{"Title", "Priority", "Status", "Due date", "Quadrant", "Score"}) {
                PdfPCell c = new PdfPCell(new Phrase(h, HEAD_FONT));
                c.setBackgroundColor(BRAND);
                c.setPadding(6);
                c.setBorderColor(BRAND);
                table.addCell(c);
            }

            for (TaskResponse t : tasks) {
                table.addCell(bodyCell(t.getTitle()));
                table.addCell(bodyCell(t.getPriority().name()));
                table.addCell(bodyCell(t.getStatus().name().replace('_', ' ')));
                table.addCell(bodyCell(t.getDueDate() == null ? "—" : t.getDueDate().toString()));
                table.addCell(bodyCell(t.getQuadrant().replace('_', ' ')));
                table.addCell(bodyCell("DONE".equals(t.getStatus().name()) ? "—" : String.valueOf(Math.round(t.getSmartScore()))));
            }
            doc.add(table);

        } catch (DocumentException e) {
            throw new RuntimeException("Could not generate PDF report", e);
        } finally {
            doc.close();
        }

        return out.toByteArray();
    }

    public String generateCsvReport(User user) {
        List<TaskResponse> tasks = taskService.getAllTasksForUser(user);
        StringBuilder sb = new StringBuilder();
        sb.append("Title,Description,Priority,Status,Due Date,Category,Estimated Hours,Quadrant,Smart Score,Recurrence\n");
        for (TaskResponse t : tasks) {
            sb.append(csv(t.getTitle())).append(',')
              .append(csv(t.getDescription())).append(',')
              .append(t.getPriority()).append(',')
              .append(t.getStatus()).append(',')
              .append(t.getDueDate() == null ? "" : t.getDueDate()).append(',')
              .append(csv(t.getCategory())).append(',')
              .append(t.getEstimatedHours() == null ? "" : t.getEstimatedHours()).append(',')
              .append(t.getQuadrant()).append(',')
              .append(Math.round(t.getSmartScore())).append(',')
              .append(t.getRecurrence()).append('\n');
        }
        return sb.toString();
    }

    private PdfPCell summaryCell(String label, String value) {
        PdfPCell cell = new PdfPCell();
        cell.setPadding(8);
        cell.setBorderColor(new Color(227, 225, 217));
        Paragraph p = new Paragraph();
        p.add(new Chunk(value + "\n", new Font(Font.HELVETICA, 16, Font.BOLD, BRAND)));
        p.add(new Chunk(label, SUB_FONT));
        cell.addElement(p);
        return cell;
    }

    private PdfPCell bodyCell(String text) {
        PdfPCell c = new PdfPCell(new Phrase(text == null ? "" : text, CELL_FONT));
        c.setPadding(6);
        c.setBorderColor(new Color(227, 225, 217));
        return c;
    }

    private String csv(String value) {
        if (value == null) return "";
        String escaped = value.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }
}
