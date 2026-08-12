/*
    Wash Car Service - shared database query kit
    Database: SQL Server / wash_car_service

    Usage:
    - Run in SSMS, Azure Data Studio, or sqlcmd against the backend database.
    - This script is read-only by default. Do not add UPDATE/DELETE here unless the team agrees.
    - Change @TopRows / @FromDate / @ToDate in each section when you need a wider range.
*/

/* ============================================================
   1. Database health and schema
   ============================================================ */

SELECT
    DB_NAME() AS database_name,
    @@SERVERNAME AS server_name,
    SYSDATETIME() AS checked_at;

SELECT
    s.name AS schema_name,
    t.name AS table_name,
    SUM(p.rows) AS row_count
FROM sys.tables t
JOIN sys.schemas s ON s.schema_id = t.schema_id
JOIN sys.partitions p ON p.object_id = t.object_id AND p.index_id IN (0, 1)
GROUP BY s.name, t.name
ORDER BY s.name, t.name;

SELECT
    c.TABLE_SCHEMA AS table_schema,
    c.TABLE_NAME AS table_name,
    c.ORDINAL_POSITION AS ordinal_position,
    c.COLUMN_NAME AS column_name,
    c.DATA_TYPE AS data_type,
    c.CHARACTER_MAXIMUM_LENGTH AS max_length,
    c.NUMERIC_PRECISION AS numeric_precision,
    c.NUMERIC_SCALE AS numeric_scale,
    c.IS_NULLABLE AS is_nullable,
    c.COLUMN_DEFAULT AS column_default
FROM INFORMATION_SCHEMA.COLUMNS c
ORDER BY c.TABLE_SCHEMA, c.TABLE_NAME, c.ORDINAL_POSITION;

SELECT
    kc.name AS constraint_name,
    OBJECT_SCHEMA_NAME(kc.parent_object_id) AS schema_name,
    OBJECT_NAME(kc.parent_object_id) AS table_name,
    kc.type_desc AS constraint_type
FROM sys.key_constraints kc
ORDER BY schema_name, table_name, constraint_name;

SELECT
    fk.name AS foreign_key_name,
    OBJECT_SCHEMA_NAME(fk.parent_object_id) AS child_schema,
    OBJECT_NAME(fk.parent_object_id) AS child_table,
    COL_NAME(fkc.parent_object_id, fkc.parent_column_id) AS child_column,
    OBJECT_SCHEMA_NAME(fk.referenced_object_id) AS parent_schema,
    OBJECT_NAME(fk.referenced_object_id) AS parent_table,
    COL_NAME(fkc.referenced_object_id, fkc.referenced_column_id) AS parent_column
FROM sys.foreign_keys fk
JOIN sys.foreign_key_columns fkc ON fkc.constraint_object_id = fk.object_id
ORDER BY child_schema, child_table, foreign_key_name;

SELECT
    OBJECT_SCHEMA_NAME(i.object_id) AS schema_name,
    OBJECT_NAME(i.object_id) AS table_name,
    i.name AS index_name,
    i.is_unique,
    i.has_filter,
    i.filter_definition
FROM sys.indexes i
WHERE i.name IS NOT NULL
ORDER BY schema_name, table_name, index_name;

IF OBJECT_ID('dbo.flyway_schema_history', 'U') IS NOT NULL
BEGIN
    SELECT
        installed_rank,
        version,
        description,
        type,
        script,
        installed_on,
        success
    FROM dbo.flyway_schema_history
    ORDER BY installed_rank;
END;

SELECT
    required_column.table_name,
    required_column.column_name,
    CASE
        WHEN COL_LENGTH(required_column.table_name, required_column.column_name) IS NULL THEN 'MISSING'
        ELSE 'OK'
    END AS status
FROM (VALUES
    ('dbo.bookings', 'check_in_at'),
    ('dbo.bookings', 'assigned_staff_id'),
    ('dbo.bookings', 'payment_method'),
    ('dbo.bookings', 'payment_status'),
    ('dbo.bookings', 'paid_at')
) AS required_column(table_name, column_name)
ORDER BY required_column.table_name, required_column.column_name;
GO

/* ============================================================
   2. Users, staff, and vehicles
   ============================================================ */

DECLARE @TopRowsUsers INT = 100;

