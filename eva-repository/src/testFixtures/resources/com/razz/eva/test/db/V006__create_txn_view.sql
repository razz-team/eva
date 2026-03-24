CREATE TABLE txn_view (
    transaction_id UUID NOT NULL PRIMARY KEY,
    value INT NOT NULL,
    currency TEXT NOT NULL
);
