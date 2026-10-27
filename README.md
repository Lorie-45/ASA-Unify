# Asaunify

**An internal approvals platform where staff submit equipment, vehicle and loan requests (and memos), and managers approve them step by step.**

| Part | Folder | Tech | Runs on |
| --- | --- | --- | --- |
| Backend API | [asaunify/](asaunify) | Java 21, Spring Boot 3.5, PostgreSQL | http://localhost:8081 |
| Web app | [asaunify-fe/](asaunify-fe) | React 19, TypeScript, Vite, Tailwind | http://localhost:3000 |

## What it does

- Submit **equipment**, **vehicle** and **loan** requests, plus **memos**
- Multi-stage approvals based on the user's role (12 roles, such as `EMPLOYEE`, `DEPARTMENT_HEAD`, `FLEET_MANAGER`, `DRIVER`, `LOAN_OFFICER` and `ADMIN`)
- Real-time notifications (WebSocket)
- Reports and exports (Excel, CSV, PDF), an audit log, and user and department management
- Login with JWT, plus password reset by email

## Quick start

### Requirements

Install these first:

- [JDK 21](https://adoptium.net)
- [Node.js 20+](https://nodejs.org)
- [PostgreSQL 14+](https://www.postgresql.org/download/)

Maven isn't needed. The project includes `mvnw`.

### 1. Clone the repo

```bash
git clone <repository-url>
cd asaunify-project
```

### 2. Create the database

```sql
CREATE DATABASE asaunify;
```

The tables are created automatically when the backend starts.

### 3. Configure the backend

`application.properties` is gitignored, so create it from the template:

```bash
cd asaunify/src/main/resources
cp application-example.properties application.properties     # Windows: copy application-example.properties application.properties
```

Open `application.properties` and set at least:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/asaunify
spring.datasource.username=your_postgresql_username
spring.datasource.password=your_postgresql_password

jwt.secret=<32+ random characters>

seed.admin.email=your_admin_email@example.com
seed.admin.password=your_strong_admin_password
```

`seed.admin.email` and `seed.admin.password` are **required**. The app won't start without them, and no default exists in code.

### 4. Start the backend

```bash
cd asaunify
./mvnw spring-boot:run        # Windows: .\mvnw.cmd spring-boot:run
```

Wait for the log line `Started AsaunifyApplication`. The API is now at http://localhost:8081.

### 5. Start the frontend

Open a **second terminal**:

```bash
cd asaunify-fe
cp .env.example .env          # Windows: copy .env.example .env
npm install
npm run dev
```

Open http://localhost:3000.

### 6. Log in

On the first start, the backend creates an admin account from the `seed.admin.email` and `seed.admin.password` you set in step 3. Log in with those, and change the password afterwards. Then create other users and departments from **Admin → Users / Departments**.

## Configuration

Database and JWT settings can also be supplied as environment variables, which take precedence over `application.properties`:

| Variable | What it is | Default |
| --- | --- | --- |
| `DB_URL` | Database JDBC URL | `jdbc:postgresql://localhost:5433/asaunify` |
| `DB_USERNAME` / `DB_PASSWORD` | Database login | `postgres` / dev-only |
| `JWT_SECRET` | Token signing key (32+ random bytes) | dev-only |

The admin account has no environment variable or default. Set `seed.admin.email` and `seed.admin.password` in `application.properties` (see step 3).

Frontend variables are in `asaunify-fe/.env`:

```env
VITE_API_BASE_URL=http://localhost:8081/api
VITE_WS_URL=http://localhost:8081/ws
```

**Password-reset emails** need SMTP credentials (Gmail App Password by default). All settings, including SMTP, reset limits and allowed origins, are documented in [application-example.properties](asaunify/src/main/resources/application-example.properties).

**Production:** set real secrets and run with the `prod` profile. The app refuses to start on dev-default secrets in that mode.

## API docs

With the backend running, open Swagger UI at http://localhost:8081/swagger-ui.html.

## Useful commands

| Task | Command |
| --- | --- |
| Backend tests | `cd asaunify && ./mvnw test` |
| Build backend jar | `cd asaunify && ./mvnw clean package` |
| Frontend lint | `cd asaunify-fe && npm run lint` |
| Frontend production build | `cd asaunify-fe && npm run build` |

## Troubleshooting

| Problem | Fix |
| --- | --- |
| `Connection refused` or password error at backend start | Check PostgreSQL is running, then set `DB_URL`, `DB_USERNAME` and `DB_PASSWORD` (step 3). |
| Port 8081 or 3000 already in use | Stop the other process, or change `server.port` (backend) or `server.port` in `asaunify-fe/vite.config.ts` (frontend). |
| Frontend can't reach the API | Make sure the backend is running and `asaunify-fe/.env` exists. |
| No reset email arrives | SMTP credentials aren't configured. Delivery errors are logged by the backend only. |

## Project structure

```
asaunify-project/
├── asaunify/        Spring Boot backend
│   └── src/main/java/com/asa/asaunify/
│       ├── controllers/   REST endpoints
│       ├── services/      business logic, reports, exports, email
│       ├── workflow/      approval flows per request type
│       ├── config/        security, JWT, WebSocket, seeding
│       └── entity/ repos/ dtos/ enums/
└── asaunify-fe/     React web app
    └── src/
        ├── pages/         screens
        ├── components/    shared UI
        ├── api/           backend calls
        ├── store/         state (Zustand)
        └── routes/ hooks/ types/ utils/
```

## Contributing

Branch from `main` (`feature/<name>` or `fix/<name>`), run the backend tests and `npm run lint`, then open a pull request.
