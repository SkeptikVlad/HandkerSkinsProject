CREATE TABLE skin_prices (
        id BIGSERIAL PRIMARY KEY,
        market_hash_name VARCHAR(255) NOT NULL,
        lowest_price VARCHAR(50),
        volume VARCHAR(50),
        fetched_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
);