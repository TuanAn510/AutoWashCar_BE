# Project Report Notes

## Muc tieu

He thong phuc vu nghien cuu va thuc nghiem ve loyalty tier progression trong mo hinh wash car service. Backend ghi nhan booking, chi tieu, so lan ghe, diem tich luy, redemption va promotion usage de export du lieu cho giai doan RBL/Data Science.

## Kien truc

- React frontend co the tich hop sau qua REST API.
- Spring Boot backend xu ly auth, booking, loyalty, promotion va report.
- MySQL luu quan he du lieu chat che.
- Flyway quan ly schema version.
- He thong chi co 2 role chinh: `ROLE_CUSTOMER` va `ROLE_ADMIN`.

## Loyalty Engine

- Diem duoc cong khi admin cap nhat booking sang `COMPLETED`.
- Mac dinh `10,000 VND = 1 point`, cau hinh bang `LOYALTY_POINTS_AMOUNT_UNIT`.
- Tier duoc xet theo `lifetimePoints`.
- Scheduler ngay 1 hang thang cap nhat tier va xu ly diem het han.
- Diem earn het han sau 12 thang, cau hinh bang `LOYALTY_POINT_EXPIRY_MONTHS`.

## Booking Priority

Admin xem queue tai `GET /api/admin/bookings/priority-queue`.

Thu tu sap xep:

1. `priorityLevel` cua tier giam dan.
2. `scheduledAt` tang dan.

## RBL/Data Science

Endpoint CSV ban dau:

- `GET /api/admin/reports/export/bookings.csv`

Co the mo rong export:

- loyalty transactions
- customer tier progression
- promotion usage
- reward redemption behavior
