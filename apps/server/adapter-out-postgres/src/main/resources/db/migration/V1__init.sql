CREATE TABLE projects (
    project_key text PRIMARY KEY,
    revision bigint NOT NULL,
    document jsonb NOT NULL,
    created_at timestamptz NOT NULL
);

CREATE TABLE environment_versions (
    project_key text NOT NULL,
    environment_key text NOT NULL,
    version bigint NOT NULL,
    PRIMARY KEY (project_key, environment_key)
);

CREATE TABLE flags (
    project_key text NOT NULL,
    flag_key text NOT NULL,
    revision bigint NOT NULL,
    archived boolean NOT NULL,
    document jsonb NOT NULL,
    updated_at timestamptz NOT NULL,
    PRIMARY KEY (project_key, flag_key)
);

CREATE TABLE segments (
    project_key text NOT NULL,
    environment_key text NOT NULL,
    segment_key text NOT NULL,
    revision bigint NOT NULL,
    document jsonb NOT NULL,
    updated_at timestamptz NOT NULL,
    PRIMARY KEY (project_key, environment_key, segment_key)
);

CREATE TABLE api_keys (
    id text PRIMARY KEY,
    kind text NOT NULL CHECK (kind IN ('ADMIN', 'SDK')),
    project_key text NOT NULL,
    environment_key text NOT NULL,
    label text NOT NULL,
    lookup text NOT NULL UNIQUE,
    created_at timestamptz NOT NULL
);

CREATE INDEX api_keys_by_environment ON api_keys (project_key, environment_key, created_at, id);

CREATE TABLE change_log (
    project_key text NOT NULL,
    environment_key text NOT NULL,
    version bigint NOT NULL,
    committed_at timestamptz NOT NULL,
    changes jsonb NOT NULL,
    PRIMARY KEY (project_key, environment_key, version),
    FOREIGN KEY (project_key, environment_key) REFERENCES environment_versions (project_key, environment_key)
);

CREATE TABLE audit_log (
    sequence bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id text NOT NULL UNIQUE,
    project_key text NOT NULL,
    environment_key text,
    environment_version bigint,
    entity_type text NOT NULL,
    entity_key text NOT NULL,
    action text NOT NULL,
    author text NOT NULL,
    occurred_at timestamptz NOT NULL,
    before_state jsonb,
    after_state jsonb
);

CREATE INDEX audit_log_by_project ON audit_log (project_key, sequence DESC);
CREATE INDEX audit_log_by_entity ON audit_log (project_key, entity_key, sequence DESC);
