-- P3-02: a token carries the version of its account when it was issued; counting the version up revokes every token
-- issued before, with no list of revoked tokens to keep (FR-01.6, FR-01.7, BR-41).
ALTER TABLE employee ADD COLUMN token_version INTEGER NOT NULL DEFAULT 0 CHECK (token_version >= 0);
