# nippon_spring_2026_ecommerce_project

## Web UI Routes (Thymeleaf)

The server-rendered UI uses a shared Thymeleaf layout (`templates/fragments/layout.html`) with role-aware navigation, flash messages, and a responsive stylesheet (`static/css/app.css`) that keeps keyboard focus visible and adapts at mobile breakpoints (max-width 60rem for the admin sidebar, 40rem for general layout).

| Route | Method | Access | View | Notes |
|---|---|---|---|---|
| `/` | GET | Public | `index` | Landing page; hero actions differ for anonymous vs signed-in users |
| `/login` | GET | Public | `login` | Shows failure message when `?error` is present, success message when `?logout` is present |
| `/login` | POST | Public | — | Spring Security form login; success → `/home`, failure → `/login?error` |
| `/register` | GET | Public | `register` | Registration form |
| `/register` | POST | Public | `register` / redirect | Validates input; on success flashes a message and redirects to `/login`; on duplicate email shows a form-level error; on unexpected failure shows an error flash |
| `/home` | GET | Authenticated | `home` | Product cards; empty state when no active products |
| `/products` | GET | Authenticated | `products/list` | Browse active products with search, filters, sorting, and pagination (CAP-15) |
| `/products/{id}` | GET | Authenticated | `products/detail` | Product detail with ordered image gallery, stock state, category, price, and published-review summary (CAP-16) |
| `/orders` | GET | Authenticated | `orders` | Current customer's orders table; empty state when none |
| `/admin` | GET | `ADMIN` only | `admin/dashboard` | Stats cards and users table; empty state when no users |
| `/admin/categories` | GET | `ADMIN` only | `admin/categories` | JavaScript-driven category management page; renders the empty state when no categories exist |
| `/admin/products` | GET | `ADMIN` only | `admin/products` | JavaScript-driven product management page: create/edit products, manage images, toggle active state, adjust stock |
| `/logout` | POST | Authenticated | — | Form in the navigation; redirects to `/login?logout` |

Navigation differs by audience: anonymous users see *Sign in* / *Register*; authenticated customers additionally see *Shop*, *Products*, *My orders*, their email, and *Sign out*; admins get a single *Admin* link in the header plus a dedicated sidebar (`templates/fragments/admin-sidebar.html`) with *Dashboard*, *Products*, *Categories*, and *Back to shop*. The active link is highlighted in both navigation areas; on screens at or below 60rem the admin sidebar becomes a horizontally scrollable navigation strip. Non-`/api` POSTs require a CSRF token (Thymeleaf adds it automatically to `th:action` forms such as login, register, and sign out); `/api/auth/**` is CSRF-exempt; `/api/admin/**` requires both the `ADMIN` role and a valid `X-CSRF-TOKEN` header.

The catalog has two distinct entry points: **Shop** (`/home`) is the quick active-product list, while **Products** (`/products`) is the searchable catalog with filters, sorting, result count, active filter chips, and numbered pagination. Both render cards from `templates/fragments/product-card.html` with an image thumbnail, stock badge, price, and *View details* action. The admin product page has a breadcrumb, an *Add product* shortcut, inline stock editing, busy states, and a success flash.

### Admin category API

The category page is a JavaScript client (`static/js/admin-categories.js`). It loads and mutates categories through the JSON API below, reading the CSRF token from the layout's `<meta name="_csrf">` and `<meta name="_csrf_header">` tags and sending it as the `X-CSRF-TOKEN` header on every POST/PUT/DELETE.

| Route | Method | Success | Errors |
|---|---|---|---|
| `/api/admin/categories` | GET | `200`, JSON array `{"id", "name"}` sorted by name | `302` → `/login` (anonymous), `403` (`CUSTOMER`) |
| `/api/admin/categories` | POST | `201`, created `{"id", "name"}` | `400` invalid name, `409` duplicate name, `403` missing CSRF token or wrong role |
| `/api/admin/categories/{id}` | PUT | `200`, updated `{"id", "name"}` | `400` invalid name, `404` unknown id, `409` duplicate name |
| `/api/admin/categories/{id}` | DELETE | `204` no content | `404` unknown id, `409` category still referenced by products |

Errors use the shared `ProblemDetail` format described below. The page surfaces them as toast messages ("Category name already exists", "Category could not be deleted", …).

