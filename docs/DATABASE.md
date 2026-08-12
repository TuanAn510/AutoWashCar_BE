# Database Design

Database: MySQL.

Main migration: `src/main/resources/db/migration/V1__init_schema.sql`.

## Main Tables

- `users`: customer/admin accounts, unique phone, password hash, role.
- `vehicles`: customer vehicles with unique license plates.
- `service_categories`: service groups.
- `services`: wash car services with price and duration.
- `membership_tiers`: tier rules including minimum points, discount percentage, booking window days, and priority level.
- `loyalty_accounts`: current points, lifetime points, total spending, visit count, and current tier.
- `loyalty_transactions`: earn/redeem/expire/adjust transaction history.
- `promotions`: discount codes, target tier, active period, and usage limit.
- `rewards`: point redemption rewards.
- `reward_redemptions`: customer reward redemptions and redemption codes.
- `bookings`: wash car bookings, total amount, discount, and status.
- `booking_services`: service snapshots inside each booking.
- `survey_event_logs`: page view, click, form submit, and booking-created logs for the survey period.

## Key Relationships

- `users 1-n vehicles`
- `users 1-1 loyalty_accounts`
- `membership_tiers 1-n loyalty_accounts`
- `membership_tiers 1-n promotions`
- `users 1-n bookings`
- `vehicles 1-n bookings`
- `bookings 1-n booking_services`
- `bookings 1-n loyalty_transactions`
- `bookings 1-n survey_event_logs`
- `rewards 1-n reward_redemptions`

## Business Rules In DB/Model

- User phone and vehicle license plate are unique.
- Each booking slot `scheduled_at` is unique in the week 1-4 prototype.
- Booking services are stored as snapshots in `booking_services`.
- Tiers are not hard-coded in service logic; admin can configure them through API.
- Default seed data creates Member/Silver/Gold/Platinum with booking windows of 7/10/12/14 days.
- The database includes check constraints for role, status, points, prices, service duration, promotion time ranges, and business enums.
