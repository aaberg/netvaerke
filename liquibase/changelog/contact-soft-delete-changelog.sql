-- changeset contact:2
    ADD COLUMN deleted_at TIMESTAMPTZ;

DROP INDEX contact.contact_tenant_idx;

CREATE INDEX contact_tenant_active_created_idx
    ON contact.contact (tenant, created_at, id)
    WHERE deleted_at IS NULL;
