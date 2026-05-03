CREATE TABLE IF NOT EXISTS password_entries (
  id UUID PRIMARY KEY,
  name TEXT NOT NULL,
  password TEXT NOT NULL,
  comment_text TEXT NOT NULL DEFAULT '',
  created_at BIGINT NOT NULL,
  deleted_at BIGINT
);

CREATE INDEX IF NOT EXISTS idx_password_entries_deleted_at
  ON password_entries (deleted_at);

CREATE INDEX IF NOT EXISTS idx_password_entries_created_at
  ON password_entries (created_at);

CREATE INDEX IF NOT EXISTS idx_password_entries_password
  ON password_entries (password);

CREATE TABLE IF NOT EXISTS password_history (
  id BIGSERIAL PRIMARY KEY,
  entry_id UUID NOT NULL REFERENCES password_entries(id) ON DELETE CASCADE,
  password TEXT NOT NULL,
  changed_at BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_password_history_entry_changed
  ON password_history (entry_id, changed_at);
