-- V16: Vehicle brand/model catalog + verification status support

-- =============================================
-- vehicle_brands
-- =============================================
IF OBJECT_ID('dbo.vehicle_brands', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.vehicle_brands (
        id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        name NVARCHAR(80) NOT NULL,
        is_active BIT NOT NULL DEFAULT 1,
        created_at DATETIME2(6) NOT NULL,
        updated_at DATETIME2(6) NOT NULL,
        CONSTRAINT uk_vehicle_brands_name UNIQUE (name)
    );

    CREATE INDEX idx_vehicle_brands_active
        ON dbo.vehicle_brands(is_active, name);
END
GO

-- =============================================
-- vehicle_models
-- =============================================
IF OBJECT_ID('dbo.vehicle_models', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.vehicle_models (
        id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        brand_id BIGINT NOT NULL,
        name NVARCHAR(80) NOT NULL,
        is_active BIT NOT NULL DEFAULT 1,
        is_new BIT NOT NULL DEFAULT 0,
        created_at DATETIME2(6) NOT NULL,
        updated_at DATETIME2(6) NOT NULL,
        CONSTRAINT fk_vehicle_models_brand FOREIGN KEY (brand_id) REFERENCES dbo.vehicle_brands(id),
        CONSTRAINT uk_vehicle_models_brand_name UNIQUE (brand_id, name)
    );

    CREATE INDEX idx_vehicle_models_active
        ON dbo.vehicle_models(brand_id, is_active, name);
END
GO

-- =============================================
-- vehicles: catalog refs + verification fields
-- =============================================
IF COL_LENGTH('dbo.vehicles', 'brand_id') IS NULL
BEGIN
    ALTER TABLE dbo.vehicles ADD brand_id BIGINT NULL;
END
GO

IF COL_LENGTH('dbo.vehicles', 'model_id') IS NULL
BEGIN
    ALTER TABLE dbo.vehicles ADD model_id BIGINT NULL;
END
GO

IF COL_LENGTH('dbo.vehicles', 'verification_status') IS NULL
BEGIN
    ALTER TABLE dbo.vehicles
        ADD verification_status NVARCHAR(20) NOT NULL
            CONSTRAINT df_vehicles_verification_status DEFAULT 'APPROVED';
END
GO

IF COL_LENGTH('dbo.vehicles', 'verification_note') IS NULL
BEGIN
    ALTER TABLE dbo.vehicles ADD verification_note NVARCHAR(MAX) NULL;
END
GO

IF COL_LENGTH('dbo.vehicles', 'verified_at') IS NULL
BEGIN
    ALTER TABLE dbo.vehicles ADD verified_at DATETIME2(6) NULL;
END
GO

IF COL_LENGTH('dbo.vehicles', 'verified_by') IS NULL
BEGIN
    ALTER TABLE dbo.vehicles ADD verified_by BIGINT NULL;
END
GO

IF NOT EXISTS (
    SELECT 1
    FROM sys.foreign_keys
    WHERE name = 'fk_vehicles_brand'
      AND parent_object_id = OBJECT_ID('dbo.vehicles')
)
BEGIN
    ALTER TABLE dbo.vehicles
        ADD CONSTRAINT fk_vehicles_brand FOREIGN KEY (brand_id) REFERENCES dbo.vehicle_brands(id);
END
GO

IF NOT EXISTS (
    SELECT 1
    FROM sys.foreign_keys
    WHERE name = 'fk_vehicles_model'
      AND parent_object_id = OBJECT_ID('dbo.vehicles')
)
BEGIN
    ALTER TABLE dbo.vehicles
        ADD CONSTRAINT fk_vehicles_model FOREIGN KEY (model_id) REFERENCES dbo.vehicle_models(id);
END
GO

IF NOT EXISTS (
    SELECT 1
    FROM sys.check_constraints
    WHERE name = 'chk_vehicles_verification_status'
      AND parent_object_id = OBJECT_ID('dbo.vehicles')
)
BEGIN
    ALTER TABLE dbo.vehicles
        ADD CONSTRAINT chk_vehicles_verification_status
            CHECK (verification_status IN ('PENDING', 'APPROVED', 'REJECTED'));
END
GO

-- =============================================
-- vehicle_access_requests: verification request support
-- =============================================
IF COL_LENGTH('dbo.vehicle_access_requests', 'request_type') IS NULL
BEGIN
    ALTER TABLE dbo.vehicle_access_requests
        ADD request_type NVARCHAR(40) NOT NULL
            CONSTRAINT df_vehicle_access_requests_request_type DEFAULT 'ACCESS_REQUEST';
END
GO

IF COL_LENGTH('dbo.vehicle_access_requests', 'suggested_brand_name') IS NULL
BEGIN
    ALTER TABLE dbo.vehicle_access_requests ADD suggested_brand_name NVARCHAR(80) NULL;
END
GO

IF COL_LENGTH('dbo.vehicle_access_requests', 'suggested_model_name') IS NULL
BEGIN
    ALTER TABLE dbo.vehicle_access_requests ADD suggested_model_name NVARCHAR(80) NULL;
END
GO

IF COL_LENGTH('dbo.vehicle_access_requests', 'brand_id') IS NULL
BEGIN
    ALTER TABLE dbo.vehicle_access_requests ADD brand_id BIGINT NULL;
END
GO

IF COL_LENGTH('dbo.vehicle_access_requests', 'model_id') IS NULL
BEGIN
    ALTER TABLE dbo.vehicle_access_requests ADD model_id BIGINT NULL;
END
GO

IF NOT EXISTS (
    SELECT 1
    FROM sys.foreign_keys
    WHERE name = 'fk_vehicle_access_requests_brand'
      AND parent_object_id = OBJECT_ID('dbo.vehicle_access_requests')
)
BEGIN
    ALTER TABLE dbo.vehicle_access_requests
        ADD CONSTRAINT fk_vehicle_access_requests_brand FOREIGN KEY (brand_id) REFERENCES dbo.vehicle_brands(id);
END
GO

IF NOT EXISTS (
    SELECT 1
    FROM sys.foreign_keys
    WHERE name = 'fk_vehicle_access_requests_model'
      AND parent_object_id = OBJECT_ID('dbo.vehicle_access_requests')
)
BEGIN
    ALTER TABLE dbo.vehicle_access_requests
        ADD CONSTRAINT fk_vehicle_access_requests_model FOREIGN KEY (model_id) REFERENCES dbo.vehicle_models(id);
END
GO

IF NOT EXISTS (
    SELECT 1
    FROM sys.check_constraints
    WHERE name = 'chk_vehicle_access_requests_request_type'
      AND parent_object_id = OBJECT_ID('dbo.vehicle_access_requests')
)
BEGIN
    ALTER TABLE dbo.vehicle_access_requests
        ADD CONSTRAINT chk_vehicle_access_requests_request_type
            CHECK (request_type IN ('ACCESS_REQUEST', 'BRAND_MODEL_VERIFICATION'));
END
GO
