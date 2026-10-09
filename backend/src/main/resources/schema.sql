-- 幂等建表脚本：应用启动时自动执行（spring.sql.init.mode=always），可重复运行
-- 注意：索引内联在建表语句中（MySQL 的 CREATE INDEX 不支持 IF NOT EXISTS）

CREATE TABLE IF NOT EXISTS player_rank (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(32) NOT NULL UNIQUE,
    name VARCHAR(32) NOT NULL,
    level INT NOT NULL,
    min_score INT NOT NULL,
    max_score INT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS game_role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(32) NOT NULL UNIQUE,
    name VARCHAR(32) NOT NULL,
    description VARCHAR(255),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS player (
    id BIGINT PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    real_name VARCHAR(64),
    phone VARCHAR(20) UNIQUE,
    id_card VARCHAR(32) UNIQUE,
    balance DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    status VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    rank_id BIGINT NOT NULL,
    rank_score INT NOT NULL DEFAULT 0,
    selected_role_id BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_player_rank FOREIGN KEY (rank_id) REFERENCES player_rank (id),
    CONSTRAINT fk_player_selected_role FOREIGN KEY (selected_role_id) REFERENCES game_role (id),
    KEY idx_player_rank_score (rank_score),
    KEY idx_player_rank_id (rank_id),
    KEY idx_player_selected_role_id (selected_role_id)
);

CREATE TABLE IF NOT EXISTS friendship (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    requester_id BIGINT NOT NULL,
    addressee_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_friendship_requester FOREIGN KEY (requester_id) REFERENCES player (id),
    CONSTRAINT fk_friendship_addressee FOREIGN KEY (addressee_id) REFERENCES player (id),
    CONSTRAINT uk_friendship_request_pair UNIQUE (requester_id, addressee_id),
    KEY idx_friendship_requester_status (requester_id, status),
    KEY idx_friendship_addressee_status (addressee_id, status),
    KEY idx_friendship_pair_status (requester_id, addressee_id, status)
);
