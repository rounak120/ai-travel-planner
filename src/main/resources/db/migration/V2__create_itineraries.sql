-- Immutable snapshots: a failed generation never replaces the previous plan.
CREATE TABLE itineraries (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    trip_id UUID NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    document JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT itinerary_document_object CHECK (jsonb_typeof(document) = 'object')
);
CREATE INDEX itineraries_trip_latest_idx ON itineraries (trip_id, id DESC);
