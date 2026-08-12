SET ANSI_NULLS ON;
GO

SET QUOTED_IDENTIFIER ON;
GO

IF EXISTS (
    SELECT 1
    FROM sys.key_constraints
    WHERE parent_object_id = OBJECT_ID(N'dbo.service_categories')
      AND name = N'uk_service_categories_name'
)
BEGIN
    ALTER TABLE dbo.service_categories DROP CONSTRAINT uk_service_categories_name;
END;
GO

ALTER TABLE dbo.service_categories
    ALTER COLUMN name NVARCHAR(120) NOT NULL;
GO

ALTER TABLE dbo.service_categories
    ALTER COLUMN description NVARCHAR(1000) NULL;
GO

ALTER TABLE dbo.service_categories
    ADD CONSTRAINT uk_service_categories_name UNIQUE (name);
GO

IF EXISTS (
    SELECT 1
    FROM sys.key_constraints
    WHERE parent_object_id = OBJECT_ID(N'dbo.services')
      AND name = N'uk_services_category_name'
)
BEGIN
    ALTER TABLE dbo.services DROP CONSTRAINT uk_services_category_name;
END;
GO

ALTER TABLE dbo.services
    ALTER COLUMN name NVARCHAR(120) NOT NULL;
GO

ALTER TABLE dbo.services
    ALTER COLUMN description NVARCHAR(2000) NULL;
GO

ALTER TABLE dbo.services
    ADD CONSTRAINT uk_services_category_name UNIQUE (category_id, name);
GO

UPDATE dbo.services
SET
    name = N'Rửa Cơ Bản',
    description = N'Rửa xe cơ bản',
    price = 150000.00,
    updated_at = SYSDATETIME()
WHERE id = 1;
GO

UPDATE dbo.services
SET
    name = N'Rửa Cao Cấp',
    description = N'Rửa xe cao cấp',
    price = 350000.00,
    updated_at = SYSDATETIME()
WHERE id = 2;
GO

UPDATE dbo.services
SET
    name = N'Chăm Sóc Toàn Diện',
    description = N'Chăm sóc xe toàn diện',
    price = 850000.00,
    updated_at = SYSDATETIME()
WHERE id = 3;
GO
