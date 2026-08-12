# API Documentation

Default base URL: `http://localhost:8080`

Interactive API documentation:

- Swagger UI: `/swagger-ui.html`
- OpenAPI JSON: `/v3/api-docs`
- Public/customer group: `/v3/api-docs/01-public-and-customer`
- Admin group: `/v3/api-docs/02-admin`
- Project report page: `/project-report.html`

Standard response format:

```json
{
  "success": true,
  "message": "Message",
  "data": {}
}
```

JWT header:

```text
Authorization: Bearer <token>
```

## Auth

`GET /api/health`

Public endpoint used to verify that the temporarily hosted backend is running.

`POST /api/auth/register`

```json
{
  "fullName": "John Nguyen",
  "phone": "0912345678",
  "password": "Password@123",
  "licensePlate": "59A12345",
  "brand": "Toyota",
  "model": "Vios",
  "color": "White",
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

Successful register/login responses include an access token and a refresh token:

```json
{
  "token": "jwt-access-token",
  "refreshToken": "refresh-token",
  "tokenType": "Bearer",
  "user": {}
}
```

`POST /api/auth/refresh`

Rotates a valid refresh token and returns a new access token plus a new refresh token. The previous refresh token is revoked.

```json
{
  "refreshToken": "existing-refresh-token"
}
```

`POST /api/auth/logout`

Revokes the provided refresh token.

```json
{
  "refreshToken": "refresh-token-to-revoke"
}
```

`GET /api/auth/me`

Requires a valid JWT.

Phone numbers are normalized before persistence and login lookup. For example, local digits and `+84` input resolve to the same stored phone where applicable.

## Admin Users

- `GET /api/admin/users?keyword=&role=&active=`
- `GET /api/admin/users/{id}`
- `PATCH /api/admin/users/{id}/status`
- `PATCH /api/admin/users/{id}/role`
- `POST /api/admin/users/{id}/reset-password`

Status request:

```json
{
  "active": false
}
```

Role request:

```json
{
  "role": "ROLE_ADMIN"
}
```

Password reset request:

```json
{
  "newPassword": "NewPassword@123"
}
```

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

Reward types:

- `DISCOUNT_CODE`: applies a fixed discount amount.
- `FREE_WASH`: discounts the whole booking subtotal.
- `ADD_ON`: adds the configured add-on service for free, or discounts it if the customer already selected it.

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
  "description": "Promotion for Silver tier customers",
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
  "note": "Morning car wash"
}
```

Admin:

- `GET /api/admin/bookings/today`
- `GET /api/admin/bookings/priority-queue`
- `PATCH /api/admin/bookings/{id}/status`

Status request:

```json
{
  "status": "CONFIRMED"
}
```

Status workflow:

- `PENDING -> CONFIRMED -> IN_QUEUE -> IN_PROGRESS -> COMPLETED`
- `PENDING`, `CONFIRMED`, and `IN_QUEUE` can be cancelled.
- `COMPLETED` and `CANCELLED` are terminal.

Availability response returns 08:00-17:00 slots in 30-minute intervals:

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

`reason` can be `PAST`, `OUT_OF_TIER_WINDOW`, or `BOOKED`.

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
