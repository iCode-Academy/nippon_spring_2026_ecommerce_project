# nippon_spring_2026_ecommerce_project

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