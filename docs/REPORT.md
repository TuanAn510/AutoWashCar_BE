# Project Report Notes

## Objective

The system supports research and experimentation on loyalty tier progression in a wash car service model. The backend records bookings, spending, visit count, earned points, redemptions, and promotion usage so the team can export data for the RBL/Data Science phase.

## Architecture

- React frontend can integrate later through REST APIs.
- Spring Boot backend handles authentication, booking, loyalty, promotion, and reports.
- SQL Server stores strongly related business data.
- Flyway manages versioned database schema changes.
- The system has two primary roles: `ROLE_CUSTOMER` and `ROLE_ADMIN`.

## Loyalty Engine

- Points are earned when an admin updates a booking to `COMPLETED`.
- Default rule: `10,000 VND = 1 point`, configurable through `LOYALTY_POINTS_AMOUNT_UNIT`.
- Monthly tier review is based on the configured review/expiry window, so customers can be upgraded or downgraded.
- A scheduler runs on the first day of every month to refresh customer tiers, expire old point lots, and expire unused reward redemptions.
- Earned points expire after 12 months, configurable through `LOYALTY_POINT_EXPIRY_MONTHS`.
- Point expiry is ledger-based: original earn transactions are preserved and expiry is recorded with separate negative transactions.
- Rewards support discount vouchers, free wash, and free add-on services.

## User Management

- Customer identity is normalized around phone number and active license plate.
- Admin users can search users, activate/deactivate accounts, change roles, and reset passwords.
- Sensitive admin user actions are recorded in audit logs.

## Booking Priority

Admin can view the queue at `GET /api/admin/bookings/priority-queue`.

Sorting order:

1. Membership tier `priorityLevel` descending.
2. `scheduledAt` ascending.

## RBL/Data Science

Initial CSV endpoint:

- `GET /api/admin/reports/export/bookings.csv`

Future exports can include:

- loyalty transactions
- customer tier progression
- promotion usage
- reward redemption behavior
- audit log activity
