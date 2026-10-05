CREATE TABLE products (
    id BIGSERIAL PRIMARY KEY,

    seller_id BIGINT NOT NULL,

    name VARCHAR(255) NOT NULL,

    description TEXT,

    price NUMERIC(19, 2) NOT NULL,

    stock_quantity INTEGER NOT NULL DEFAULT 0,

    is_active BOOLEAN NOT NULL DEFAULT TRUE,

    created_at TIMESTAMP WITH TIME ZONE NOT NULL
        DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
        DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_products_seller
        FOREIGN KEY (seller_id)
        REFERENCES users(id)
        ON DELETE RESTRICT,

    CONSTRAINT chk_products_price
        CHECK (price >= 0),

    CONSTRAINT chk_products_stock_quantity
        CHECK (stock_quantity >= 0)
);

CREATE INDEX idx_products_seller_id
    ON products(seller_id);

CREATE INDEX idx_products_active
    ON products(is_active);