SELECT
    role,
    is_active,
    COUNT(*) AS total_users
FROM dbo.users
GROUP BY role, is_active
ORDER BY role, is_active DESC;

SELECT TOP (@TopRowsUsers)
    id,
    full_name,
    phone,
    role,
    is_active,
    created_at,
    updated_at
FROM dbo.users
ORDER BY created_at DESC;

SELECT
    id,
    full_name,
    phone,
    is_active
FROM dbo.users
WHERE role = 'ROLE_STAFF'
ORDER BY is_active DESC, full_name;

SELECT TOP (@TopRowsUsers)
    u.id AS customer_id,
    u.full_name AS customer_name,
    u.phone,
    v.id AS vehicle_id,
    v.license_plate,
    v.brand,
    v.model,
    v.color,
    v.manufacture_year,
    v.is_active AS vehicle_active,
    v.ownership_start_at,
    v.ownership_end_at
FROM dbo.users u
JOIN dbo.vehicles v ON v.customer_id = u.id
ORDER BY u.full_name, v.is_active DESC, v.license_plate;
GO

/* ============================================================
   3. Service catalog
   ============================================================ */

SELECT
    c.id AS category_id,
    c.name AS category_name,
    c.is_active AS category_active,
    s.id AS service_id,
    s.name AS service_name,
    s.price,
    s.duration_minutes,
    s.is_active AS service_active
FROM dbo.service_categories c
LEFT JOIN dbo.services s ON s.category_id = c.id
ORDER BY c.name, s.name;

SELECT
    c.name AS category_name,
    COUNT(s.id) AS service_count,
    MIN(s.price) AS min_price,
    MAX(s.price) AS max_price,
    SUM(CASE WHEN s.is_active = 1 THEN 1 ELSE 0 END) AS active_services
FROM dbo.service_categories c
LEFT JOIN dbo.services s ON s.category_id = c.id
GROUP BY c.name
ORDER BY c.name;
GO

/* ============================================================
   4. Appointments / bookings
   ============================================================ */

DECLARE @TopRowsAppointments INT = 200;
DECLARE @FromDateAppointments DATETIME2(6) = DATEADD(DAY, -30, CAST(GETDATE() AS DATE));
DECLARE @ToDateAppointments DATETIME2(6) = DATEADD(DAY, 30, CAST(GETDATE() AS DATE));

SELECT TOP (@TopRowsAppointments)
    b.id AS booking_id,
    b.scheduled_at,
    b.status,
    customer.full_name AS customer_name,
    customer.phone AS customer_phone,
    v.license_plate,
    v.brand,
    v.model,
    b.payment_method,
    b.payment_status,
    b.paid_at,
    b.subtotal_amount,
    b.discount_amount,
    b.final_amount,
    b.note,
    b.created_at,
    b.updated_at
FROM dbo.bookings b
JOIN dbo.users customer ON customer.id = b.customer_id
JOIN dbo.vehicles v ON v.id = b.vehicle_id
WHERE b.scheduled_at >= @FromDateAppointments
  AND b.scheduled_at < @ToDateAppointments
ORDER BY b.scheduled_at DESC, b.id DESC;

SELECT
    b.id AS booking_id,
    b.scheduled_at,
    b.status,
    customer.full_name AS customer_name,
    customer.phone AS customer_phone,
    v.license_plate,
    b.payment_status,
    b.final_amount
FROM dbo.bookings b
JOIN dbo.users customer ON customer.id = b.customer_id
JOIN dbo.vehicles v ON v.id = b.vehicle_id
WHERE b.scheduled_at >= CAST(GETDATE() AS DATE)
  AND b.scheduled_at < DATEADD(DAY, 1, CAST(GETDATE() AS DATE))
ORDER BY b.scheduled_at, b.id;

SELECT
    b.scheduled_at,
    COUNT(*) AS active_booking_count
FROM dbo.bookings b
WHERE b.status IN ('PENDING', 'CONFIRMED', 'IN_QUEUE', 'IN_PROGRESS')
GROUP BY b.scheduled_at
ORDER BY b.scheduled_at;

IF COL_LENGTH('dbo.bookings', 'assigned_staff_id') IS NULL
BEGIN
    SELECT 'SKIPPED: dbo.bookings.assigned_staff_id is missing. Run the backend migration that adds appointment assignment first.' AS message;
