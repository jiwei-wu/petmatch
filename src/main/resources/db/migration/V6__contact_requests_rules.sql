-- One request per sender per match (also stops repeated pestering after a decline),
-- and nobody can send a request to themselves.
CREATE UNIQUE INDEX uq_contact_requests_match_sender ON contact_requests (match_id, from_user_id);
ALTER TABLE contact_requests ADD CONSTRAINT chk_contact_requests_not_self CHECK (from_user_id <> to_user_id);