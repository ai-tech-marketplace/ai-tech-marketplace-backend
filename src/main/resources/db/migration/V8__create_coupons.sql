CREATE TABLE coupons (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL,
    description TEXT,
    discount_type VARCHAR(30) NOT NULL,
    discount_value NUMERIC(15,2) NOT NULL,
    max_discount_amount NUMERIC(15,2),
    min_order_value NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    usage_limit INTEGER NOT NULL,
    used_count INTEGER NOT NULL DEFAULT 0,
    usage_limit_per_user INTEGER NOT NULL DEFAULT 1,
    is_public BOOLEAN NOT NULL DEFAULT TRUE,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    start_date TIMESTAMP WITH TIME ZONE NOT NULL,
    end_date TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE coupon_usages (
    id BIGSERIAL PRIMARY KEY,
    coupon_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    discount_amount NUMERIC(15,2) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'USED',
    used_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    released_at TIMESTAMP WITH TIME ZONE,

    CONSTRAINT fk_coupon_usages_coupon
        FOREIGN KEY (coupon_id)
        REFERENCES coupons(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_coupon_usages_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_coupons_code ON coupons(code);
CREATE INDEX idx_coupons_discovery ON coupons(is_public, status, start_date, end_date);
CREATE INDEX idx_coupon_usages_coupon_user ON coupon_usages(coupon_id, user_id, status);
CREATE INDEX idx_coupon_usages_order_id ON coupon_usages(order_id);
CREATE UNIQUE INDEX uk_coupon_usages_order ON coupon_usages(order_id);
