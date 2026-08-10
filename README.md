# Wash Car Service Spring Boot Backend

Backend Java Spring Boot + MySQL cho project wash car service, dat lich rua xe va loyalty tier progression.

## Stack

- Java 17
- Spring Boot 4.1.0
- Spring Web MVC, Spring Security, Spring Data JPA
- MySQL + Flyway migration
- JWT authentication
- Maven Wrapper, khong can cai Maven global

## Chay local

1. Tao database MySQL local, hoac de app tu tao database qua JDBC option `createDatabaseIfNotExist=true`.
2. Cau hinh bien moi truong neu khac mac dinh:

```powershell
$env:DB_URL="jdbc:mysql://localhost:3306/wash_car_service?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Ho_Chi_Minh"
$env:DB_USERNAME="root"
$env:DB_PASSWORD="your_mysql_password"
$env:JWT_SECRET="change-this-to-a-long-secret-at-least-32-bytes"
```

3. Chay backend:

```powershell
.\mvnw.cmd spring-boot:run
```

4. Tai khoan admin seed mac dinh:

```text
phone: 0900000000
password: Admin@123456
```

Co the doi bang `ADMIN_SEED_PHONE` va `ADMIN_SEED_PASSWORD`.

## Swagger va report hub

Sau khi backend chay:

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- Project report page: `http://localhost:8080/project-report.html`
- Project report JSON: `http://localhost:8080/api/project-report`

Trong Swagger UI, bam `Authorize` va nhap JWT theo dang:

```text
Bearer <token>
```

## Build va test

```powershell
.\mvnw.cmd test
.\mvnw.cmd package
```

## Module da co

- Auth: dang ky customer bang phone + license plate, login JWT, lay profile hien tai.
- Vehicle: customer quan ly xe cua minh.
- Catalog: service categories va services.
- Booking availability: tra slot trong 08:00-17:00 moi 30 phut cho form booking.
- Loyalty Engine: point balance, transactions, tier auto update, monthly review scheduler, point expiry 12 thang.
- Booking: dat lich, gioi han booking window theo tier, checkout discount, status lifecycle.
- Priority Queue: admin xem hang doi uu tien theo tier.
- Promotion: promotion theo tier, customer chi thay promotion phu hop.
- Rewards: customer doi diem lay voucher/free wash/add-on.
- Survey logs: ghi nhan page view/click/form submit/booking created de chay survey lay log.
- Dashboard/Report: overview va export booking CSV cho RBL/data science.

Project chi co 2 role chinh:

- `ROLE_CUSTOMER`
- `ROLE_ADMIN`

## Mapping yeu cau

| Yeu cau | Trang thai |
|---|---|
| Java Spring Boot backend | Da dung |
| MySQL local | Da cau hinh |
| Users phone + license plate | Da co trong register |
| ROLE_CUSTOMER va ROLE_ADMIN | Da co |
| Loyalty tracking | Da co |
| Auto-tiering monthly review | Da co scheduler ngay 1 hang thang |
| Point expiry 12 thang | Da co scheduler/logic expiry |
| Booking window Member/Silver/Gold/Platinum | Da seed 7/10/12/14 ngay |
| Priority queue theo tier | Da co endpoint admin |
| Promotion target theo tier | Da co |
| Bo payment online/refund | Khong tich hop payment gateway |
| Export data CSV/JSON cho RBL | Da co CSV booking, co the mo rong JSON |

## Tai lieu chi tiet

- [API.md](docs/API.md)
- [DATABASE.md](docs/DATABASE.md)
- [REPORT.md](docs/REPORT.md)
- [WEEK1_4.md](docs/WEEK1_4.md)
- [SWAGGER_REPORT.md](docs/SWAGGER_REPORT.md)
