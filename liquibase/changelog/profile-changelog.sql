-- changeset profile:1

CREATE SCHEMA profile;

CREATE TABLE profile.Profile(
    user_id UUID PRIMARY KEY,
    name varchar(255) NOT NULL,
    email varchar(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
