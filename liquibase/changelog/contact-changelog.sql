-- changeset contact:1

CREATE SCHEMA contact;

CREATE TABLE contact.Contact(
    id UUID PRIMARY KEY,
    name varchar(255) NOT NULL,
    tenant UUID NOT NULL,
    details JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX contact_tenant_idx ON contact.Contact(tenant);