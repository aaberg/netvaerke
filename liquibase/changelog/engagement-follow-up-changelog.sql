-- changeset engagement:2

CREATE TABLE engagement.follow_up_rule (
    id UUID PRIMARY KEY,
    tenant UUID NOT NULL,
    resource_id UUID NOT NULL,
    cadence_amount INTEGER NOT NULL CHECK (cadence_amount > 0),
    cadence_unit VARCHAR(16) NOT NULL,
    last_completed_on DATE,
    retired_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT follow_up_rule_cadence_unit_check
        CHECK (cadence_unit IN ('DAYS', 'WEEKS', 'MONTHS', 'YEARS')),
    CONSTRAINT follow_up_rule_tenant_resource_id_key
        UNIQUE (tenant, resource_id, id)
);

CREATE UNIQUE INDEX follow_up_rule_active_resource_idx
    ON engagement.follow_up_rule (tenant, resource_id)
    WHERE retired_at IS NULL;

CREATE TABLE engagement.follow_up (
    id UUID PRIMARY KEY,
    tenant UUID NOT NULL,
    resource_id UUID NOT NULL,
    rule_id UUID,
    due_on DATE NOT NULL,
    due_date_revision BIGINT NOT NULL DEFAULT 1 CHECK (due_date_revision > 0),
    status VARCHAR(16) NOT NULL,
    cadence_amount INTEGER,
    cadence_unit VARCHAR(16),
    completed_on DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT follow_up_rule_fk
        FOREIGN KEY (tenant, resource_id, rule_id)
        REFERENCES engagement.follow_up_rule (tenant, resource_id, id),
    CONSTRAINT follow_up_status_check
        CHECK (status IN ('OPEN', 'DONE', 'CANCELLED')),
    CONSTRAINT follow_up_completion_check
        CHECK (
            (status = 'DONE' AND completed_on IS NOT NULL)
            OR (status IN ('OPEN', 'CANCELLED') AND completed_on IS NULL)
        ),
    CONSTRAINT follow_up_schedule_check
        CHECK (
            (rule_id IS NULL AND cadence_amount IS NULL AND cadence_unit IS NULL)
            OR (
                rule_id IS NOT NULL
                AND cadence_amount IS NOT NULL
                AND cadence_amount > 0
                AND cadence_unit IS NOT NULL
                AND cadence_unit IN ('DAYS', 'WEEKS', 'MONTHS', 'YEARS')
            )
        )
);

CREATE UNIQUE INDEX follow_up_open_rule_idx
    ON engagement.follow_up (tenant, rule_id)
    WHERE status = 'OPEN' AND rule_id IS NOT NULL;

CREATE INDEX follow_up_open_due_idx
    ON engagement.follow_up (tenant, due_on, id)
    WHERE status = 'OPEN';

CREATE INDEX follow_up_resource_due_idx
    ON engagement.follow_up (tenant, resource_id, due_on DESC, id DESC);
