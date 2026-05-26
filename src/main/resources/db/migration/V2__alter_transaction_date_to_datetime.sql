-- =============================================================================
-- V2__alter_transaction_date_to_datetime.sql
-- Change transaction_date from DATE to DATETIME to capture full timestamp.
-- Compatible with Aurora MySQL 8.0+
-- =============================================================================

ALTER TABLE purchase_transactions
    MODIFY COLUMN transaction_date DATETIME NOT NULL;
