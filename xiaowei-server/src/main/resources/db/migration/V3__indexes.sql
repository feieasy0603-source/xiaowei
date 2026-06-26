CREATE INDEX idx_jobs_order ON jobs(order_id);
CREATE INDEX idx_paper_files_paper ON paper_files(paper_id);
CREATE INDEX idx_paper_files_job ON paper_files(job_id);
CREATE INDEX idx_orders_paper ON orders(paper_id);
