-- Flyway applies this file once and records its checksum. Use V2 for later changes.
CREATE TABLE trips (
    id UUID PRIMARY KEY,
    destination VARCHAR(200) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    travelers INTEGER NOT NULL,
    budget NUMERIC(14, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT trips_destination_not_blank CHECK (length(trim(destination)) > 0),
    CONSTRAINT trips_dates_in_order CHECK (end_date >= start_date),
    CONSTRAINT trips_travelers_range CHECK (travelers BETWEEN 1 AND 100),
    CONSTRAINT trips_budget_positive CHECK (budget > 0),
    CONSTRAINT trips_currency_format CHECK (currency ~ '^[A-Z]{3}$')
);

-- Preserve the order of the API's interests list without comma-separated strings.
CREATE TABLE trip_interests (
    trip_id UUID NOT NULL,
    interest_order INTEGER NOT NULL,
    interest VARCHAR(80) NOT NULL,
    PRIMARY KEY (trip_id, interest_order),
    CONSTRAINT trip_interests_trip_fk FOREIGN KEY (trip_id) REFERENCES trips(id) ON DELETE CASCADE,
    CONSTRAINT trip_interests_order_range CHECK (interest_order BETWEEN 0 AND 19),
    CONSTRAINT trip_interests_not_blank CHECK (length(trim(interest)) > 0)
);

-- "start date must be today or later" belongs to creation-time validation:
-- saved trips must remain valid after their travel dates have passed.
