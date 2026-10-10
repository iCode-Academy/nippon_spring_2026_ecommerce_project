CREATE TABLE cart (
    id	BIGSERIAL PRIMARY KEY,
    user_id	BIGINT NOT NULL,
    created_at	TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT	uq_cart_user UNIQUE (user_id),
    CONSTRAINT	fk_cart_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE CASCADE
);

CREATE INDEX idx_cart_user_id ON cart (user_id);

CREATE TABLE cart_items (
    id	BIGSERIAL PRIMARY KEY,
    cart_id	BIGINT NOT NULL,
    product_id	BIGINT NOT NULL,
    quantity	INTEGER NOT NULL DEFAULT 1,
    CONSTRAINT fk_cart_item_cart
        FOREIGN KEY (cart_id) REFERENCES cart (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_cart_item_product
        FOREIGN KEY (product_id) REFERENCES product (id)
        ON DELETE CASCADE,
    CONSTRAINT uq_cart_product UNIQUE (cart_id, product_id),
    CONSTRAINT ck_cart_item_quantity_positive CHECK (quantity > 0)
);

CREATE INDEX idx_cart_item_cart_id ON cart_items (cart_id);
CREATE INDEX idx_cart_item_product_id ON cart_items (product_id);