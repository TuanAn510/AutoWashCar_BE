-- V40: Add catalog brand/model fields to vehicle_access_requests
-- These fields carry the brand/model the customer selected from the catalog
-- when submitting a plate verification request (Flow 2: catalog + duplicate plate).
-- Unlike suggested_brand_name / suggested_model_name (used for "Other" brand/model),
-- these fields do NOT affect the admin's "combined" classification.

IF COL_LENGTH('dbo.vehicle_access_requests', 'catalog_brand_name') IS NULL
BEGIN
    ALTER TABLE dbo.vehicle_access_requests ADD catalog_brand_name NVARCHAR(80) NULL;
END
GO

IF COL_LENGTH('dbo.vehicle_access_requests', 'catalog_model_name') IS NULL
BEGIN
    ALTER TABLE dbo.vehicle_access_requests ADD catalog_model_name NVARCHAR(80) NULL;
END
GO