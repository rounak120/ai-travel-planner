-- One current provider-backed location per trip; existing trips remain unchanged.
CREATE TABLE trip_locations (
    trip_id UUID PRIMARY KEY REFERENCES trips(id) ON DELETE CASCADE,
    document JSONB NOT NULL,
    CONSTRAINT trip_location_object CHECK (jsonb_typeof(document) = 'object')
);
