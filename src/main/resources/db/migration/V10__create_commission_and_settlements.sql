CREATE TABLE commission_settings (
    id BIGSERIAL PRIMARY KEY,
    scope VARCHAR(30) NOT NULL,
    target_id BIGINT,
    rate NUMERIC(5, 4) NOT NULL,
    description VARCHAR(255),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_commission_scope_target UNIQUE (scope, target_id),
    CONSTRAINT chk_commission_rate CHECK (rate >= 0.0000 AND rate <= 1.0000)
);

CREATE INDEX idx_commission_lookup ON commission_settings(scope, target_id, is_active);

CREATE TABLE seller_order_settlements (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    order_item_id BIGINT NOT NULL,
    seller_id BIGINT NOT NULL,
    item_subtotal NUMERIC(15, 2) NOT NULL,
    commission_rate NUMERIC(5, 4) NOT NULL,
    commission_amount NUMERIC(15, 2) NOT NULL,
    platform_discount_subsidized NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    seller_net_amount NUMERIC(15, 2) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    settled_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_settlement_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
    CONSTRAINT fk_settlement_order_item FOREIGN KEY (order_item_id) REFERENCES order_items(id) ON DELETE CASCADE,
    CONSTRAINT fk_settlement_seller FOREIGN KEY (seller_id) REFERENCES users(id),
    CONSTRAINT chk_settlement_status CHECK (status IN ('PENDING', 'SETTLED', 'CANCELLED'))
);

CREATE INDEX idx_settlements_seller_created ON seller_order_settlements(seller_id, created_at);
CREATE INDEX idx_settlements_seller_status ON seller_order_settlements(seller_id, status);
CREATE INDEX idx_settlements_order_id ON seller_order_settlements(order_id);
