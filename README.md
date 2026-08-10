# Wash Car Service Backend

Spring Boot backend for a wash car service project. The system focuses on two roles, `CUSTOMER` and `ADMIN`, and supports registration, login, vehicle management, booking, service catalog management, loyalty tiers, rewards, promotions, survey logs, Swagger API documentation, and a project report page.

This repository is written and documented in English.

## Technology Stack

- Java 17
- Spring Boot 4.1.0
- Spring Web MVC
- Spring Security + JWT
- Spring Data JPA
- MySQL
- Flyway database migration
- springdoc-openapi Swagger UI
- Maven Wrapper

## Project Scope

The current implementation prioritizes the week 1-4 project milestone:

- Build the Spring Boot backend foundation.
- Prepare APIs for React registration, login, and booking forms.
- Use local MySQL as the main database.
- Provide Swagger UI for API presentation.
- Provide a report page for project demonstration.
- Support temporary hosting so users can simulate bookings and generate survey logs.

The frontend is intentionally not modified in this backend workspace.

## Roles

- `ROLE_CUSTOMER`: registers, logs in, manages vehicles, checks available slots, creates bookings, views loyalty data, redeems rewards, and sees eligible promotions.
- `ROLE_ADMIN`: manages catalog data, membership tiers, rewards, promotions, booking status, survey logs, reports, and dashboard metrics.

## Local Setup

1. Create a local MySQL database, or let the application create it through the JDBC option `createDatabaseIfNotExist=true`.
2. Configure environment variables if your local values differ from the defaults:

```powershell
$env:DB_URL="jdbc:mysql://localhost:3306/wash_car_service?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Ho_Chi_Minh"
$env:DB_USERNAME="root"
$env:DB_PASSWORD="your_mysql_password"
$env:JWT_SECRET="change-this-to-a-long-secret-at-least-32-bytes"
```

3. Start the backend:

```powershell
.\mvnw.cmd spring-boot:run
```

## Default Admin Account

```text
phone: 0900000000
password: Admin@123456
```

The default admin account can be changed with:

- `ADMIN_SEED_PHONE`
- `ADMIN_SEED_PASSWORD`

## Swagger And Project Report

After the backend starts, open these URLs:

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- Public/customer OpenAPI group: `http://localhost:8080/v3/api-docs/01-public-and-customer`
- Admin OpenAPI group: `http://localhost:8080/v3/api-docs/02-admin`
- Project report page: `http://localhost:8080/project-report.html`
- Project report JSON: `http://localhost:8080/api/project-report`

To test secured endpoints in Swagger UI:

1. Call `POST /api/auth/login`.
2. Copy the returned token.
3. Click `Authorize`.
4. Enter the token in this format:

```text
Bearer <token>
```

## Build And Test

```powershell
.\mvnw.cmd test
.\mvnw.cmd package
```

## Main Modules

- Auth: customer registration, admin/customer login, JWT authentication, current user profile.
- Vehicle: customer-owned vehicle management.
- Catalog: service categories and car wash services.
- Booking: slot availability, booking creation, booking history, status lifecycle.
- Loyalty: point balance, earned/redeemed/expired transactions, automatic tier updates.
- Rewards: point redemption for vouchers, free wash, or add-on rewards.
- Promotions: tier-targeted discounts with active dates and usage limits.
- Survey Logs: public event logging for temporary survey/testing sessions.
- Reports: admin dashboard metrics and booking CSV export.
- Project Report: static HTML and JSON report endpoints for presentation.

## Database Summary

The schema is managed by Flyway in:

```text
src/main/resources/db/migration/V1__init_schema.sql
```

Main tables:

- `users`
- `vehicles`
- `service_categories`
- `services`
- `membership_tiers`
- `loyalty_accounts`
- `loyalty_transactions`
- `promotions`
- `rewards`
- `reward_redemptions`
- `bookings`
- `booking_services`
- `survey_event_logs`

Important constraints:

- Primary keys are defined on all tables.
- User phone numbers are unique.
- Vehicle license plates are unique.
- Booking slots are unique in the week 1-4 prototype.
- Foreign keys connect users, vehicles, bookings, services, loyalty, promotions, rewards, and survey logs.
- Check constraints protect roles, statuses, enum values, price values, point values, durations, and promotion date ranges.

## Requirement Mapping

| Requirement | Status |
|---|---|
| Spring Boot backend | Implemented |
| Local MySQL database | Implemented |
| Two main roles: customer and admin | Implemented |
| Customer registration and login | Implemented |
| Booking form API support | Implemented |
| Survey log collection | Implemented |
| Swagger UI documentation | Implemented |
| Project report page | Implemented |
| Database primary keys and constraints | Implemented |
| Loyalty tracking and tier progression | Implemented |
| Reward redemption | Implemented |
| Tier-targeted promotions | Implemented |
| Admin dashboard and CSV export | Implemented |

## Documentation

- [API Documentation](docs/API.md)
- [Database Design](docs/DATABASE.md)
- [Project Report Notes](docs/REPORT.md)
- [Week 1-4 Scope](docs/WEEK1_4.md)
- [Swagger And Project Report Tool](docs/SWAGGER_REPORT.md)
