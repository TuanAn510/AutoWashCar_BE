# Database Design

Database: MySQL.

Migration chinh: `src/main/resources/db/migration/V1__init_schema.sql`.

## Bang chinh

- `users`: customer/admin account, phone unique, password hash, role.
- `vehicles`: xe cua customer, license plate unique.
- `service_categories`: nhom dich vu.
- `services`: dich vu rua xe, gia va thoi luong.
- `membership_tiers`: tier rule gom min points, discount percent, booking window days, priority level.
- `loyalty_accounts`: diem hien tai, diem tich luy, tong chi tieu, so lan ghe, tier hien tai.
- `loyalty_transactions`: lich su earn/redeem/expire/adjust.
- `promotions`: ma khuyen mai, target tier, thoi gian, usage limit.
- `rewards`: phan thuong doi diem.
- `reward_redemptions`: reward customer da doi va ma redemption.
- `bookings`: lich dat rua xe, tong tien, discount, trang thai.
- `booking_services`: snapshot dich vu trong booking.
- `survey_event_logs`: log hanh vi click/page view/form submit trong giai doan survey.

## Quan he noi bat

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

## Business rules trong DB/model

- Phone va license plate la unique.
- Moi booking slot `scheduled_at` la unique trong prototype tuan 1-4.
- Booking luu snapshot dich vu qua `booking_services`.
- Tier khong hard-code trong code; admin co the cau hinh bang API.
- Seed mac dinh tao Member/Silver/Gold/Platinum voi booking window 7/10/12/14 ngay.
- DB co check constraints cho role, status, diem, gia tien, thoi gian promotion va enum nghiep vu.
