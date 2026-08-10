# Week 1-4 Scope

Muc tieu tuan 1-4: dung khung Spring Boot, san sang tich hop React dang ky/dang nhap/form booking, host tam de sinh vien/nhan vien FPT click dat lich gia lap va thu log.

## Da hoan thien phia backend

- Spring Boot + MySQL + Flyway.
- JWT auth cho customer/admin.
- Register customer bang phone + license plate.
- Login tra JWT token.
- Catalog services public cho booking form.
- Booking form API:
  - `GET /api/bookings/availability?date=YYYY-MM-DD`
  - `POST /api/bookings`
  - `GET /api/bookings/my`
- Survey log API:
  - `POST /api/survey/logs` public
  - `GET /api/admin/survey/logs` admin
- Health check:
  - `GET /api/health`
- Swagger/report presentation:
  - `GET /swagger-ui.html`
  - `GET /project-report.html`
- CORS cau hinh bang `CORS_ALLOWED_ORIGINS`.

## Ràng buộc database quan trọng

- Tat ca bang co primary key `id BIGINT AUTO_INCREMENT`.
- Phone user unique.
- License plate unique.
- Booking `scheduled_at` unique cho prototype mot slot mot booking.
- Foreign key day du giua user, vehicle, booking, services, loyalty va survey logs.
- Check constraints cho:
  - role customer/admin
  - booking status
  - promotion/reward/loyalty transaction type
  - price, point, duration khong am
  - promotion end date sau start date

## Flow demo tuan 1-4

1. Frontend goi `GET /api/health` de kiem tra backend.
2. User mo trang dang ky, frontend goi `POST /api/survey/logs` voi `PAGE_VIEW`.
3. User dang ky bang `POST /api/auth/register`.
4. User login bang `POST /api/auth/login`.
5. Form booking lay services bang `GET /api/catalog/services`.
6. Form booking lay slot bang `GET /api/bookings/availability?date=YYYY-MM-DD`.
7. User submit booking bang `POST /api/bookings`.
8. Frontend ghi `BOOKING_CREATED` qua `POST /api/survey/logs`.
9. Admin xem log bang `GET /api/admin/survey/logs`.
10. Nhom thuyet trinh dung `/project-report.html` va `/swagger-ui.html` de trinh bay API/bao cao.

## Host tam

Bien moi truong can thiet:

```powershell
$env:DB_URL="jdbc:mysql://localhost:3306/wash_car_service?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Ho_Chi_Minh"
$env:DB_USERNAME="root"
$env:DB_PASSWORD="your_mysql_password"
$env:JWT_SECRET="change-this-to-a-long-secret-at-least-32-bytes"
$env:CORS_ALLOWED_ORIGINS="http://localhost:5173,https://your-temporary-frontend.example"
```

Chay bang Maven Wrapper:

```powershell
.\mvnw.cmd spring-boot:run
```

Hoac build Docker:

```powershell
docker build -t wash-car-service-server .
docker run -p 8080:8080 --env DB_URL="jdbc:mysql://host.docker.internal:3306/wash_car_service?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Ho_Chi_Minh" --env DB_USERNAME=root --env DB_PASSWORD=your_mysql_password --env JWT_SECRET=change-this-to-a-long-secret-at-least-32-bytes wash-car-service-server
```
