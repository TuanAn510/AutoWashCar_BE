# Swagger And Project Report Tool

Objective: provide a visual tool for presenting project APIs and report content during demos.

## Tools Added

- Swagger UI powered by springdoc-openapi.
- OpenAPI JSON/YAML generated from the running Spring Boot application.
- Static project report page in HTML.
- Project report JSON endpoint that a frontend or other tool can consume.

## Local URLs

```text
http://localhost:8080/swagger-ui.html
http://localhost:8080/v3/api-docs
http://localhost:8080/project-report.html
http://localhost:8080/api/project-report
```

## How To Use Swagger During Demo

1. Start the backend.
2. Open `http://localhost:8080/swagger-ui.html`.
3. Log in through `POST /api/auth/login`.
4. Copy the token from the response.
5. Click `Authorize`.
6. Enter:

```text
Bearer <token>
```

7. Try these APIs:
   - `GET /api/health`
   - `GET /api/catalog/services`
   - `GET /api/bookings/availability?date=YYYY-MM-DD`
   - `POST /api/bookings`
   - `POST /api/survey/logs`

## Report Page Content

`/project-report.html` presents:

- Architecture.
- Week 1-4 features.
- Database constraints.
- Temporary hosting readiness.
- Quick links to Swagger UI and OpenAPI groups.

## OpenAPI Groups

- `01-public-and-customer`: auth, catalog, customer booking, public survey log, public report.
- `02-admin`: admin catalog, booking status, survey logs, dashboard, reports.

## Dependency

Swagger UI is enabled by this dependency:

```xml
<dependency>
  <groupId>org.springdoc</groupId>
  <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
  <version>3.1.0</version>
</dependency>
```
