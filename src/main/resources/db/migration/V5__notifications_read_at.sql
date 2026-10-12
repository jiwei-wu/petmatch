-- Track when a notification was read (NULL = unread) and speed up "my latest notifications".
ALTER TABLE notifications ADD COLUMN read_at TIMESTAMPTZ;
CREATE INDEX idx_notifications_user_created ON notifications (user_id, created_at DESC);