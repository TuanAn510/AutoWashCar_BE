IF EXISTS (
    SELECT 1
    FROM sys.key_constraints
    WHERE name = 'uk_bookings_scheduled_at'
      AND parent_object_id = OBJECT_ID('dbo.bookings')
)
BEGIN
    ALTER TABLE dbo.bookings DROP CONSTRAINT uk_bookings_scheduled_at;
END
GO

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = 'ux_bookings_active_scheduled_at'
      AND object_id = OBJECT_ID('dbo.bookings')
)
BEGIN
    CREATE UNIQUE INDEX ux_bookings_active_scheduled_at
        ON dbo.bookings(scheduled_at)
        WHERE status IN ('PENDING', 'CONFIRMED', 'IN_QUEUE', 'IN_PROGRESS');
END
GO
