# Database Design

Database: SQL Server.

Main migration: `src/main/resources/db/migration/V1__init_schema.sql`.

Additional migrations:

- `V2__allow_rebooking_cancelled_slots.sql`: changes booking slot uniqueness so cancelled bookings release their slot.
- `V3__loyalty_user_management_hardening.sql`: adds point lots, monthly loyalty snapshots, audit logs, optimistic lock versions, and active-license-plate uniqueness.

## Main Tables

- `users`: customer/admin accounts, unique phone, password hash, role.
- `vehicles`: customer vehicles with active-license-plate uniqueness and ownership timestamps.
- `service_categories`: service groups.
- `services`: wash car services with price and duration.
- `membership_tiers`: tier rules including minimum points, discount percentage, booking window days, and priority level.
- `loyalty_accounts`: current points, lifetime points, total spending, visit count, and current tier.
- `point_lots`: remaining balance per earning event, used for FIFO redemption and 12-month expiry.
- `loyalty_transactions`: earn/redeem/expire/adjust transaction history.
- `loyalty_monthly_snapshots`: monthly tier review result, review points, review spending, and visit count.
- `promotions`: discount codes, target tier, active period, and usage limit.
- `rewards`: point redemption rewards.
- `reward_redemptions`: customer reward redemptions and redemption codes.
- `bookings`: wash car bookings, total amount, discount, and status.
- `booking_services`: service snapshots inside each booking.
- `survey_event_logs`: page view, click, form submit, and booking-created logs for the survey period.
- `audit_logs`: admin actions on user status, role, and password reset.

## Key Relationships

- `users 1-n vehicles`
- `users 1-1 loyalty_accounts`
- `users 1-n point_lots`
- `users 1-n loyalty_monthly_snapshots`
- `membership_tiers 1-n loyalty_accounts`
- `membership_tiers 1-n promotions`
- `users 1-n bookings`
- `vehicles 1-n bookings`
- `bookings 1-n booking_services`
- `bookings 1-n loyalty_transactions`
- `bookings 1-n survey_event_logs`
- `rewards 1-n reward_redemptions`
- `users 1-n audit_logs`

## Business Rules In DB/Model

- User phone is unique after normalization.
- Vehicle license plate is unique only for active vehicles, so inactive historical ownership does not block reuse.
- Each active booking slot `scheduled_at` is unique. Cancelled bookings do not block the same slot from being booked again.
- Booking services are stored as snapshots in `booking_services`.
- Tiers are not hard-coded in service logic; admin can configure them through API.
- Monthly loyalty review evaluates the recent review window instead of relying only on lifetime points, allowing downgrade as well as upgrade.
- Point expiry never rewrites original earn transactions; it reduces point lots and creates separate `EXPIRE` transactions.
- Default seed data creates Member/Silver/Gold/Platinum with booking windows of 7/10/12/14 days.
- The database includes check constraints for role, status, points, prices, service duration, promotion time ranges, and business enums.
