-- changeset engagement:1

CREATE SCHEMA engagement;

CREATE TABLE engagement.interaction (
    id UUID PRIMARY KEY,
    tenant UUID NOT NULL,
    contact_id UUID NOT NULL,
    user_id UUID NOT NULL,
    channel VARCHAR(16) NOT NULL,
    notes TEXT,
    occurred_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT interaction_channel_check
        CHECK (channel IN ('PHONE', 'EMAIL', 'TEXT', 'CHAT', 'IN_PERSON'))
);

CREATE INDEX interaction_tenant_contact_occurred_idx
    ON engagement.interaction (tenant, contact_id, occurred_at DESC, id DESC);

CREATE INDEX interaction_tenant_user_occurred_idx
    ON engagement.interaction (tenant, user_id, occurred_at DESC, id DESC);