END
ELSE
BEGIN
    EXEC sp_executesql N'
        SELECT
            b.id AS booking_id,
            b.scheduled_at,
            b.status,
            customer.full_name AS customer_name,
            customer.phone AS customer_phone,
            v.license_plate,
            staff.full_name AS assigned_staff_name,
            b.final_amount
        FROM dbo.bookings b
        JOIN dbo.users customer ON customer.id = b.customer_id
        JOIN dbo.vehicles v ON v.id = b.vehicle_id
        LEFT JOIN dbo.users staff ON staff.id = b.assigned_staff_id
        WHERE b.assigned_staff_id IS NULL
          AND b.status IN (''PENDING'', ''CONFIRMED'', ''IN_QUEUE'', ''IN_PROGRESS'')
        ORDER BY b.scheduled_at, b.id;

        SELECT
            staff.id AS staff_id,
            staff.full_name AS staff_name,
            COUNT(b.id) AS active_appointment_count
        FROM dbo.users staff
        LEFT JOIN dbo.bookings b
            ON b.assigned_staff_id = staff.id
           AND b.status IN (''PENDING'', ''CONFIRMED'', ''IN_QUEUE'', ''IN_PROGRESS'')
        WHERE staff.role = ''ROLE_STAFF''
        GROUP BY staff.id, staff.full_name
        ORDER BY active_appointment_count DESC, staff.full_name;
    ';
END;
GO

/* ============================================================
   5. Queue and staff workload
   ============================================================ */

DECLARE @QueueDate DATE = CAST(GETDATE() AS DATE);

SELECT
    b.id AS booking_id,
    b.scheduled_at,
    b.status,
    customer.full_name AS customer_name,
    customer.phone AS customer_phone,
    v.license_plate,
    tier.name AS membership_tier,
    tier.priority_level,
    b.payment_status,
    b.final_amount
FROM dbo.bookings b
JOIN dbo.users customer ON customer.id = b.customer_id
JOIN dbo.vehicles v ON v.id = b.vehicle_id
LEFT JOIN dbo.loyalty_accounts la ON la.customer_id = customer.id
LEFT JOIN dbo.membership_tiers tier ON tier.id = la.membership_tier_id
WHERE b.status IN ('IN_QUEUE', 'IN_PROGRESS')
  AND b.scheduled_at >= @QueueDate
  AND b.scheduled_at < DATEADD(DAY, 1, @QueueDate)
ORDER BY
    CASE WHEN b.status = 'IN_PROGRESS' THEN 0 ELSE 1 END,
    COALESCE(tier.priority_level, 999),
    b.scheduled_at;

IF COL_LENGTH('dbo.bookings', 'assigned_staff_id') IS NULL
BEGIN
    SELECT 'SKIPPED: dbo.bookings.assigned_staff_id is missing. Staff workload requires the appointment assignment migration.' AS message;
END
ELSE
BEGIN
    EXEC sp_executesql N'
        DECLARE @QueueDateInner DATE = CAST(GETDATE() AS DATE);

        SELECT
            staff.id AS staff_id,
            staff.full_name AS staff_name,
            b.status,
            COUNT(b.id) AS booking_count
        FROM dbo.users staff
        LEFT JOIN dbo.bookings b
            ON b.assigned_staff_id = staff.id
           AND b.scheduled_at >= @QueueDateInner
           AND b.scheduled_at < DATEADD(DAY, 1, @QueueDateInner)
        WHERE staff.role = ''ROLE_STAFF''
        GROUP BY staff.id, staff.full_name, b.status
        ORDER BY staff.full_name, b.status;
    ';
END;
GO

/* ============================================================
   6. Payment and revenue
   ============================================================ */

DECLARE @FromDatePayment DATETIME2(6) = DATEADD(DAY, -30, CAST(GETDATE() AS DATE));
DECLARE @ToDatePayment DATETIME2(6) = DATEADD(DAY, 1, CAST(GETDATE() AS DATE));

SELECT
    payment_status,
    payment_method,
    COUNT(*) AS booking_count,
    SUM(final_amount) AS total_amount
FROM dbo.bookings
WHERE scheduled_at >= @FromDatePayment
  AND scheduled_at < @ToDatePayment
