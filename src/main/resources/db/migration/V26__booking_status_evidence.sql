IF COL_LENGTH('dbo.bookings', 'service_started_at') IS NULL
BEGIN
    ALTER TABLE dbo.bookings ADD service_started_at DATETIME2(6) NULL;
END;

IF COL_LENGTH('dbo.bookings', 'check_in_image_url') IS NULL
BEGIN
    ALTER TABLE dbo.bookings ADD check_in_image_url VARCHAR(500) NULL;
END;

IF COL_LENGTH('dbo.bookings', 'completion_image_url') IS NULL
BEGIN
    ALTER TABLE dbo.bookings ADD completion_image_url VARCHAR(500) NULL;
END;
