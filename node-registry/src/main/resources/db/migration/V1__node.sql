CREATE TABLE registry.node (
    node_id    varchar(64) PRIMARY KEY,
    name       varchar(255)     NOT NULL,
    type       varchar(32)      NOT NULL,
    warning    double precision NOT NULL,
    anomaly    double precision NOT NULL,
    enabled    boolean          NOT NULL,
    created_at timestamptz      NOT NULL
);
