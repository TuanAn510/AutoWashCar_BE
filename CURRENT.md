# CURRENT - AutoWashCar_BE

Last updated: 2026-08-22

## Active project context

- Backend repo: `C:\Users\AD\AutoWashCar_BE`
- Current branch during session: `feat/notifications-polling`
- Remote: `https://github.com/TuanAn510/AutoWashCar_BE`
- Do not push directly to `main`/`master`; push feature work to the active feature branch unless user explicitly says otherwise.

## Recent backend state

- No backend code changes were made in this session.
- Backend repo was pulled; current branch was already up to date at the time.
- Backend was not pushed because there were no local changes.

## Local run notes

Default backend run initially failed because default profile uses SQL Server config and JPA/entityManager could not initialize without the expected DB.

Important default datasource config in `src/main/resources/application.properties`:

- `spring.datasource.url=${DB_URL:jdbc:sqlserver://localhost:1433;databaseName=wash_car_service;encrypt=false;trustServerCertificate=true;sendStringParametersAsUnicode=true}`
- `spring.datasource.username=${DB_USERNAME:sa}`
- `spring.datasource.password=${DB_PASSWORD:sa}`

Local profile file exists:

- `src/main/resources/application-local.properties`

Local profile config observed:

- Uses H2 memory DB: `jdbc:h2:mem:wash_car_service_local;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1`
- `spring.jpa.hibernate.ddl-auto=create-drop`
- `spring.flyway.enabled=false`
- `app.payment.frontend-url=http://localhost:3000`

Issue found:

- H2 dependency is declared with `<scope>test</scope>` in `pom.xml`, so normal `spring-boot:run -Dspring-boot.run.profiles=local` cannot load `org.h2.Driver`.

Working local run approach used:

```powershell
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local -Dspring-boot.run.useTestClasspath=true
```

Expected local URL:

- `http://localhost:8080/`

Latest checked local state in session:

- Port `8080` was listening.

Logs used:

- `C:\Users\AD\AutoWashCar_BE\autowash-be-run.log`
- `C:\Users\AD\AutoWashCar_BE\autowash-be-run.err.log`
- `C:\Users\AD\AutoWashCar_BE\autowash-be-local-run.log`
- `C:\Users\AD\AutoWashCar_BE\autowash-be-local-run.err.log`
- `C:\Users\AD\AutoWashCar_BE\autowash-be-local-testcp-run.log`
- `C:\Users\AD\AutoWashCar_BE\autowash-be-local-testcp-run.err.log`

## Related frontend change

Frontend notification UX improvements were completed and pushed in `AutoWashCar_FE`:

- Commit: `fca1206 feat(notifications): add draggable notification dock`
- Branch: `feat/notifications-polling`
- Summary: notification button is draggable, position persists, accidental open during drag is prevented, polling interval reduced from 10s to 5s.

## User preferences learned in this project

- User wants direct execution, not just instructions.
- User asked not to push to `main`; verify current branch before commit/push.
- User prefers fast feedback and local runnable URLs.
- For local backend testing, use local profile when user says "chạy bằng local".
