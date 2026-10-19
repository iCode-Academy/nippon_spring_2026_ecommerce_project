# nippon_spring_2026_ecommerce_project

## Web UI Routes (Thymeleaf)

The server-rendered UI uses a shared Thymeleaf layout (`templates/fragments/layout.html`) with role-aware navigation, flash messages, and a responsive stylesheet (`static/css/app.css`) that keeps keyboard focus visible and adapts at a mobile breakpoint (max-width 40rem).

| Route | Method | Access | View | Notes |
|---|---|---|---|---|
| `/` | GET | Public | `index` | Landing page; hero actions differ for anonymous vs signed-in users |
| `/login` | GET | Public | `login` | Shows failure message when `?error` is present, success message when `?logout` is present |
| `/login` | POST | Public | — | Spring Security form login; success → `/home`, failure → `/login?error` |
| `/register` | GET | Public | `register` | Registration form |
| `/register` | POST | Public | `register` / redirect | Validates input; on success flashes a message and redirects to `/login`; on duplicate email shows a form-level error; on unexpected failure shows an error flash |
| `/home` | GET | Authenticated | `home` | Product cards; empty state when no active products |
| `/orders` | GET | Authenticated | `orders` | Current customer's orders table; empty state when none |
| `/admin` | GET | `ADMIN` only | `admin/dashboard` | Stats cards and users table; empty state when no users |
| `/admin/categories` | GET | `ADMIN` only | `admin/categories` | JavaScript-driven category management page; renders the empty state when no categories exist |
| `/logout` | POST | Authenticated | — | Form in the navigation; redirects to `/login?logout` |

Navigation differs by audience: anonymous users see *Sign in* / *Register*; authenticated customers additionally see *Shop*, *My orders*, their email, and *Sign out*; admins additionally see *Admin* and *Categories*. Non-`/api` POSTs require a CSRF token (Thymeleaf adds it automatically to `th:action` forms such as login, register, and sign out); `/api/auth/**` is CSRF-exempt; `/api/admin/**` requires both the `ADMIN` role and a valid `X-CSRF-TOKEN` header.

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

Error pages for 403, 404, and 500 live in `templates/error/` and use the same layout. Representative mobile and desktop screenshots are attached to the pull request for UI changes (see CONTRIBUTING.md).

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