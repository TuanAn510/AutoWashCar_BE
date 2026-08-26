IF COL_LENGTH('dbo.vehicles', 'car_type') IS NULL
BEGIN
    ALTER TABLE dbo.vehicles
        ADD car_type VARCHAR(20) NOT NULL
            CONSTRAINT df_vehicles_car_type DEFAULT 'sedan';
END
GO

IF NOT EXISTS (
    SELECT 1
    FROM sys.check_constraints
    WHERE name = 'chk_vehicles_car_type'
      AND parent_object_id = OBJECT_ID('dbo.vehicles')
)
BEGIN
    ALTER TABLE dbo.vehicles
        ADD CONSTRAINT chk_vehicles_car_type CHECK (car_type IN ('sedan', 'suv', 'pickup'));
END
GO
