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
| `/logout` | POST | Authenticated | — | Form in the navigation; redirects to `/login?logout` |

Navigation differs by audience: anonymous users see *Sign in* / *Register*; authenticated customers additionally see *Shop*, *My orders*, their email, and *Sign out*; admins additionally see *Admin*. Non-`/api` POSTs require a CSRF token (Thymeleaf adds it automatically); `/api/**` remains CSRF-exempt.

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