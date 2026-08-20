WITH booking_point_totals AS (
    SELECT
        booking.id AS booking_id,
        SUM(CONVERT(INT, FLOOR(booking_service.price / 10000))) AS formula_points
    FROM dbo.bookings booking
    JOIN dbo.booking_services booking_service ON booking_service.booking_id = booking.id
    WHERE booking.status NOT IN ('COMPLETED', 'CANCELLED')
    GROUP BY booking.id
)
UPDATE loyalty_transaction
SET loyalty_transaction.points = booking_point_total.formula_points
FROM dbo.loyalty_transactions loyalty_transaction
JOIN booking_point_totals booking_point_total
  ON booking_point_total.booking_id = loyalty_transaction.booking_id
WHERE loyalty_transaction.type = 'EARN'
  AND loyalty_transaction.status = 'PENDING'
  AND loyalty_transaction.points <> booking_point_total.formula_points;
GO

WITH booking_point_totals AS (
    SELECT
        booking.id AS booking_id,
        SUM(CONVERT(INT, FLOOR(booking_service.price / 10000))) AS formula_points
    FROM dbo.bookings booking
    JOIN dbo.booking_services booking_service ON booking_service.booking_id = booking.id
    WHERE booking.status NOT IN ('COMPLETED', 'CANCELLED')
    GROUP BY booking.id
)
UPDATE booking
SET booking.earned_points = booking_point_total.formula_points
FROM dbo.bookings booking
JOIN booking_point_totals booking_point_total ON booking_point_total.booking_id = booking.id
WHERE booking.earned_points > 0
  AND booking.earned_points <> booking_point_total.formula_points;
GO

UPDATE booking_service
SET booking_service.reward_points = CONVERT(INT, FLOOR(booking_service.price / 10000))
FROM dbo.booking_services booking_service
JOIN dbo.bookings booking ON booking.id = booking_service.booking_id
WHERE booking.status NOT IN ('COMPLETED', 'CANCELLED')
  AND booking_service.reward_points <> CONVERT(INT, FLOOR(booking_service.price / 10000));
GO
