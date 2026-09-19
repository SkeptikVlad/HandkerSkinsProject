DROP TABLE IF EXISTS skin_price_history CASCADE;
DROP TABLE IF EXISTS skins CASCADE;

CREATE TABLE skins (
                       id BIGSERIAL PRIMARY KEY,
                       market_hash_name VARCHAR(255) NOT NULL UNIQUE
);

CREATE TABLE skin_price_history (
                                    id BIGSERIAL PRIMARY KEY,
                                    skin_id BIGINT NOT NULL,
                                    min_price DECIMAL(10, 2),
                                    top_order DECIMAL(10, 2),
                                    volume INT,
                                    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                                    CONSTRAINT fk_skin_price_history_skin FOREIGN KEY (skin_id) REFERENCES skins (id) ON DELETE CASCADE
);