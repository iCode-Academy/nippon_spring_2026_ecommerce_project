create table orders (
    id                bigserial primary key,
    user_id           bigint        not null references users(id),
    status            varchar(20)   not null
        check (status in ('PENDING','CONFIRMED','SHIPPED','DELIVERED','CANCELLED')),
    subtotal          numeric(12,2) not null check (subtotal >= 0),
    total             numeric(12,2) not null check (total >= 0),
    ship_title        varchar(255),
    ship_city         varchar(255)  not null,
    ship_district     varchar(255)  not null,
    ship_address_line varchar(255)  not null,
    ship_phone        varchar(255)  not null,
    created_at        timestamp with time zone not null,
    updated_at        timestamp with time zone not null
);

CREATE TABLE order_items (
    id            bigserial primary key,
    order_id      bigint        not null references orders(id) on delete cascade,
    product_id    bigint        not null,
    product_name  varchar(255)  not null,
    unit_price    numeric(12,2) not null check (unit_price >= 0),
    line_total    numeric(12,2) not null check (line_total >= 0),
    quantity      INT           not null check (quantity > 0)
);

create index idx_orders_user_created on orders(user_id, created_at desc);
create index  idx_order_items_order   on order_items(order_id);