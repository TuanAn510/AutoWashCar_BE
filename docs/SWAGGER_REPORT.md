# Swagger And Project Report Tool

Muc tieu: co mot cong cu truc quan de trinh bay API va noi dung bao cao project trong giai doan demo.

## Cong cu da them

- Swagger UI tu springdoc-openapi.
- OpenAPI JSON/YAML tu runtime Spring Boot.
- Project report page dang HTML.
- Project report JSON endpoint de frontend hoac cong cu khac co the doc.

## URL khi chay local

```text
http://localhost:8080/swagger-ui.html
http://localhost:8080/v3/api-docs
http://localhost:8080/project-report.html
http://localhost:8080/api/project-report
```

## Cach dung Swagger khi demo

1. Chay backend.
2. Mo `http://localhost:8080/swagger-ui.html`.
3. Login bang `POST /api/auth/login`.
4. Copy token trong response.
5. Bam `Authorize`.
6. Nhap:

```text
Bearer <token>
```

7. Goi thu cac API:
   - `GET /api/health`
   - `GET /api/catalog/services`
   - `GET /api/bookings/availability?date=YYYY-MM-DD`
   - `POST /api/bookings`
   - `POST /api/survey/logs`

## Noi dung report page

`/project-report.html` trinh bay:

- Architecture.
- Week 1-4 features.
- Database constraints.
- Temporary hosting readiness.
- Link nhanh sang Swagger UI va OpenAPI groups.

## OpenAPI groups

- `01-public-and-customer`: auth, catalog, customer booking, survey log public, report public.
- `02-admin`: admin catalog, booking status, survey logs, dashboard, reports.

## Dependency

Theo tai lieu springdoc chinh thuc, Swagger UI duoc kich hoat bang:

```xml
<dependency>
  <groupId>org.springdoc</groupId>
  <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
  <version>3.1.0</version>
</dependency>
```
