CREATE TABLE feedback.feedback (
    feedback_id varchar(36)  PRIMARY KEY,
    analysis_id varchar(64)  NOT NULL UNIQUE,
    node_id     varchar(64)  NOT NULL,
    verdict     varchar(16)  NOT NULL,
    cause       varchar(16),
    comment     varchar(1000),
    author      varchar(255) NOT NULL,
    created_at  timestamptz  NOT NULL
);

CREATE TABLE feedback.analysis_snapshot (
    analysis_id   varchar(64)      PRIMARY KEY,
    node_id       varchar(64)      NOT NULL,
    window_start  timestamptz      NOT NULL,
    state         varchar(16)      NOT NULL,
    score         double precision NOT NULL,
    model_version integer          NOT NULL,
    vector        jsonb            NOT NULL,
    feature_names jsonb            NOT NULL,
    created_at    timestamptz      NOT NULL
);
CREATE INDEX snapshot_node_window ON feedback.analysis_snapshot (node_id, window_start);

CREATE TABLE feedback.suggestion (
    id         varchar(36)  PRIMARY KEY,
    text       varchar(2000) NOT NULL,
    author     varchar(255) NOT NULL,
    created_at timestamptz  NOT NULL
);
