-- Insert dummy transaction data into the 'transactions' table
INSERT INTO transactions (id, to_address, amount_ether, status, transaction_hash, error_message, retry_count,
                          created_at, updated_at, version)
VALUES ('6d57f63b-cf9f-4e1b-a34f-cb26f0e99f63', '0x1234567890abcdef1234567890abcdef12345678', 10.5, 'PENDING',
        '0xabc123xyz456hash7890', NULL, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1),
       ('74e5e68f-b8c2-408d-b44b-f2e6977d381b', '0x234567890abcdef1234567890abcdef12345679', 5.3, 'COMPLETED',
        '0xdef456ghi789hash1234', 'Some error occurred', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1),
       ('58d789c2-634d-4b8f-8f91-e9a4299a460f', '0x345678901abcdef1234567890abcdef12345680', 1.2, 'FAILED',
        '0x1234567890hashabcdef456', 'Timeout error', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1),
       ('c6e5c1be-c524-4ad6-a83f-b365e0cc6ff9', '0x456789012abcdef1234567890abcdef12345681', 3.75, 'PENDING',
        '0x234567890abcdefhash78901234', NULL, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1),
       ('4d25db1f-1e72-4c09-bd58-67db23bdbf6b', '0x567890123abcdef1234567890abcdef12345682', 7.1, 'COMPLETED',
        '0x345678901abcdefhash123456789', 'Some minor issue', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1);