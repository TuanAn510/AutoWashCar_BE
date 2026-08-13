-- V14: Convert all remaining user-facing VARCHAR columns to NVARCHAR for Vietnamese Unicode support
SET ANSI_NULLS ON;
GO
SET QUOTED_IDENTIFIER ON;
GO

-- =============================================
-- users
-- =============================================
ALTER TABLE dbo.users ALTER COLUMN full_name NVARCHAR(120) NOT NULL;
GO

-- =============================================
-- vehicles
-- =============================================
ALTER TABLE dbo.vehicles ALTER COLUMN brand NVARCHAR(80) NOT NULL;
GO
ALTER TABLE dbo.vehicles ALTER COLUMN model NVARCHAR(80) NOT NULL;
GO
ALTER TABLE dbo.vehicles ALTER COLUMN color NVARCHAR(40) NULL;
GO
-- Drop dependent index before altering license_plate
DROP INDEX IF EXISTS ux_vehicles_active_license_plate ON dbo.vehicles;
GO
ALTER TABLE dbo.vehicles ALTER COLUMN license_plate NVARCHAR(20) NOT NULL;
GO
-- Recreate the index
CREATE UNIQUE NONCLUSTERED INDEX ux_vehicles_active_license_plate
    ON dbo.vehicles (license_plate)
    WHERE (is_active = 1);
GO

-- =============================================
-- membership_tiers
-- =============================================
-- Drop unique constraint on name before altering
ALTER TABLE dbo.membership_tiers DROP CONSTRAINT uk_membership_tiers_name;
GO
ALTER TABLE dbo.membership_tiers ALTER COLUMN name NVARCHAR(80) NOT NULL;
GO
ALTER TABLE dbo.membership_tiers ADD CONSTRAINT uk_membership_tiers_name UNIQUE (name);
GO
ALTER TABLE dbo.membership_tiers ALTER COLUMN description NVARCHAR(MAX) NULL;
GO

-- =============================================
-- promotions
-- =============================================
-- Drop unique constraint on code before altering
ALTER TABLE dbo.promotions DROP CONSTRAINT uk_promotions_code;
GO
ALTER TABLE dbo.promotions ALTER COLUMN code NVARCHAR(40) NOT NULL;
GO
ALTER TABLE dbo.promotions ADD CONSTRAINT uk_promotions_code UNIQUE (code);
GO
ALTER TABLE dbo.promotions ALTER COLUMN title NVARCHAR(160) NOT NULL;
GO
ALTER TABLE dbo.promotions ALTER COLUMN description NVARCHAR(MAX) NULL;
GO

-- =============================================
-- rewards
-- =============================================
ALTER TABLE dbo.rewards ALTER COLUMN name NVARCHAR(160) NOT NULL;
GO
ALTER TABLE dbo.rewards ALTER COLUMN description NVARCHAR(MAX) NULL;
GO

-- =============================================
-- reward_redemptions
-- =============================================
-- Drop unique constraint on code before altering
ALTER TABLE dbo.reward_redemptions DROP CONSTRAINT uk_reward_redemptions_code;
GO
ALTER TABLE dbo.reward_redemptions ALTER COLUMN code NVARCHAR(40) NOT NULL;
GO
ALTER TABLE dbo.reward_redemptions ADD CONSTRAINT uk_reward_redemptions_code UNIQUE (code);
GO

-- =============================================
-- booking_services
-- =============================================
ALTER TABLE dbo.booking_services ALTER COLUMN service_name NVARCHAR(120) NOT NULL;
GO

-- =============================================
-- loyalty_transactions
-- =============================================
ALTER TABLE dbo.loyalty_transactions ALTER COLUMN description NVARCHAR(MAX) NULL;
GO

-- =============================================
-- survey_event_logs
-- =============================================
ALTER TABLE dbo.survey_event_logs ALTER COLUMN page NVARCHAR(120) NULL;
GO
ALTER TABLE dbo.survey_event_logs ALTER COLUMN action NVARCHAR(120) NULL;
GO
ALTER TABLE dbo.survey_event_logs ALTER COLUMN metadata_json NVARCHAR(MAX) NULL;
GO
ALTER TABLE dbo.survey_event_logs ALTER COLUMN ip_address NVARCHAR(64) NULL;
GO
ALTER TABLE dbo.survey_event_logs ALTER COLUMN user_agent NVARCHAR(500) NULL;
GO

-- =============================================
-- vehicle_access_requests
-- =============================================
-- Drop dependent index before altering license_plate
DROP INDEX IF EXISTS idx_vehicle_access_requests_license_plate ON dbo.vehicle_access_requests;
GO
ALTER TABLE dbo.vehicle_access_requests ALTER COLUMN license_plate NVARCHAR(20) NOT NULL;
GO
CREATE INDEX idx_vehicle_access_requests_license_plate
    ON dbo.vehicle_access_requests(license_plate);
GO
ALTER TABLE dbo.vehicle_access_requests ALTER COLUMN relationship NVARCHAR(80) NOT NULL;
GO
ALTER TABLE dbo.vehicle_access_requests ALTER COLUMN note NVARCHAR(MAX) NULL;
GO
ALTER TABLE dbo.vehicle_access_requests ALTER COLUMN review_note NVARCHAR(MAX) NULL;
GO