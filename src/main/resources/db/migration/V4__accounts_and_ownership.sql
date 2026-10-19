CREATE TABLE app_users (
    username VARCHAR(50) PRIMARY KEY,
    password_hash VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
-- Existing trips remain unassigned. No account can claim them through the API.
ALTER TABLE trips ADD COLUMN owner_name VARCHAR(50) REFERENCES app_users(username);
CREATE INDEX trips_owner_newest_idx ON trips(owner_name, created_at DESC, id DESC);
