CREATE TABLE IF NOT EXISTS skins (
    id BIGSERIAL PRIMARY KEY,
    market_hash_name VARCHAR(255) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS skin_price_history (
    id BIGSERIAL PRIMARY KEY,
    skin_id BIGINT NOT NULL,
    min_price DECIMAL(10, 2),
    suggested_price DECIMAL(10, 2),
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_skin_price_history_skin FOREIGN KEY (skin_id) REFERENCES skins (id) ON DELETE CASCADE
);