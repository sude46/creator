ALTER TABLE operation_log ADD COLUMN reason VARCHAR(500);
CREATE INDEX idx_operation_target ON operation_log(target_type, target_id, id);
