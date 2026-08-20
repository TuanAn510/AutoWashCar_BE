IF COL_LENGTH('dbo.services', 'reward_points') IS NULL
BEGIN
    ALTER TABLE dbo.services ADD reward_points INT NULL;
END;
GO

UPDATE dbo.services
SET reward_points =
    CASE
        WHEN price = 850000 THEN FLOOR(price / 10000) * 2
        ELSE FLOOR(price / 10000)
    END
WHERE reward_points IS NULL;
GO

IF EXISTS (
    SELECT 1
    FROM sys.columns
    WHERE object_id = OBJECT_ID('dbo.services')
      AND name = 'reward_points'
      AND is_nullable = 1
)
BEGIN
    ALTER TABLE dbo.services ALTER COLUMN reward_points INT NOT NULL;
END;
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.default_constraints
    WHERE parent_object_id = OBJECT_ID('dbo.services')
      AND name = 'df_services_reward_points'
)
BEGIN
    ALTER TABLE dbo.services
        ADD CONSTRAINT df_services_reward_points DEFAULT 0 FOR reward_points;
END;
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.check_constraints
    WHERE parent_object_id = OBJECT_ID('dbo.services')
      AND name = 'chk_services_reward_points'
)
BEGIN
    ALTER TABLE dbo.services
        ADD CONSTRAINT chk_services_reward_points CHECK (reward_points >= 0);
END;
GO

IF COL_LENGTH('dbo.services', 'version') IS NULL
BEGIN
    ALTER TABLE dbo.services
        ADD version BIGINT NOT NULL CONSTRAINT df_services_version DEFAULT 0;
END;
GO

IF COL_LENGTH('dbo.booking_services', 'reward_points') IS NULL
BEGIN
    ALTER TABLE dbo.booking_services ADD reward_points INT NULL;
END;
GO

UPDATE booking_service
SET booking_service.reward_points = service.reward_points
FROM dbo.booking_services booking_service
JOIN dbo.services service ON service.id = booking_service.service_id
WHERE booking_service.reward_points IS NULL;

UPDATE dbo.booking_services SET reward_points = 0 WHERE reward_points IS NULL;
GO

IF EXISTS (
    SELECT 1
    FROM sys.columns
    WHERE object_id = OBJECT_ID('dbo.booking_services')
      AND name = 'reward_points'
      AND is_nullable = 1
)
BEGIN
    ALTER TABLE dbo.booking_services ALTER COLUMN reward_points INT NOT NULL;
END;
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.default_constraints
    WHERE parent_object_id = OBJECT_ID('dbo.booking_services')
      AND name = 'df_booking_services_reward_points'
)
BEGIN
    ALTER TABLE dbo.booking_services
        ADD CONSTRAINT df_booking_services_reward_points DEFAULT 0 FOR reward_points;
END;
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.check_constraints
    WHERE parent_object_id = OBJECT_ID('dbo.booking_services')
      AND name = 'chk_booking_services_reward_points'
)
BEGIN
    ALTER TABLE dbo.booking_services
        ADD CONSTRAINT chk_booking_services_reward_points CHECK (reward_points >= 0);
END;
GO
