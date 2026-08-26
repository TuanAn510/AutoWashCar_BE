IF OBJECT_ID('dbo.vehicle_access_request_documents', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.vehicle_access_request_documents (
        id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        access_request_id BIGINT NOT NULL,
        url NVARCHAR(500) NOT NULL,
        original_name NVARCHAR(255) NULL,
        mime_type NVARCHAR(127) NULL,
        sort_order INT NOT NULL DEFAULT 0,
        created_at DATETIME2(6) NOT NULL DEFAULT SYSDATETIME(),
        updated_at DATETIME2(6) NOT NULL DEFAULT SYSDATETIME(),
        CONSTRAINT fk_access_request_documents_request
            FOREIGN KEY (access_request_id) REFERENCES dbo.vehicle_access_requests(id)
    );

    CREATE INDEX idx_access_request_documents_rid
        ON dbo.vehicle_access_request_documents(access_request_id);
END
GO