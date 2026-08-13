CREATE TABLE vehicle_images (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    vehicle_id BIGINT NOT NULL,
    url NVARCHAR(500) NOT NULL,
    original_name NVARCHAR(255) NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
    updated_at DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
    CONSTRAINT fk_vehicle_images_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicles(id)
);

CREATE INDEX idx_vehicle_images_vehicle_id ON vehicle_images(vehicle_id);