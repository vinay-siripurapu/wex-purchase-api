-- =============================================================================
-- V1__create_purchase_transactions.sql
-- Initial schema: purchase_transactions table
-- Compatible with Aurora MySQL 8.0+
-- =============================================================================

CREATE TABLE purchase_transactions (
    id               CHAR(36)       NOT NULL,
    idempotency_key  VARCHAR(64)    NOT NULL,
    description      VARCHAR(50)    NOT NULL,
    transaction_date DATE           NOT NULL,
    purchase_amount  DECIMAL(17, 2) NOT NULL,

    CONSTRAINT pk_purchase_transactions PRIMARY KEY (id),
    CONSTRAINT uc_idempotency_key       UNIQUE      (idempotency_key)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- Index on transaction_date to support future range queries
CREATE INDEX idx_transaction_date ON purchase_transactions (transaction_date);
