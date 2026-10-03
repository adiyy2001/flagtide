CREATE TABLE instance_stats (
    instance_id text NOT NULL,
    project_key text NOT NULL,
    environment_key text NOT NULL,
    updated_at timestamptz NOT NULL,
    connected integer NOT NULL,
    buckets jsonb NOT NULL,
    PRIMARY KEY (instance_id, project_key, environment_key)
);

CREATE INDEX instance_stats_by_environment ON instance_stats (project_key, environment_key, updated_at);
