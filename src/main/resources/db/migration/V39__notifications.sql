IF OBJECT_ID('dbo.notifications', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.notifications (
        id BIGINT IDENTITY(1,1) PRIMARY KEY,
        recipient_id BIGINT NOT NULL,
        type VARCHAR(60) NOT NULL,
        title NVARCHAR(160) NOT NULL,
        message NVARCHAR(500) NOT NULL,
        target_type VARCHAR(60),
        target_id BIGINT,
        is_read BIT NOT NULL DEFAULT 0,
        created_at DATETIME2(6) NOT NULL,
        updated_at DATETIME2(6) NOT NULL,
        CONSTRAINT fk_notifications_recipient
            FOREIGN KEY (recipient_id) REFERENCES dbo.users(id)
    );

    CREATE INDEX idx_notifications_recipient_created
        ON dbo.notifications(recipient_id, created_at DESC, id DESC);

    CREATE INDEX idx_notifications_recipient_unread
        ON dbo.notifications(recipient_id, is_read, created_at DESC);
END;
