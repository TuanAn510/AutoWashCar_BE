;WITH duplicate_demo_earnings AS (
    SELECT
        transaction_row.id,
        ROW_NUMBER() OVER (
            PARTITION BY transaction_row.customer_id
            ORDER BY transaction_row.created_at, transaction_row.id
        ) AS duplicate_rank
    FROM dbo.loyalty_transactions transaction_row
    JOIN dbo.users customer ON customer.id = transaction_row.customer_id
    WHERE customer.phone IN ('0911111111', '0922222222', '0933333333')
      AND transaction_row.type = 'EARN'
      AND transaction_row.booking_id IS NULL
      AND NOT EXISTS (
          SELECT 1 FROM dbo.point_lots lot
          WHERE lot.earn_transaction_id = transaction_row.id
      )
)
DELETE transaction_row
FROM dbo.loyalty_transactions transaction_row
JOIN duplicate_demo_earnings duplicate_row ON duplicate_row.id = transaction_row.id
WHERE duplicate_row.duplicate_rank > 1;
GO

INSERT INTO dbo.point_lots (
    customer_id,
    earn_transaction_id,
    initial_points,
    remaining_points,
    earned_at,
    expires_at,
    created_at,
    updated_at
)
SELECT
    transaction_row.customer_id,
    transaction_row.id,
    transaction_row.points,
    transaction_row.points,
    transaction_row.created_at,
    COALESCE(transaction_row.expires_at, DATEADD(MONTH, 12, transaction_row.created_at)),
    transaction_row.created_at,
    transaction_row.created_at
FROM dbo.loyalty_transactions transaction_row
WHERE transaction_row.type = 'EARN'
  AND transaction_row.status = 'POSTED'
  AND transaction_row.points > 0
  AND NOT EXISTS (
      SELECT 1 FROM dbo.point_lots lot
      WHERE lot.earn_transaction_id = transaction_row.id
  );
GO

;WITH demo_balances AS (
    SELECT
        account.id AS account_id,
        COALESCE(SUM(CASE
            WHEN transaction_row.type = 'EARN' AND transaction_row.status = 'POSTED'
                THEN transaction_row.points
            WHEN transaction_row.type <> 'EARN'
                THEN transaction_row.points
            ELSE 0
        END), 0) AS current_points,
        COALESCE(SUM(CASE
            WHEN transaction_row.type = 'EARN' AND transaction_row.status = 'POSTED'
                THEN transaction_row.points
            ELSE 0
        END), 0) AS lifetime_points
    FROM dbo.loyalty_accounts account
    JOIN dbo.users customer ON customer.id = account.customer_id
    LEFT JOIN dbo.loyalty_transactions transaction_row
        ON transaction_row.customer_id = account.customer_id
    WHERE customer.phone IN ('0911111111', '0922222222', '0933333333')
    GROUP BY account.id
)
UPDATE account
SET
    account.current_points = CASE WHEN balance.current_points < 0 THEN 0 ELSE balance.current_points END,
    account.lifetime_points = balance.lifetime_points,
    account.updated_at = SYSDATETIME()
FROM dbo.loyalty_accounts account
JOIN demo_balances balance ON balance.account_id = account.id;
GO
