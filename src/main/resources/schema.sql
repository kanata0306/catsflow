-- ==========================================
-- Users
-- ユーザー情報
-- ==========================================
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,           -- ユーザーID
    name VARCHAR(100) NOT NULL,                     -- ユーザー名
    email VARCHAR(255) NOT NULL UNIQUE,             -- メールアドレス
    password_hash VARCHAR(100),                     -- 認証用パスワードハッシュ
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, -- 作成日時
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP  -- 更新日時
);

CREATE TABLE IF NOT EXISTS line_user_links (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    line_user_id VARCHAR(255) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL DEFAULT 'linked',
    linked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS sync_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    source_type VARCHAR(50) NOT NULL,
    source_name VARCHAR(255),
    status VARCHAR(20) NOT NULL,
    record_count INT DEFAULT 0,
    message VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS webhook_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id VARCHAR(255) NOT NULL UNIQUE,
    user_id VARCHAR(255),
    event_type VARCHAR(50),
    payload_hash VARCHAR(128),
    processed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ==========================================
-- Accounts
-- 銀行口座・クレジットカード・現金など
-- ==========================================
CREATE TABLE IF NOT EXISTS accounts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,           -- 口座ID
    user_id BIGINT NOT NULL,                        -- ユーザーID
    name VARCHAR(100) NOT NULL,                     -- 口座・カード名
    type VARCHAR(30) NOT NULL,                      -- 口座種別
    institution_name VARCHAR(100),                  -- 銀行名・カード会社名
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, -- 作成日時
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, -- 更新日時

    FOREIGN KEY (user_id) REFERENCES users(id),

    UNIQUE (user_id, name)
);

-- ==========================================
CREATE TABLE IF NOT EXISTS categories (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,           -- カテゴリID
    user_id BIGINT NOT NULL,                        -- ユーザーID
    name VARCHAR(50) NOT NULL,                      -- カテゴリ名
    type VARCHAR(20) NOT NULL,                      -- 収入・支出区分
    parent_id BIGINT,                               -- 親カテゴリID
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, -- 作成日時

    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (parent_id) REFERENCES categories(id),

    UNIQUE (user_id, name, type)
);

-- ==========================================
CREATE TABLE IF NOT EXISTS import_files (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,           -- CSV取込ID
    user_id BIGINT NOT NULL,                        -- ユーザーID
    file_name VARCHAR(255) NOT NULL,                -- CSVファイル名
    imported_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,-- 取込日時
    record_count INT DEFAULT 0,                     -- 取込件数

    FOREIGN KEY (user_id) REFERENCES users(id)
);


-- ==========================================
-- Transactions
-- 家計簿の入出金データ
-- ==========================================
CREATE TABLE IF NOT EXISTS transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,           -- 取引ID

    user_id BIGINT NOT NULL,                        -- ユーザーID
    account_id BIGINT NOT NULL,                     -- 口座・カードID
    category_id BIGINT,                             -- カテゴリID

    transaction_date DATE NOT NULL,                 -- 取引日
    amount DECIMAL(15,2) NOT NULL,                  -- 取引金額
    type VARCHAR(20) NOT NULL,                      -- 収入・支出区分

    description VARCHAR(255),                       -- 取引内容
    memo VARCHAR(1000),                             -- メモ
    import_file_id BIGINT,                          -- CSV取込ID
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, -- 作成日時
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, -- 更新日時

    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (account_id) REFERENCES accounts(id),
    FOREIGN KEY (category_id) REFERENCES categories(id),
    FOREIGN KEY (import_file_id) REFERENCES import_files(id)
);