### Category management rules

- Category names are required, trimmed, and limited to 150 characters; the client validates empty input and the API answers invalid payloads with `400` plus field errors.
- Duplicate names are rejected case-insensitively with `409` ("A category named … already exists") instead of a raw database failure.
- The page and the API require the `ADMIN` role: anonymous users are redirected to `/login`, and `CUSTOMER` users receive `403 Forbidden` on both `/admin/categories` and `/api/admin/categories/**`.
- Mutations must carry the CSRF token: the JavaScript reads it from the layout's meta tags and sends it in the `X-CSRF-TOKEN` header; a request without a valid token is rejected with `403 Forbidden` before it reaches the controller. Login, register, and sign out keep Thymeleaf's automatic token injection.
- Deleting is safe: a category referenced by products is never removed — the API answers `409` with how many products must be reassigned first and the page shows an error toast. Unreferenced categories are deleted (`204`) and the list refreshes.
- Categories are listed alphabetically; the JavaScript client shows an empty state when no categories exist and reloads the table after every mutation.

### Admin product management

The page at `/admin/products` is linked from the admin sidebar. It uses `static/js/admin-products.js` and the protected JSON API below, reading the CSRF token from the layout meta tags and sending it as `X-CSRF-TOKEN`.

| Route | Method | Success | Errors |
|---|---|---|---|
| `/api/admin/products` | GET | `200`, JSON array of products | `403` wrong role |
| `/api/admin/products` | POST | `201`, created product | `400` invalid payload, `403` missing CSRF or wrong role |
| `/api/admin/products/{id}` | GET | `200`, product | `404` unknown id, `403` wrong role |
| `/api/admin/products/{id}` | PUT | `200`, updated product | `400` invalid payload, `404` unknown id |
| `/api/admin/products/{id}/toggle-active` | POST | `204` no content | `404` unknown id, `403` missing CSRF or wrong role |
| `/api/admin/products/{id}/adjust-stock` | POST | `204` no content | `400` missing quantity or stock below zero, `404` unknown id |
| `/api/admin/products/{productId}/images` | GET / POST | `200` / `201`, images | `400` invalid URL, `404` unknown product |
| `/api/admin/products/{productId}/images/order` | PUT | `204` no content | `400` invalid IDs, `404` unknown product or image |
| `/api/admin/products/{productId}/images/{imageId}` | DELETE | `204` no content | `404` unknown product or image |

The product table has no "Updated At" column because products do not store an update timestamp. The active-state and stock buttons use the same CSRF-protected API and refresh the table after each mutation.

Error pages for 403, 404, and 500 live in `templates/error/` and use the same layout. Representative mobile and desktop screenshots are attached to the pull request for UI changes (see CONTRIBUTING.md).

## Public Product REST API (CAP-14)

The public product REST API allows customers and anonymous users to browse active products and retrieve individual product details. Authentication is not required for GET requests.

### Endpoints

| Method | Endpoint | Description | Success | Errors |
|---|---|---|---|---|
| GET | `/api/products` | List active products with pagination | `200 OK` | `400` invalid pagination |
| GET | `/api/products/{id}` | Retrieve an active product by ID | `200 OK` | `404` product not found or inactive |

### Product listing

Example request:

```http
GET /api/products?page=0&size=10
```

Query parameters:

| Parameter | Default | Rules |
|---|---|---|
| `page` | `0` | Zero-based page index; must be 0 or greater |
| `size` | `10` | Number of products per page; must be between 1 and 50 |

Products are sorted by ID in ascending order. Only active products are returned.

The response uses Spring Data's paginated `Page` structure, including the product list in `content` and pagination metadata such as `totalElements`, `totalPages`, `number`, and `size`.

If no active products exist, the API returns `200 OK` with an empty `content` array.

### Product detail

Example request:

```http
GET /api/products/1
```

Example successful response:

```json
{
  "id": 1,
  "name": "Wireless Mouse",
  "description": "Ergonomic wireless mouse",
  "price": 29.99,
  "stockQuantity": 15,
  "active": true,
  "categoryId": 2,
  "categoryName": "Electronics"
}
```

The API returns `404 Not Found` when the product does not exist or is inactive. Inactive products are not exposed through the public API.