GROUP BY payment_status, payment_method
ORDER BY payment_status, payment_method;

SELECT
    CAST(COALESCE(paid_at, completed_at, scheduled_at) AS DATE) AS revenue_date,
    payment_method,
    COUNT(*) AS paid_booking_count,
    SUM(final_amount) AS revenue
FROM dbo.bookings
WHERE payment_status = 'PAID'
  AND COALESCE(paid_at, completed_at, scheduled_at) >= @FromDatePayment
  AND COALESCE(paid_at, completed_at, scheduled_at) < @ToDatePayment
GROUP BY CAST(COALESCE(paid_at, completed_at, scheduled_at) AS DATE), payment_method
ORDER BY revenue_date DESC, payment_method;

SELECT
    b.id AS booking_id,
    b.scheduled_at,
    customer.full_name AS customer_name,
    customer.phone AS customer_phone,
    b.payment_method,
    b.payment_status,
    b.final_amount
FROM dbo.bookings b
JOIN dbo.users customer ON customer.id = b.customer_id
WHERE b.status IN ('CONFIRMED', 'IN_QUEUE', 'IN_PROGRESS', 'COMPLETED')
  AND b.payment_status IN ('UNPAID', 'PENDING')
ORDER BY b.scheduled_at, b.id;
GO

/* ============================================================
   7. Promotions, rewards, and loyalty
   ============================================================ */

DECLARE @TopRowsLoyalty INT = 100;

SELECT
    la.customer_id,
    customer.full_name AS customer_name,
    customer.phone,
    la.current_points,
    la.lifetime_points,
    la.total_spending,
    la.visit_count,
    tier.name AS membership_tier,
    tier.priority_level,
    la.last_reviewed_at
FROM dbo.loyalty_accounts la
JOIN dbo.users customer ON customer.id = la.customer_id
LEFT JOIN dbo.membership_tiers tier ON tier.id = la.membership_tier_id
ORDER BY la.current_points DESC, la.total_spending DESC;

SELECT TOP (@TopRowsLoyalty)
    lt.id AS transaction_id,
    lt.created_at,
    customer.full_name AS customer_name,
    customer.phone,
    lt.type,
    lt.points,
    lt.booking_id,
    lt.redemption_id,
    lt.expires_at,
    lt.description
FROM dbo.loyalty_transactions lt
JOIN dbo.users customer ON customer.id = lt.customer_id
ORDER BY lt.created_at DESC, lt.id DESC;

SELECT
    pl.customer_id,
    customer.full_name AS customer_name,
    customer.phone,
    pl.id AS point_lot_id,
    pl.remaining_points,
    pl.expires_at
FROM dbo.point_lots pl
JOIN dbo.users customer ON customer.id = pl.customer_id
WHERE pl.remaining_points > 0
  AND pl.expires_at < DATEADD(DAY, 30, SYSUTCDATETIME())
ORDER BY pl.expires_at, customer.full_name;

SELECT
    code,
    title,
    discount_type,
    discount_value,
    start_at,
    end_at,
    usage_limit,
    used_count,
    is_active
FROM dbo.promotions
WHERE is_active = 1
  AND start_at <= SYSUTCDATETIME()
  AND end_at > SYSUTCDATETIME()
ORDER BY end_at, code;

SELECT
    rr.id AS redemption_id,
    rr.code,
    rr.status,
    rr.redeemed_at,
    rr.used_at,
    rr.expires_at,
    customer.full_name AS customer_name,
    customer.phone,
    r.name AS reward_name,
    rr.points_used
FROM dbo.reward_redemptions rr
JOIN dbo.users customer ON customer.id = rr.customer_id
JOIN dbo.rewards r ON r.id = rr.reward_id
ORDER BY rr.redeemed_at DESC;
GO

/* ============================================================
   8. Vehicle access requests
   ============================================================ */

SELECT
    var.id AS request_id,
    var.created_at,
    var.status,
    requester.full_name AS requester_name,
    requester.phone AS requester_phone,
    var.license_plate,
    var.relationship,
    var.note,
    var.review_note,
    var.reviewed_at,
    v.customer_id AS current_vehicle_owner_id,
    owner.full_name AS current_vehicle_owner_name
