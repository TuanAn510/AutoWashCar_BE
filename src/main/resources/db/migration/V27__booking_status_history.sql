IF OBJECT_ID('dbo.booking_status_history', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.booking_status_history (
        id BIGINT IDENTITY(1,1) PRIMARY KEY,
        booking_id BIGINT NOT NULL,
        old_status VARCHAR(30),
        new_status VARCHAR(30) NOT NULL,
        actor_id BIGINT,
        actor_role VARCHAR(20),
        changed_at DATETIME2(6) NOT NULL,
        evidence_image_url VARCHAR(500),
        note NVARCHAR(500),
        created_at DATETIME2(6) NOT NULL,
        updated_at DATETIME2(6) NOT NULL,
        CONSTRAINT fk_booking_status_history_booking
            FOREIGN KEY (booking_id) REFERENCES dbo.bookings(id),
        CONSTRAINT fk_booking_status_history_actor
            FOREIGN KEY (actor_id) REFERENCES dbo.users(id),
        CONSTRAINT chk_booking_status_history_old_status
            CHECK (old_status IS NULL OR old_status IN ('PENDING', 'CONFIRMED', 'IN_QUEUE', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
        CONSTRAINT chk_booking_status_history_new_status
            CHECK (new_status IN ('PENDING', 'CONFIRMED', 'IN_QUEUE', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
        CONSTRAINT chk_booking_status_history_actor_role
            CHECK (actor_role IS NULL OR actor_role IN ('ROLE_ADMIN', 'ROLE_STAFF', 'ROLE_CUSTOMER'))
    );

    CREATE INDEX idx_booking_status_history_booking_changed
        ON dbo.booking_status_history(booking_id, changed_at, id);
END;