### Error handling

- `400 Bad Request`: Invalid pagination parameters, such as a negative page index or a page size outside 1–50.
- `404 Not Found`: Requested product does not exist or is inactive.
- `500 Internal Server Error`: Unexpected server failure.

Errors use the shared `ProblemDetail` format documented in the API Error Handling section below.

### Access rules

Both endpoints are publicly accessible without authentication. Product creation, updates, deletion, and inventory management remain restricted to their existing administrative endpoints.

## Customer Product Detail (CAP-16)

The detail page at `/products/{id}` is linked from each product card in `/home` and `/products`. It requires an authenticated user, like the browse page, and is rendered with `templates/products/detail.html` using the shared responsive layout.

| Element | Source | Empty or failure state |
|---|---|---|
| Product name, description, category, price | Active product record | Missing description shows "No description provided." |
| Image gallery | `ProductImageService.findByProductId`, ordered by `position ASC` | No images shows "No images available"; the first image is the primary image and the rest are thumbnails in order |
| Stock state | `stockQuantity` from the active product | `0` shows "Out of stock" / "Currently unavailable."; positive stock shows "In stock: N" |
| Review summary | Reviews with `moderationStatus = APPROVED` for the product | No published reviews shows "No published reviews yet."; otherwise shows the average rating out of 5 and the published review count |

Only active products can be opened. A missing or inactive product returns `404 Not Found` and renders `error/404` for browser requests; inactive products never render as purchasable products. Pending and rejected reviews are excluded from the published-review summary. The public REST API detail endpoint keeps the `ProductResponse` shape documented above and applies the same active-product rule.

The two-column layout stacks on screens at or below `40rem`. Desktop and mobile screenshots for this page are stored in `docs/ui-evidence/product-detail-desktop.png` and `docs/ui-evidence/product-detail-mobile.png`.


## Customer Cart REST API (CAP-18)

The customer cart REST API allows authenticated customers to retrieve
their own cart, including cart items, current product prices,
availability, line totals, and subtotal.

### Endpoints

| Method | Endpoint | Description | Success | Errors |
|---|---|---|---|---|
| GET | `/api/cart` | Retrieve the authenticated customer's cart | `200 OK` | `302` anonymous user, `403` access denied, `500` unexpected failure |
| POST | `/api/cart/items` | Add a product to the authenticated customer's cart | `200 OK`, updated cart in the GET response format | `400` invalid request, `404` missing product, `409` unavailable/insufficient stock, `403` missing CSRF or forbidden account, `302` anonymous user |

### Add an item (CAP-19)

From the product detail, product list, or shop home page, *Add to cart* sends a JSON request without reloading the page. The detail page accepts a quantity; the list and home buttons add one. The page reads the CSRF token and header name from the layout's `_csrf` and `_csrf_header` meta tags and shows an accessible success or error message. An out-of-stock item has its add button disabled; the server always checks the latest stock and active state again.

Desktop and mobile evidence of the product detail's successful add feedback is in `docs/ui-evidence/cart-add-desktop.png` and `docs/ui-evidence/cart-add-mobile.png`.

```http
POST /api/cart/items
Content-Type: application/json
X-CSRF-TOKEN: <token from the rendered page>

{"productId":10,"quantity":2}
```

Both fields are required and must be positive integers. The response is the updated cart (`cartId`, `items`, and `subtotal`, as shown below). If no cart exists, it is created on the first successful addition. Adding a product already in the cart increases its existing quantity rather than creating another item; the **combined** quantity must not exceed the product's current stock. Concurrent additions for the same customer are serialized. Cart additions do not reserve or decrease stock. The server derives cart ownership from the signed-in customer; the request cannot choose another user's cart.

Invalid or missing fields return `400` with field errors. A nonexistent product returns `404`; an inactive or out-of-stock product, or a quantity exceeding stock, returns `409`. A disabled/deleted customer, an ADMIN, or a request without a valid CSRF token returns `403`. An anonymous request follows the existing sign-in redirect (`302` to `/login`). API errors returned by the controller follow the shared `ProblemDetail` format.

### Retrieve current cart

Example request:

```http
GET /api/cart
```

Example successful response:

```json
{
  "cartId": 1,
  "items": [
    {
      "id": 1,
      "productId": 10,
      "productName": "Notebook",
      "quantity": 2,
      "unitPrice": 12.50,
      "available": true,
      "lineTotal": 25.00
    },
    {
      "id": 2,
      "productId": 11,
      "productName": "Pen",
      "quantity": 3,
      "unitPrice": 3.00,
      "available": true,
      "lineTotal": 9.00
    }
  ],
  "subtotal": 34.00
}
```

### Response fields

| Field | Description |
|---|---|
| `cartId` | ID of the authenticated customer's cart; null if no cart exists |
| `items` | Cart items ordered by ID in ascending order |
| `id` | Cart item ID |
| `productId` | ID of the associated product |
| `productName` | Current product name |
| `quantity` | Quantity stored in the cart item |
| `unitPrice` | Current product price from the database |
| `available` | Whether the product is active and has sufficient stock |
| `lineTotal` | Current unit price multiplied by quantity |
| `subtotal` | Sum of all cart item line totals |

### Pricing and availability rules

- Product prices are retrieved from PostgreSQL.
- All monetary calculations use `BigDecimal`.
- Line totals are calculated as `unitPrice × quantity`.
- The subtotal is calculated as the sum of all line totals.
- Product price changes are reflected in subsequent cart requests.
- A product is available only when it is active and its stock
  quantity is greater than or equal to the cart item quantity.
- Inactive products and products with insufficient stock remain
  visible in the cart with `available: false`.
- Unavailable items are included in the displayed subtotal.
- Reading the cart does not modify product stock or reserve inventory.

### Empty cart

If the authenticated customer does not have a cart, the API returns
`200 OK` with the following response:

```json
{
  "cartId": null,
  "items": [],
  "subtotal": 0
}
```

If an existing cart contains no items, its cart ID is returned with
an empty items array and a subtotal of zero.

A GET request does not automatically create a cart.

### Access rules

- The endpoint requires the `CUSTOMER` role.
- The current customer is resolved from Spring Security authentication.
- Cart ownership is determined on the server.
- The endpoint does not accept user IDs or cart IDs as ownership selectors.
- Supplied `userId` or `cartId` query parameters do not override
  the authenticated customer.
- Customers cannot retrieve another customer's cart.
- Anonymous users are redirected to `/login`.
- Users without the required role receive `403 Forbidden`.

### Error handling

- `302 Found`: Unauthenticated request redirected to `/login`.
- `403 Forbidden`: Insufficient permissions or the authenticated
  customer's account no longer exists.
- `500 Internal Server Error`: Unexpected server failure.

Exceptions handled by the controller use the shared `ProblemDetail`
format documented in the API Error Handling section below.

### Automated tests

The cart service and endpoint are covered by:

- `CartServiceTest`: Unit tests for cart retrieval, pricing,
  availability, subtotal calculation, empty carts, and missing customers.
- `CartApiControllerTest`: PostgreSQL integration tests for
  authentication, authorization, cart ownership, current database
  prices, empty carts, availability, and error responses.

This feature introduces no UI changes.


## API Error Handling

The API uses Spring `ProblemDetail` responses for consistent error handling.

### Validation Error — 400 Bad Request

Returned when request data fails validation.

Example:

{
  "title": "Validation failed",
  "status": 400,
  "detail": "One or more fields are invalid.",
  "errors": {
    "email": "Email must be valid",
    "password": "Password is required"
  }
}

### Forbidden — 403 Forbidden

Returned when the user does not have permission to access a resource.

Example:

{
  "title": "Forbidden",
  "status": 403,
  "detail": "You do not have permission to access this resource."
}

### Resource Not Found — 404 Not Found

Returned when the requested resource does not exist.

Example:

{
  "title": "Resource not found",
  "status": 404,
  "detail": "The requested resource was not found."
}

### Conflict — 409 Conflict

Returned when the request conflicts with existing data, such as registering an email address that is already registered.

Example:

{
  "title": "Conflict",
  "status": 409,
  "detail": "Email is already registered"
}

### Internal Server Error — 500

Returned when an unexpected server error occurs.

Example:

{
  "title": "Internal server error",
  "status": 500,
  "detail": "An unexpected error occurred."
}

Internal exception messages, stack traces, SQL errors, and other sensitive implementation details are not exposed in API responses.