FROM dbo.vehicle_access_requests var
JOIN dbo.users requester ON requester.id = var.requester_id
LEFT JOIN dbo.vehicles v ON v.id = var.vehicle_id
LEFT JOIN dbo.users owner ON owner.id = v.customer_id
ORDER BY
    CASE var.status WHEN 'PENDING' THEN 0 WHEN 'APPROVED' THEN 1 ELSE 2 END,
    var.created_at DESC;
GO

/* ============================================================
   9. Reports and dashboard queries
   ============================================================ */

DECLARE @FromDateReport DATETIME2(6) = DATEADD(DAY, -30, CAST(GETDATE() AS DATE));
DECLARE @ToDateReport DATETIME2(6) = DATEADD(DAY, 1, CAST(GETDATE() AS DATE));

SELECT
    COUNT(*) AS total_bookings,
    SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END) AS completed_bookings,
    SUM(CASE WHEN status = 'CANCELLED' THEN 1 ELSE 0 END) AS cancelled_bookings,
    SUM(CASE WHEN payment_status = 'PAID' THEN 1 ELSE 0 END) AS paid_bookings,
    SUM(CASE WHEN payment_status = 'PAID' THEN final_amount ELSE 0 END) AS paid_revenue
FROM dbo.bookings
WHERE scheduled_at >= @FromDateReport
  AND scheduled_at < @ToDateReport;

SELECT
    CAST(b.scheduled_at AS DATE) AS booking_date,
    COUNT(*) AS booking_count,
    SUM(CASE WHEN b.status = 'COMPLETED' THEN 1 ELSE 0 END) AS completed_count,
    SUM(CASE WHEN b.status = 'CANCELLED' THEN 1 ELSE 0 END) AS cancelled_count,
    SUM(CASE WHEN b.payment_status = 'PAID' THEN b.final_amount ELSE 0 END) AS paid_revenue
FROM dbo.bookings b
WHERE b.scheduled_at >= @FromDateReport
  AND b.scheduled_at < @ToDateReport
GROUP BY CAST(b.scheduled_at AS DATE)
ORDER BY booking_date DESC;

SELECT
    bs.service_id,
    bs.service_name,
    COUNT(*) AS usage_count,
    SUM(bs.price) AS gross_service_amount,
    AVG(CAST(bs.duration_minutes AS DECIMAL(10, 2))) AS avg_duration_minutes
FROM dbo.booking_services bs
JOIN dbo.bookings b ON b.id = bs.booking_id
WHERE b.scheduled_at >= @FromDateReport
  AND b.scheduled_at < @ToDateReport
  AND b.status <> 'CANCELLED'
GROUP BY bs.service_id, bs.service_name
ORDER BY usage_count DESC, gross_service_amount DESC;

SELECT TOP (50)
    customer.id AS customer_id,
    customer.full_name,
    customer.phone,
    COUNT(b.id) AS booking_count,
    SUM(CASE WHEN b.payment_status = 'PAID' THEN b.final_amount ELSE 0 END) AS total_paid_amount,
    MAX(b.scheduled_at) AS last_booking_at
FROM dbo.users customer
LEFT JOIN dbo.bookings b ON b.customer_id = customer.id
WHERE customer.role = 'ROLE_CUSTOMER'
GROUP BY customer.id, customer.full_name, customer.phone
ORDER BY total_paid_amount DESC, booking_count DESC;
GO

/* ============================================================
   10. Data quality checks
   Expected result for most checks below: 0 rows.
   ============================================================ */

SELECT
    b.scheduled_at,
    COUNT(*) AS active_booking_count,
    STRING_AGG(CAST(b.id AS VARCHAR(20)), ', ') AS booking_ids
FROM dbo.bookings b
WHERE b.status IN ('PENDING', 'CONFIRMED', 'IN_QUEUE', 'IN_PROGRESS')
GROUP BY b.scheduled_at
HAVING COUNT(*) > 1
ORDER BY b.scheduled_at;

SELECT
    b.id AS booking_id,
    b.customer_id AS booking_customer_id,
    v.customer_id AS vehicle_customer_id,
    v.license_plate,
    b.scheduled_at,
    b.status
FROM dbo.bookings b
JOIN dbo.vehicles v ON v.id = b.vehicle_id
WHERE b.customer_id <> v.customer_id
ORDER BY b.scheduled_at DESC;

