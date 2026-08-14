-- Carrier fields needed to create the vehicle after an admin approves a
-- brand/model verification request (the vehicle is deferred until approval).
IF OBJECT_ID('dbo.vehicle_access_requests', 'U') IS NOT NULL
BEGIN
    IF NOT EXISTS (SELECT 1 FROM sys.columns
                   WHERE object_id = OBJECT_ID('dbo.vehicle_access_requests')
                     AND name = 'car_type')
    BEGIN
        ALTER TABLE dbo.vehicle_access_requests ADD car_type NVARCHAR(20) NULL;
    END

    IF NOT EXISTS (SELECT 1 FROM sys.columns
                   WHERE object_id = OBJECT_ID('dbo.vehicle_access_requests')
                     AND name = 'manufacture_year')
    BEGIN
        ALTER TABLE dbo.vehicle_access_requests ADD manufacture_year INT NULL;
    END
END
GO