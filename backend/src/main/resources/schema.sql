CREATE TABLE player_rank (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(32) NOT NULL UNIQUE,
    name VARCHAR(32) NOT NULL,
    level INT NOT NULL,
    min_score INT NOT NULL,
    max_score INT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE game_role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(32) NOT NULL UNIQUE,
    name VARCHAR(32) NOT NULL,
    description VARCHAR(255),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE player (
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
    CONSTRAINT fk_player_selected_role FOREIGN KEY (selected_role_id) REFERENCES game_role (id)
);

CREATE INDEX idx_player_rank_score ON player (rank_score);
CREATE INDEX idx_player_rank_id ON player (rank_id);
CREATE INDEX idx_player_selected_role_id ON player (selected_role_id);

CREATE TABLE friendship (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    requester_id BIGINT NOT NULL,
    addressee_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_friendship_requester FOREIGN KEY (requester_id) REFERENCES player (id),
    CONSTRAINT fk_friendship_addressee FOREIGN KEY (addressee_id) REFERENCES player (id),
    CONSTRAINT uk_friendship_request_pair UNIQUE (requester_id, addressee_id)
);

CREATE INDEX idx_friendship_requester_status ON friendship (requester_id, status);
CREATE INDEX idx_friendship_addressee_status ON friendship (addressee_id, status);
CREATE INDEX idx_friendship_pair_status ON friendship (requester_id, addressee_id, status);
