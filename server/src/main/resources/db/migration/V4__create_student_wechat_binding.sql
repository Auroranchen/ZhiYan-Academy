CREATE TABLE IF NOT EXISTS student_wechat_binding (
    id BIGINT NOT NULL AUTO_INCREMENT,
    studentid INT NOT NULL,
    appid VARCHAR(64) NOT NULL,
    openid VARCHAR(128) NOT NULL,
    unionid VARCHAR(128) NULL,
    bindtime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lastlogintime DATETIME NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_wechat_app_openid (appid, openid),
    UNIQUE KEY uk_wechat_app_student (appid, studentid),
    KEY idx_wechat_studentid (studentid),
    CONSTRAINT fk_wechat_binding_student
        FOREIGN KEY (studentid) REFERENCES student(studentid) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
