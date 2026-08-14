IF COL_LENGTH('dbo.vehicle_access_request_documents', 'document_type') IS NULL
BEGIN
    ALTER TABLE dbo.vehicle_access_request_documents
        ADD document_type NVARCHAR(20) NOT NULL DEFAULT 'PLATE';
END
GO