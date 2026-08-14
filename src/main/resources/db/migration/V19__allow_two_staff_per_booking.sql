IF COL_LENGTH('dbo.bookings', 'secondary_assigned_staff_id') IS NULL
BEGIN
    ALTER TABLE dbo.bookings
        ADD secondary_assigned_staff_id BIGINT NULL;
END;

IF NOT EXISTS (
    SELECT 1
    FROM sys.foreign_keys
    WHERE name = 'fk_bookings_secondary_assigned_staff'
      AND parent_object_id = OBJECT_ID('dbo.bookings')
)
BEGIN
    ALTER TABLE dbo.bookings
        ADD CONSTRAINT fk_bookings_secondary_assigned_staff
            FOREIGN KEY (secondary_assigned_staff_id) REFERENCES dbo.users(id);
END;

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = 'idx_bookings_secondary_staff_schedule'
      AND object_id = OBJECT_ID('dbo.bookings')
)
BEGIN
    CREATE INDEX idx_bookings_secondary_staff_schedule
        ON dbo.bookings(secondary_assigned_staff_id, scheduled_at);
END;
