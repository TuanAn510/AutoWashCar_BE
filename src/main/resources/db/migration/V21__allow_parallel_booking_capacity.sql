IF EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = 'ux_bookings_active_scheduled_at'
      AND object_id = OBJECT_ID('dbo.bookings')
)
BEGIN
    DROP INDEX ux_bookings_active_scheduled_at ON dbo.bookings;
END;

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = 'idx_bookings_vehicle_status_schedule'
      AND object_id = OBJECT_ID('dbo.bookings')
)
BEGIN
    CREATE INDEX idx_bookings_vehicle_status_schedule
        ON dbo.bookings(vehicle_id, status, scheduled_at);
END;

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = 'idx_bookings_status_schedule'
      AND object_id = OBJECT_ID('dbo.bookings')
)
BEGIN
    CREATE INDEX idx_bookings_status_schedule
        ON dbo.bookings(status, scheduled_at);
END;
