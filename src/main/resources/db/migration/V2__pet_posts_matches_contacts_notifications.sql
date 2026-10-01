CREATE TABLE pet_posts (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id),
    type            VARCHAR(20) NOT NULL CHECK (type IN ('LOST', 'SIGHTING')),
    species         VARCHAR(30) NOT NULL,
    color           VARCHAR(50),
    size            VARCHAR(20),
    has_collar      BOOLEAN,
    description     TEXT,
    microchip_hash  VARCHAR(128),
    event_time      TIMESTAMPTZ NOT NULL,
    exact_location  geography(Point, 4326) NOT NULL,
    public_area     VARCHAR(100) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'OPEN'
                    CHECK (status IN ('OPEN', 'MATCHED', 'RESOLVED', 'EXPIRED')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_pet_posts_location ON pet_posts USING GIST (exact_location);
CREATE INDEX idx_pet_posts_type_species_status ON pet_posts (type, species, status);
CREATE INDEX idx_pet_posts_microchip ON pet_posts (microchip_hash) WHERE microchip_hash IS NOT NULL;

CREATE TABLE matches (
    id                BIGSERIAL PRIMARY KEY,
    lost_post_id      BIGINT NOT NULL REFERENCES pet_posts(id),
    sighting_post_id  BIGINT NOT NULL REFERENCES pet_posts(id),
    score             NUMERIC(5,2) NOT NULL,
    score_breakdown   JSONB NOT NULL,
    status            VARCHAR(20) NOT NULL DEFAULT 'SUGGESTED'
                      CHECK (status IN ('SUGGESTED', 'CONFIRMED', 'DISMISSED')),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (lost_post_id, sighting_post_id)
);

CREATE TABLE contact_requests (
    id            BIGSERIAL PRIMARY KEY,
    match_id      BIGINT NOT NULL REFERENCES matches(id),
    from_user_id  BIGINT NOT NULL REFERENCES users(id),
    to_user_id    BIGINT NOT NULL REFERENCES users(id),
    message       TEXT NOT NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                  CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED')),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE notifications (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES users(id),
    type       VARCHAR(50) NOT NULL,
    payload    JSONB NOT NULL,
    sent_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);