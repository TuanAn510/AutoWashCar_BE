IF OBJECT_ID('dbo.refresh_tokens', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.refresh_tokens (
        id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        user_id BIGINT NOT NULL,
        token_hash VARCHAR(128) NOT NULL,
        expires_at DATETIME2(6) NOT NULL,
        revoked_at DATETIME2(6) NULL,
        replaced_by_token_hash VARCHAR(128) NULL,
        created_at DATETIME2(6) NOT NULL,
        updated_at DATETIME2(6) NOT NULL,
        CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES dbo.users(id),
        CONSTRAINT uk_refresh_tokens_token_hash UNIQUE (token_hash)
    );

    CREATE INDEX idx_refresh_tokens_user_expires
        ON dbo.refresh_tokens(user_id, expires_at);

    CREATE INDEX idx_refresh_tokens_revoked
        ON dbo.refresh_tokens(revoked_at);
END
GO
