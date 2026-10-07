-- CAP-04: Establish authoritative catalog storage
-- NOTE: rename this file to match the next free Flyway version number in the repo
-- (check src/main/resources/db/migration for the latest V<N> already merged).

CREATE TABLE category (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(150) NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_category_name UNIQUE (name)
);

CREATE TABLE product (
    id              BIGSERIAL PRIMARY KEY,
    category_id     BIGINT NOT NULL,
    name            VARCHAR(200) NOT NULL,
    description     VARCHAR(5000),
    price           NUMERIC(12, 2) NOT NULL,
    stock_quantity  INTEGER NOT NULL DEFAULT 0,
    active          BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT fk_product_category
        FOREIGN KEY (category_id) REFERENCES category (id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_product_price_non_negative CHECK (price >= 0),
    CONSTRAINT ck_product_stock_non_negative CHECK (stock_quantity >= 0)
);

CREATE INDEX idx_product_category_id ON product (category_id);
CREATE INDEX idx_product_active ON product (active);

CREATE TABLE product_image (
    id          BIGSERIAL PRIMARY KEY,
    product_id  BIGINT NOT NULL,
    image_url   VARCHAR(500) NOT NULL,
    position    INTEGER NOT NULL,
    CONSTRAINT fk_product_image_product
        FOREIGN KEY (product_id) REFERENCES product (id)
        ON DELETE CASCADE,
    CONSTRAINT ck_product_image_position_non_negative CHECK (position >= 0),
    CONSTRAINT uq_product_image_product_position UNIQUE (product_id, position)
);

CREATE INDEX idx_product_image_product_id ON product_image (product_id);