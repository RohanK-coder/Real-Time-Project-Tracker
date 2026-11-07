CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE users (
    id          BIGSERIAL PRIMARY KEY,
    username    VARCHAR(50)  NOT NULL UNIQUE,
    email       VARCHAR(255) NOT NULL UNIQUE,
    password    VARCHAR(255) NOT NULL,
    role        VARCHAR(20)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE boards (
    id           BIGSERIAL PRIMARY KEY,
    name         VARCHAR(120) NOT NULL,
    description  VARCHAR(1000),
    owner_id     BIGINT       NOT NULL REFERENCES users (id),
    -- Monotonic per-board event counter. Every mutation increments it while holding
    -- the board row lock, so sequence order == commit order for that board.
    event_seq    BIGINT       NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE board_members (
    id        BIGSERIAL PRIMARY KEY,
    board_id  BIGINT      NOT NULL REFERENCES boards (id) ON DELETE CASCADE,
    user_id   BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role      VARCHAR(20) NOT NULL,
    added_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_board_member UNIQUE (board_id, user_id)
);
CREATE INDEX idx_board_members_user ON board_members (user_id);

CREATE TABLE tasks (
    id                BIGSERIAL PRIMARY KEY,
    board_id          BIGINT        NOT NULL REFERENCES boards (id) ON DELETE CASCADE,
    title             VARCHAR(200)  NOT NULL,
    description       VARCHAR(4000),
    status            VARCHAR(20)   NOT NULL,
    assignee          VARCHAR(50),
    created_by        VARCHAR(100)  NOT NULL,
    updated_by        VARCHAR(100)  NOT NULL,
    created_at        TIMESTAMPTZ   NOT NULL,
    updated_at        TIMESTAMPTZ   NOT NULL,
    -- Optimistic-locking version, checked against the client's expected version on every write.
    version           BIGINT        NOT NULL DEFAULT 0,
    -- Board event_seq of the last change; lets reconnecting clients fetch "everything after N".
    last_event_seq    BIGINT        NOT NULL,
    -- Soft delete keeps a tombstone so reconnecting clients learn about deletions.
    deleted           BOOLEAN       NOT NULL DEFAULT FALSE,
    github_issue_url  VARCHAR(500)
);
CREATE INDEX idx_tasks_board_seq ON tasks (board_id, last_event_seq);
CREATE INDEX idx_tasks_board_live ON tasks (board_id, status, updated_at DESC) WHERE deleted = FALSE;
CREATE INDEX idx_tasks_title_trgm ON tasks USING gin (lower(title) gin_trgm_ops);
CREATE INDEX idx_tasks_description_trgm ON tasks USING gin (lower(description) gin_trgm_ops);

CREATE TABLE activities (
    id          BIGSERIAL PRIMARY KEY,
    board_id    BIGINT       NOT NULL REFERENCES boards (id) ON DELETE CASCADE,
    task_id     BIGINT,
    actor       VARCHAR(100) NOT NULL,
    type        VARCHAR(40)  NOT NULL,
    summary     VARCHAR(1000) NOT NULL,
    seq         BIGINT       NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_activities_board ON activities (board_id, id DESC);
CREATE INDEX idx_activities_task ON activities (task_id, id DESC);

CREATE TABLE notifications (
    id          BIGSERIAL PRIMARY KEY,
    recipient   VARCHAR(50)  NOT NULL,
    board_id    BIGINT       REFERENCES boards (id) ON DELETE CASCADE,
    task_id     BIGINT,
    type        VARCHAR(40)  NOT NULL,
    message     VARCHAR(500) NOT NULL,
    actor       VARCHAR(100) NOT NULL,
    is_read     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_notifications_recipient ON notifications (recipient, id DESC);
CREATE INDEX idx_notifications_unread ON notifications (recipient) WHERE is_read = FALSE;
