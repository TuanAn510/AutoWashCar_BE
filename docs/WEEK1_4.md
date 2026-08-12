# Week 1-4 Scope

Week 1-4 objective: build the Spring Boot foundation, prepare integration points for React register/login/booking forms, and support temporary hosting so FPT students/staff can simulate booking clicks and generate survey logs.

## Backend Completed

- Spring Boot + SQL Server + Flyway.
- JWT authentication for customer/admin.
- Customer registration with normalized phone + license plate.
- Admin user management for search, account status, role changes, and password reset.
- Login returns a JWT token.
- Public catalog services for the booking form.
- Booking form APIs:
  - `GET /api/bookings/availability?date=YYYY-MM-DD`
  - `POST /api/bookings`
  - `GET /api/bookings/my`
- Survey log APIs:
  - `POST /api/survey/logs` public
  - `GET /api/admin/survey/logs` admin
- Health check:
  - `GET /api/health`
- Swagger/report presentation:
  - `GET /swagger-ui.html`
  - `GET /project-report.html`
- CORS is configurable through `CORS_ALLOWED_ORIGINS`.

## Important Database Constraints

- All tables use `id BIGINT IDENTITY(1,1)` as the primary key.
- User phone is unique.
- Vehicle license plate is unique for active vehicles.
- Booking `scheduled_at` is unique only for active bookings; a cancelled booking releases the slot for rebooking.
- Loyalty points are tracked through point lots, preserving original earn history while supporting FIFO redemption and expiry.
- Monthly loyalty review can upgrade or downgrade tiers based on the review window.
- User-management changes are captured in `audit_logs`.
- Foreign keys connect users, vehicles, bookings, services, loyalty, promotions, rewards, and survey logs.
- Check constraints cover:
  - customer/admin roles
  - booking status
  - promotion/reward/loyalty transaction types
  - non-negative price, points, and duration
  - promotion end date after start date

## Week 1-4 Demo Flow

1. Frontend calls `GET /api/health` to verify the backend.
2. User opens the register page; frontend calls `POST /api/survey/logs` with `PAGE_VIEW`.
3. User registers through `POST /api/auth/register`.
4. User logs in through `POST /api/auth/login`.
5. Booking form loads services through `GET /api/catalog/services`.
6. Booking form loads slots through `GET /api/bookings/availability?date=YYYY-MM-DD`.
7. User submits a booking through `POST /api/bookings`.
8. Frontend records `BOOKING_CREATED` through `POST /api/survey/logs`.
9. Admin views logs through `GET /api/admin/survey/logs`.
10. The team presents API/reporting through `/project-report.html` and `/swagger-ui.html`.

## Temporary Hosting

Required environment variables:

```powershell
$env:DB_URL="jdbc:sqlserver://localhost:1433;databaseName=wash_car_service;encrypt=false;trustServerCertificate=true"
$env:DB_USERNAME="sa"
$env:DB_PASSWORD="your_sql_server_password"
$env:JWT_SECRET="change-this-to-a-long-secret-at-least-32-bytes"
$env:CORS_ALLOWED_ORIGINS="http://localhost:3000,https://your-temporary-frontend.example"
```

Run with Maven Wrapper:

```powershell
.\mvnw.cmd spring-boot:run
```

Or build with Docker:

```powershell
docker build -t wash-car-service-server .
docker run -p 8080:8080 --env DB_URL="jdbc:sqlserver://host.docker.internal:1433;databaseName=wash_car_service;encrypt=false;trustServerCertificate=true" --env DB_USERNAME=sa --env DB_PASSWORD=your_sql_server_password --env JWT_SECRET=change-this-to-a-long-secret-at-least-32-bytes wash-car-service-server
```
