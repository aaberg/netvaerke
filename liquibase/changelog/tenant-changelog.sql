-- changeset tenant:1

CREATE SCHEMA tenant;

CREATE TABLE tenant.tenant(
    id UUID PRIMARY KEY,
    type varchar(255) NOT NULL,
    name varchar(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE tenant.tenant_member(
    tenant_id UUID NOT NULL REFERENCES tenant.tenant(id) ON DELETE CASCADE,
    user_id UUID NOT NULL,
    role varchar(255) NOT NULL,
    PRIMARY KEY (tenant_id, user_id)
);

CREATE INDEX tenant_member_user_id_idx ON tenant.tenant_member(user_id);
