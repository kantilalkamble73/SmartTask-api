# Smart Task Management System — Backend

A Spring Boot REST API with a built-in **smart prioritization engine**:

- **Smart Score (0–100)** — every task is scored from urgency (due date), importance
  (priority), and effort (estimated hours), weighted 45/35/20, with a small boost for
  tasks already in progress. See `service/SmartPriorityService.java`.
- **Eisenhower Quadrant** — every task is auto-classified as `DO_NOW`, `SCHEDULE`,
  `DELEGATE`, or `ELIMINATE` based on urgency × importance.
- **Next Best Task** — a dedicated endpoint recommends the single highest-value task
  to work on right now.
- **Analytics engine** — completion rate, current daily streak, 7-day productivity
  trend, and a human-readable "insight" sentence generated from the data.

## Tech stack
Java 17 · Spring Boot 3.2 · Spring Security + JWT · Spring Data JPA · PostgreSQL · Lombok · Maven

## 1. Create the database
```sql
CREATE DATABASE smart_task_db;
```

## 2. Configure credentials
Edit `src/main/resources/application.properties` if your PostgreSQL username/password
differ from the defaults (`postgres` / `postgres`).

## 3. Run
```bash
mvn spring-boot:run
```
The API starts on **http://localhost:8080**. Tables are auto-created (`ddl-auto=update`).

## API overview

| Method | Endpoint                     | Description                              | Auth |
|--------|-------------------------------|-------------------------------------------|------|
| POST   | `/api/auth/register`          | Create account, returns JWT               | No   |
| POST   | `/api/auth/login`              | Login, returns JWT                        | No   |
| GET    | `/api/tasks`                   | List tasks, sorted by smart score          | Yes  |
| POST   | `/api/tasks`                   | Create task                               | Yes  |
| PUT    | `/api/tasks/{id}`              | Update task                               | Yes  |
| PATCH  | `/api/tasks/{id}/status`       | Update only status (`{"status":"DONE"}`)  | Yes  |
| DELETE | `/api/tasks/{id}`              | Delete task                               | Yes  |
| GET    | `/api/tasks/next-best`         | Smart-recommended next task               | Yes  |
| GET    | `/api/tasks/overdue`           | All overdue tasks                         | Yes  |
| GET    | `/api/analytics/dashboard`     | Full analytics + insight                  | Yes  |

Send the JWT from login/register as `Authorization: Bearer <token>` on every
protected request.

## Project structure
```
src/main/java/com/smarttask/
  config/       Security + CORS configuration
  security/     JWT filter, util, UserDetails
  controller/   REST endpoints
  dto/          Request/response payloads
  entity/       JPA entities (User, Task, enums)
  repository/   Spring Data repositories
  service/      Business logic, incl. SmartPriorityService (the smart engine)
  exception/    Centralized error handling
```

## New in this version — extra smart features

| Method | Endpoint                          | Description                                              |
|--------|-------------------------------------|-------------------------------------------------------------|
| POST   | `/api/tasks/quick-add`              | Create a task from a sentence: `"Submit report friday high 2h #work"` |
| GET    | `/api/tasks/quick-add/preview?text=`| Live preview of how quick-add text will be parsed         |
| PATCH  | `/api/tasks/{id}/reschedule`        | Drag-and-drop style reschedule (`{"dueDate":"2026-10-05"}`)|
| GET    | `/api/planner/today?hours=6`        | Auto-generated time-boxed plan for today                  |
| GET    | `/api/export/pdf`                   | Download a styled PDF report of all tasks                 |
| GET    | `/api/export/csv`                   | Download a CSV of all tasks                                |
| POST   | `/api/demo/seed`                    | Seed the logged-in account with realistic sample tasks    |

- **Recurring tasks**: set `recurrence` (`DAILY`/`WEEKLY`/`MONTHLY`) on a task; completing it
  automatically creates the next occurrence (`TaskService.spawnNextOccurrence`).
- **Explainable scoring**: every `TaskResponse` now includes `urgencyScore`, `importanceScore`,
  `effortScore` and a plain-language `reason` string, so the UI can show *why* a task ranked where it did.
- **Quick-add NLP**: `QuickAddParser` is a dependency-free rule-based parser (dates, priority,
  duration, `#category`, recurrence) — no external NLP service required.
- **Daily planner**: `PlannerService` greedily schedules must-do (overdue/due-today) tasks first,
  fills remaining capacity by smart score, and slices any task over 4h into a 2h work block.
- **Productivity score**: a single 0-100 composite in `/api/analytics/dashboard` combining
  completion rate, streak, on-time rate and an overdue penalty.
