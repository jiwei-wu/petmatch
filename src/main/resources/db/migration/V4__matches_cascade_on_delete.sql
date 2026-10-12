-- When a post is deleted, its matches (and anything hanging off them) must disappear too.
-- This is both a data-hygiene rule and a privacy rule (right to erasure).
ALTER TABLE matches DROP CONSTRAINT IF EXISTS matches_lost_post_id_fkey;
ALTER TABLE matches DROP CONSTRAINT IF EXISTS matches_sighting_post_id_fkey;
ALTER TABLE matches ADD CONSTRAINT matches_lost_post_id_fkey
    FOREIGN KEY (lost_post_id) REFERENCES pet_posts(id) ON DELETE CASCADE;
ALTER TABLE matches ADD CONSTRAINT matches_sighting_post_id_fkey
    FOREIGN KEY (sighting_post_id) REFERENCES pet_posts(id) ON DELETE CASCADE;

ALTER TABLE contact_requests DROP CONSTRAINT IF EXISTS contact_requests_match_id_fkey;
ALTER TABLE contact_requests ADD CONSTRAINT contact_requests_match_id_fkey
    FOREIGN KEY (match_id) REFERENCES matches(id) ON DELETE CASCADE;