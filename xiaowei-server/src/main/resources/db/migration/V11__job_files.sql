CREATE TABLE job_files (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    job_id      BIGINT NOT NULL,
    user_id     BIGINT NOT NULL,
    file_type   VARCHAR(32) NOT NULL,
    file_name   VARCHAR(256) NOT NULL,
    storage_key VARCHAR(512) NOT NULL,
    size_bytes  BIGINT,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_job_files_job ON job_files(job_id);
CREATE INDEX idx_job_files_user ON job_files(user_id);
