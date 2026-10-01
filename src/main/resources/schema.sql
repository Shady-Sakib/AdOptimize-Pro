-- =====================================================================
-- Online Advertisement Optimizer - MySQL 8 schema
-- Every statement is idempotent, so this runs safely on each start.
-- =====================================================================

CREATE TABLE IF NOT EXISTS users (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    name            VARCHAR(60)  NOT NULL,
    email           VARCHAR(120) NOT NULL,
    password_hash   VARCHAR(100) NOT NULL,
    company         VARCHAR(100) NULL,
    role            VARCHAR(20)  NOT NULL,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      DATETIME     NOT NULL,
    last_login_at   DATETIME     NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT ck_users_role CHECK (role IN ('ADVERTISER', 'ADMIN'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS campaigns (
    id                BIGINT         NOT NULL AUTO_INCREMENT,
    user_id           BIGINT         NOT NULL,
    title             VARCHAR(100)   NOT NULL,
    description       VARCHAR(1000)  NOT NULL,
    audience          VARCHAR(30)    NOT NULL,
    ad_type           VARCHAR(20)    NOT NULL,
    budget            DECIMAL(12, 2) NOT NULL,
    daily_budget      DECIMAL(12, 2) NOT NULL,
    spent             DECIMAL(12, 2) NOT NULL DEFAULT 0,
    start_date        DATE           NOT NULL,
    end_date          DATE           NOT NULL,
    keywords          VARCHAR(700)   NULL,
    status            VARCHAR(20)    NOT NULL,
    rejection_reason  VARCHAR(300)   NULL,
    flagged           BOOLEAN        NOT NULL DEFAULT FALSE,
    flag_reason       VARCHAR(200)   NULL,
    flag_cleared      BOOLEAN        NOT NULL DEFAULT FALSE,
    peak_hours_only   BOOLEAN        NOT NULL DEFAULT FALSE,
    quality_score     DECIMAL(4, 2)  NOT NULL DEFAULT 1.00,
    impressions       BIGINT         NOT NULL DEFAULT 0,
    clicks            BIGINT         NOT NULL DEFAULT 0,
    conversions       BIGINT         NOT NULL DEFAULT 0,
    budget_alert_sent BOOLEAN        NOT NULL DEFAULT FALSE,
    reviewed_by       BIGINT         NULL,
    reviewed_at       DATETIME       NULL,
    created_at        DATETIME       NOT NULL,
    updated_at        DATETIME       NOT NULL,
    deleted_at        DATETIME       NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_campaigns_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_campaigns_dates CHECK (end_date > start_date),
    CONSTRAINT ck_campaigns_money CHECK (budget > 0 AND daily_budget > 0 AND spent >= 0),
    INDEX idx_campaigns_user (user_id),
    INDEX idx_campaigns_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS campaign_daily_stats (
    campaign_id  BIGINT         NOT NULL,
    stat_date    DATE           NOT NULL,
    impressions  BIGINT         NOT NULL DEFAULT 0,
    clicks       BIGINT         NOT NULL DEFAULT 0,
    conversions  BIGINT         NOT NULL DEFAULT 0,
    spend        DECIMAL(12, 2) NOT NULL DEFAULT 0,
    PRIMARY KEY (campaign_id, stat_date),
    CONSTRAINT fk_stats_campaign FOREIGN KEY (campaign_id) REFERENCES campaigns (id),
    INDEX idx_stats_date (stat_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS payments (
    id           BIGINT         NOT NULL AUTO_INCREMENT,
    user_id      BIGINT         NOT NULL,
    amount       DECIMAL(12, 2) NOT NULL,
    card_brand   VARCHAR(20)    NOT NULL,
    card_last4   CHAR(4)        NOT NULL,
    description  VARCHAR(120)   NOT NULL,
    status       VARCHAR(20)    NOT NULL,
    created_at   DATETIME       NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_payments_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_payments_amount CHECK (amount > 0),
    INDEX idx_payments_user (user_id),
    INDEX idx_payments_created (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS notifications (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL,
    type        VARCHAR(20)  NOT NULL,
    title       VARCHAR(120) NOT NULL,
    message     VARCHAR(500) NOT NULL,
    is_read     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  DATETIME     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id),
    INDEX idx_notifications_user (user_id, is_read)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS support_tickets (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    user_id      BIGINT        NOT NULL,
    subject      VARCHAR(120)  NOT NULL,
    message      VARCHAR(2000) NOT NULL,
    priority     VARCHAR(10)   NOT NULL,
    status       VARCHAR(10)   NOT NULL,
    admin_reply  VARCHAR(2000) NULL,
    resolved_by  BIGINT        NULL,
    resolved_at  DATETIME      NULL,
    created_at   DATETIME      NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_tickets_user FOREIGN KEY (user_id) REFERENCES users (id),
    INDEX idx_tickets_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS optimization_logs (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    campaign_id  BIGINT       NOT NULL,
    user_id      BIGINT       NOT NULL,
    type         VARCHAR(30)  NOT NULL,
    description  VARCHAR(300) NOT NULL,
    applied_at   DATETIME     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_optlogs_campaign FOREIGN KEY (campaign_id) REFERENCES campaigns (id),
    INDEX idx_optlogs_campaign (campaign_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS system_settings (
    id                  TINYINT        NOT NULL,
    cpc_rate            DECIMAL(8, 2)  NOT NULL,
    cpm_rate            DECIMAL(8, 2)  NOT NULL,
    min_budget          DECIMAL(12, 2) NOT NULL,
    max_daily_budget    DECIMAL(12, 2) NOT NULL,
    platform_fee        DECIMAL(5, 2)  NOT NULL,
    peak_hours_start    INT            NOT NULL,
    peak_hours_end      INT            NOT NULL,
    auto_approve        BOOLEAN        NOT NULL,
    content_filter      BOOLEAN        NOT NULL,
    budget_alerts       BOOLEAN        NOT NULL,
    simulation_enabled  BOOLEAN        NOT NULL,
    banned_words        VARCHAR(1000)  NOT NULL,
    updated_at          DATETIME       NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_settings_single_row CHECK (id = 1)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

INSERT IGNORE INTO system_settings
    (id, cpc_rate, cpm_rate, min_budget, max_daily_budget, platform_fee, peak_hours_start, peak_hours_end,
     auto_approve, content_filter, budget_alerts, simulation_enabled, banned_words, updated_at)
VALUES
    (1, 0.45, 2.50, 50.00, 10000.00, 5.00, 18, 22,
     FALSE, TRUE, TRUE, TRUE,
     'free money,guaranteed,make $,miracle,100%,spam,xxx,get rich,risk-free,act now', '2026-01-01 00:00:00');
