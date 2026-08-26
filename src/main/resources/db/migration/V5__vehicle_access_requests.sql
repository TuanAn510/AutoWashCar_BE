IF OBJECT_ID('dbo.vehicle_access_requests', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.vehicle_access_requests (
        id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        requester_id BIGINT NOT NULL,
        vehicle_id BIGINT NULL,
        license_plate VARCHAR(20) NOT NULL,
        relationship VARCHAR(80) NOT NULL,
        note VARCHAR(MAX) NULL,
        status VARCHAR(20) NOT NULL,
        review_note VARCHAR(MAX) NULL,
        reviewed_at DATETIME2(6) NULL,
        created_at DATETIME2(6) NOT NULL,
        updated_at DATETIME2(6) NOT NULL,
        CONSTRAINT fk_vehicle_access_requests_requester FOREIGN KEY (requester_id) REFERENCES dbo.users(id),
        CONSTRAINT fk_vehicle_access_requests_vehicle FOREIGN KEY (vehicle_id) REFERENCES dbo.vehicles(id),
        CONSTRAINT chk_vehicle_access_requests_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
    );

    CREATE INDEX idx_vehicle_access_requests_requester_created
        ON dbo.vehicle_access_requests(requester_id, created_at);

    CREATE INDEX idx_vehicle_access_requests_status_created
        ON dbo.vehicle_access_requests(status, created_at);

    CREATE INDEX idx_vehicle_access_requests_license_plate
        ON dbo.vehicle_access_requests(license_plate);
END
GO
