ALTER TABLE pet_posts DROP COLUMN exact_location;

ALTER TABLE pet_posts ADD COLUMN latitude DOUBLE PRECISION NOT NULL DEFAULT 0;
ALTER TABLE pet_posts ADD COLUMN longitude DOUBLE PRECISION NOT NULL DEFAULT 0;

ALTER TABLE pet_posts ALTER COLUMN latitude DROP DEFAULT;
ALTER TABLE pet_posts ALTER COLUMN longitude DROP DEFAULT;

ALTER TABLE pet_posts ADD COLUMN exact_location geography(Point, 4326)
    GENERATED ALWAYS AS (ST_MakePoint(longitude, latitude)::geography) STORED;

CREATE INDEX idx_pet_posts_location_v2 ON pet_posts USING GIST (exact_location);