SELECT
    license_plate,
    COUNT(*) AS active_vehicle_count,
    STRING_AGG(CAST(id AS VARCHAR(20)), ', ') AS vehicle_ids
FROM dbo.vehicles
WHERE is_active = 1
GROUP BY license_plate
HAVING COUNT(*) > 1
ORDER BY license_plate;

IF COL_LENGTH('dbo.bookings', 'assigned_staff_id') IS NULL
BEGIN
    SELECT 'SKIPPED: dbo.bookings.assigned_staff_id is missing. Cannot validate assigned staff yet.' AS message;
END
ELSE
BEGIN
    EXEC sp_executesql N'
        SELECT
            b.id AS booking_id,
            b.assigned_staff_id,
            staff.full_name,
            staff.phone,
            staff.role,
            staff.is_active,
            b.scheduled_at,
            b.status
        FROM dbo.bookings b
        JOIN dbo.users staff ON staff.id = b.assigned_staff_id
        WHERE b.assigned_staff_id IS NOT NULL
          AND (staff.role <> ''ROLE_STAFF'' OR staff.is_active = 0)
        ORDER BY b.scheduled_at DESC;
    ';
END;

SELECT
    id AS booking_id,
    scheduled_at,
    status,
    payment_method,
    payment_status,
    paid_at,
    final_amount
FROM dbo.bookings
WHERE (payment_status = 'PAID' AND paid_at IS NULL)
   OR (payment_status <> 'PAID' AND paid_at IS NOT NULL)
ORDER BY scheduled_at DESC;

SELECT
    id AS booking_id,
    subtotal_amount,
    discount_amount,
    final_amount,
    subtotal_amount - discount_amount AS expected_final_amount
FROM dbo.bookings
WHERE ABS(final_amount - (subtotal_amount - discount_amount)) > 0.01
ORDER BY id DESC;

SELECT
    b.id AS booking_id,
    b.subtotal_amount,
    SUM(bs.price) AS booking_services_total
FROM dbo.bookings b
LEFT JOIN dbo.booking_services bs ON bs.booking_id = b.id
GROUP BY b.id, b.subtotal_amount
HAVING ABS(b.subtotal_amount - COALESCE(SUM(bs.price), 0)) > 0.01
ORDER BY b.id DESC;

SELECT
    id,
    full_name,
    phone,
    role
FROM dbo.users
WHERE role NOT IN ('ROLE_CUSTOMER', 'ROLE_STAFF', 'ROLE_ADMIN');

SELECT
    id,
    scheduled_at,
    status
FROM dbo.bookings
WHERE status NOT IN ('PENDING', 'CONFIRMED', 'IN_QUEUE', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED');

SELECT
    id,
    scheduled_at,
    payment_method,
    payment_status
FROM dbo.bookings
WHERE payment_method NOT IN ('CASH', 'VNPAY', 'MOMO')
   OR payment_status NOT IN ('UNPAID', 'PENDING', 'PAID', 'CANCELLED');
GO

/* ============================================================
   11. Audit and auth support
   ============================================================ */

SELECT TOP (100)
    al.id,
    al.created_at,
    actor.full_name AS actor_name,
    target.full_name AS target_user_name,
    al.action,
    al.before_value,
    al.after_value
FROM dbo.audit_logs al
LEFT JOIN dbo.users actor ON actor.id = al.actor_id
LEFT JOIN dbo.users target ON target.id = al.target_user_id
ORDER BY al.created_at DESC, al.id DESC;

SELECT
    user_id,
    COUNT(*) AS token_count,
    SUM(CASE WHEN revoked_at IS NULL AND expires_at > SYSUTCDATETIME() THEN 1 ELSE 0 END) AS active_token_count,
    MAX(created_at) AS newest_token_created_at,
    MAX(expires_at) AS newest_token_expires_at
FROM dbo.refresh_tokens
GROUP BY user_id
ORDER BY active_token_count DESC, newest_token_created_at DESC;

SELECT TOP (100)
    session_key,
    user_id,
    booking_id,
    event_type,
    page,
    action,
    created_at
FROM dbo.survey_event_logs
ORDER BY created_at DESC, id DESC;
GO
