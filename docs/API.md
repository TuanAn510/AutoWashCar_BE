# API Documentation

Base URL mac dinh: `http://localhost:8080`

Interactive API documentation:

- Swagger UI: `/swagger-ui.html`
- OpenAPI JSON: `/v3/api-docs`
- Public/customer group: `/v3/api-docs/01-public-and-customer`
- Admin group: `/v3/api-docs/02-admin`
- Project report page: `/project-report.html`

Response JSON chuan:

```json
{
  "success": true,
  "message": "Message",
  "data": {}
}
```

Dung JWT:

```text
Authorization: Bearer <token>
```

## Auth

`GET /api/health`

Public endpoint dung de kiem tra backend khi host tam.

`POST /api/auth/register`

```json
{
  "fullName": "Nguyen Van A",
  "phone": "0912345678",
  "password": "Password@123",
  "licensePlate": "59A12345",
  "brand": "Toyota",
  "model": "Vios",
  "color": "Trang",
  "manufactureYear": 2022
}
```

`POST /api/auth/login`

```json
{
  "phone": "0900000000",
  "password": "Admin@123456"
}
```

`GET /api/auth/me`

## Catalog

Public:

- `GET /api/catalog/categories`
- `GET /api/catalog/services`

Admin:

- `POST /api/admin/catalog/categories`
- `PUT /api/admin/catalog/categories/{id}`
- `POST /api/admin/catalog/services`
- `PUT /api/admin/catalog/services/{id}`

## Vehicles

- `GET /api/vehicles/my`
- `POST /api/vehicles`

## Loyalty

Customer:

- `GET /api/loyalty/me`
- `GET /api/loyalty/me/transactions`
- `GET /api/loyalty/tiers`

Admin:

- `POST /api/admin/loyalty/tiers`
- `PUT /api/admin/loyalty/tiers/{id}`

Tier request:

```json
{
  "name": "Gold",
  "minPoints": 1500,
  "discountPercent": 10,
  "bookingWindowDays": 12,
  "priorityLevel": 2,
  "description": "Gold member",
  "active": true
}
```

## Rewards

Customer:

- `GET /api/rewards`
- `POST /api/rewards/{rewardId}/redeem`
- `GET /api/rewards/my-redemptions`

Admin:

- `POST /api/admin/rewards`
- `PUT /api/admin/rewards/{id}`

## Promotions

Customer:

- `GET /api/promotions/active`

Admin:

- `GET /api/admin/promotions`
- `POST /api/admin/promotions`
- `PUT /api/admin/promotions/{id}`

Promotion request:

```json
{
  "code": "SILVER10",
  "title": "Silver plus",
  "description": "Khuyen mai cho tier Silver",
  "discountType": "PERCENTAGE",
  "discountValue": 10,
  "targetTierId": 2,
  "startAt": "2026-08-11T00:00:00",
  "endAt": "2026-12-31T23:59:59",
  "usageLimit": 100,
  "active": true
}
```

## Bookings

Customer:

- `POST /api/bookings`
- `GET /api/bookings/my`
- `GET /api/bookings/availability?date=2026-08-15`

Booking request:

```json
{
  "vehicleId": 1,
  "serviceIds": [1, 2],
  "scheduledAt": "2026-08-15T09:00:00",
  "promotionId": null,
  "rewardRedemptionId": null,
  "note": "Rua xe buoi sang"
}
```

Admin:

- `GET /api/admin/bookings/today`
- `GET /api/admin/bookings/priority-queue`
- `PATCH /api/admin/bookings/{id}/status`

Status request:

```json
{
  "status": "COMPLETED"
}
```

Availability response tra slot 08:00-17:00, cach nhau 30 phut:

```json
{
  "date": "2026-08-15",
  "bookingWindowDays": 7,
  "slots": [
    {
      "startAt": "2026-08-15T08:00:00",
      "available": true,
      "reason": null
    }
  ]
}
```

`reason` co the la `PAST`, `OUT_OF_TIER_WINDOW`, hoac `BOOKED`.

## Survey Logs

Public:

- `POST /api/survey/logs`

```json
{
  "sessionKey": "survey-session-001",
  "eventType": "PAGE_VIEW",
  "page": "/booking",
  "action": "open_booking_form",
  "metadataJson": "{\"source\":\"fpt-survey\"}"
}
```

Admin:

- `GET /api/admin/survey/logs`

## Reports

Admin:

- `GET /api/admin/dashboard/overview`
- `GET /api/admin/reports/export/bookings.csv`

Public presentation:

- `GET /api/project-report`
- `GET /project-report.html`
