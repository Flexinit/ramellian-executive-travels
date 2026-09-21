# Ramellian Executive Travels Changelog

This file records functional, security, API, and configuration changes made to
the backend. Every future code change should add an entry under
`Unreleased` before the change is considered complete.

## Unreleased

### Identity, vehicle, and owner documents

- Added `UserDocument` records with pending, approved, and rejected review
  states, file metadata, and optional vehicle association.
- Persisted document review states as the `DocumentReviewStatus` enum using
  readable string values (`PENDING`, `APPROVED`, and `REJECTED`).
- Persisted document categories as the `DocumentType` enum using readable
  string values for identity, business, licence, insurance, and registration
  documents.
- Added authenticated multipart uploads through `POST /api/v1/documents`.
  Supported document types include `NATIONAL_ID`, `PASSPORT`,
  `DRIVING_LICENSE`, `PROOF_OF_ADDRESS`, `INSURANCE_STICKER`,
  `VEHICLE_REGISTRATION`, `BUSINESS_REGISTRATION`, and `TAX_CERTIFICATE`.
- Added owner document listing through `GET /api/v1/documents/me`.
- Added administrator review endpoints for pending documents and approval or
  rejection with notes.
- Added protected document download through
  `GET /api/v1/documents/{id}/content`; only the document owner or a document
  reviewer can retrieve the file.
- Added configurable private document storage through `app.document.directory`
  and restricted document permissions.
- Added `INDIVIDUAL` and `BUSINESS` owner profiles, including business name,
  registration number, tax number, and address fields, through
  `PUT /api/v1/users/me/owner-profile`.
- Business registration and tax documents are accepted only for business owner
  profiles; vehicle insurance and registration documents are scoped to the
  vehicle owner.

### Vehicle media and reviews

- Added local vehicle photo uploads with persisted metadata and static
  `/uploads/vehicles/**` resource serving.
- Added vehicle review listing by vehicle.
- Completed-booking reviews recalculate the vehicle's average rating.

### Booking flow

- Added `BookingStatus` with `PENDING`, `CONFIRMED`, `ACTIVE`, `COMPLETED`,
  `CANCELLED`, and `REJECTED` values, persisted as readable strings.
- Added booking creation with vehicle availability, date validation, overlap
  protection, and estimated rental amount calculation.
- Added renter and owner booking lists through `/api/v1/bookings/mine` and
  `/api/v1/bookings/owner`.
- Added controlled booking status transitions through
  `PUT /api/v1/bookings/{id}/status`.

### Booking reports

- Added renter, owner, and platform booking reports.
- Reports separate upcoming, active, completed, cancelled, and rejected
  bookings using the current time and booking lifecycle.
- Reports include total counts, successful gross booking value, 1% platform
  revenue, owner payout, and the underlying booking lists.
- Added:
  - `GET /api/v1/bookings/reports/mine`
  - `GET /api/v1/bookings/reports/owner`
  - `GET /api/v1/bookings/reports/admin`

### Contract review before payment

- Added renter contract draft generation through
  `POST /api/v1/payments/bookings/{bookingId}/contract/draft`.
- Added explicit renter acceptance through
  `POST /api/v1/payments/bookings/{bookingId}/contract/accept`.
- Payment now requires an existing accepted contract and uses the frozen
  contract content rather than generating a new template after payment.
- Signing now requires a successful payment, while the original reviewed PDF
  remains available for reference.

### Pesapal payments

- Added Pesapal checkout support for M-Pesa and cards through
  `POST /api/v1/payments/pesapal/bookings/{bookingId}/checkout`.
- Added server-side Pesapal OAuth, order submission, payment-status lookup, and
  idempotent IPN processing at `/api/v1/payments/pesapal/ipn`.
- Added provider order references, receipt, and callback event persistence.
- Pesapal payments remain `PENDING` until the server verifies the provider
  status; successful verification confirms the booking and records the 1%
  platform fee and owner payout ledger amounts.
- Added environment-backed Pesapal configuration:
  `PESAPAL_BASE_URL`, `PESAPAL_CONSUMER_KEY`, `PESAPAL_CONSUMER_SECRET`,
  `PESAPAL_CALLBACK_URL`, and `PESAPAL_NOTIFICATION_ID`.

### Frontend integration guide and vehicle discovery

- Added `FRONTEND_INTEGRATION.md` with the complete authentication, vehicle
  discovery, booking, contract, payment, signing, role, error, and deployment
  flow.
- Added renter-focused vehicle search and filtering on `GET /api/v1/vehicles`,
  including text search, location, price, capacity, fuel, transmission, service
  mode, rental-date availability, rating/price sorting, and validation for
  invalid filter combinations.
- Added vehicle ratings to the vehicle response for discovery cards.

### Frontend integration readiness

- Added booking detail retrieval through `GET /api/v1/bookings/{id}` with
  renter, owner, and manager access checks.
- Added payment status polling through
  `GET /api/v1/payments/pesapal/bookings/{bookingId}/status`; the server
  refreshes pending Pesapal payments before returning the status.
- Added explicit `PAYMENT_READ` authorization for renter, owner, admin, and
  super-admin payment status access.
- Added configurable browser CORS support through `CORS_ALLOWED_ORIGINS`,
  defaulting to local frontend ports 3000 and 5173.
- Disabled the client-trusted manual payment confirmation endpoint by default;
  it can only be enabled explicitly with `MANUAL_PAYMENT_CONFIRMATION_ENABLED`
  for controlled non-production testing.
- Added chauffeur assignment for chauffeured bookings through
  `PUT /api/v1/bookings/{id}/chauffeur`; only users with the `CHAUFFEUR` role
  can be assigned.
- Added automatic cancellation of unpaid pending bookings after
  `PENDING_BOOKING_EXPIRATION_MS` (15 minutes by default).
- Booking confirmation is now payment-gated in the production service path;
  owner or manager status updates cannot bypass verified payment.
- Added consistent JSON handling for malformed requests, invalid path/query
  values, validation constraint failures, access denial, and unexpected server
  errors.
- Added owner settlement onboarding fields (`payoutProvider`,
  `payoutAccountReference`, and `payoutStatus`) to the owner profile flow.
  Settlement remains `PENDING_VERIFICATION` until a real payout provider and
  KYC/settlement verification process are configured.
- Added `SELF_DRIVE` and `CHAUFFEURED` booking modes. Self-drive bookings
  require an ID number plus approved identity and driving-license documents;
  chauffeured bookings require approved identity documents.
- Added successful-payment settlement records with a 1% platform fee and
  owner payout calculation.
- Successful booking payment generates a booking contract that can be viewed
  by the renter or owner and signed online by the renter.
- Added self-hosted real PDF contracts using Apache PDFBox. The original
  unsigned PDF and final signed PDF are stored in configurable contract
  storage, with SHA-256 hash, signer, timestamp, IP address, and user-agent
  audit metadata retained for later retrieval.
- Added protected PDF retrieval through
  `GET /api/v1/payments/contracts/{id}/document?signed=true|false`.
- Contract signing now requires a signature image upload and explicit consent;
  the signature is embedded into the final PDF rather than stored only as
  contract text.
- Added versioned contract templates. Admins can create approved platform
  templates; owners can submit vehicle-owner templates for admin approval.
  Paid bookings resolve the latest approved owner template first, then the
  approved platform template, and replace supported booking placeholders.

### Authentication

- Added stateless JWT authentication with Spring Security.
- Added BCrypt password hashing.
- Added `POST /api/v1/auth/register`.
- Added `POST /api/v1/auth/login`.
- Added configurable JWT settings:
  - `JWT_SECRET`
  - `JWT_EXPIRATION_MS`
- Disabled users cannot authenticate.
- Added validation for registration, login, and password requests.
- Added secure current-password verification before password changes.

### Roles

- Added persisted user-to-role relationships.
- Added supported roles:
  - `CHAUFFEUR`
  - `ADMIN`
  - `SUPER_ADMIN`
  - `OWNER`
  - `RENTER`
- New registrations receive the `RENTER` role by default.
- Added role claims to JWTs.
- Added role-based URL authorization.
- Enabled method-level authorization with `@PreAuthorize`.

### Permissions

- Added persisted permissions and role-to-permission relationships.
- Added permission authorities to Spring Security authentication.
- Added permission claims to JWTs.
- Added startup initialization for standard roles and permissions.
- Added permissions for profiles, bookings, payments, vehicles, users, roles,
  and deletion operations.
- Added permission-based URL authorization for protected resources.

### User Management

- Added `GET /api/v1/users/me`.
- Added `PATCH /api/v1/users/me`.
- Added `PUT /api/v1/users/me/password`.
- Added admin user listing through `GET /api/v1/users/admin`.
- Added admin user lookup through `GET /api/v1/users/admin/{id}`.
- Added account enable/disable through
  `PUT /api/v1/users/admin/{id}/enabled`.
- Added role assignment through `PUT /api/v1/users/admin/{id}/role`.
- Added protected user deletion through `DELETE /api/v1/users/admin/{id}`.
- Users cannot delete their own accounts.
- User management, deletion, and role assignment require the appropriate
  authorities.

### DELETE Authorization

- Added `BOOKING_DELETE` for `DELETE /api/v1/bookings/**`.
- Added `VEHICLE_DELETE` for `DELETE /api/v1/vehicles/**`.
- Added `PAYMENT_DELETE` for `DELETE /api/v1/payments/**`.
- Added `USER_DELETE` for user deletion.
- `ADMIN` receives management and deletion permissions.
- `SUPER_ADMIN` receives all configured permissions.

### API Documentation

- Added Springdoc OpenAPI and Swagger UI.
- Added the `bearerAuth` HTTP Bearer JWT security scheme.
- Permitted public access to:
  - `/swagger-ui.html`
  - `/swagger-ui/**`
  - `/v3/api-docs/**`
- Added tests verifying the OpenAPI Bearer authentication definition.
- Swagger UI is available at `/swagger-ui/index.html`.

### Email Verification and Two-Factor Authentication

- Added six-digit, hashed, expiring email-verification codes.
- Registration now requires email verification before issuing JWT access.
- Added `POST /api/v1/auth/verify-email`.
- Added configurable two-factor authentication per user.
- Added `PUT /api/v1/users/me/2fa` to enable or disable 2FA using `EMAIL` or
  `WHATSAPP`.
- Added `POST /api/v1/auth/verify-2fa` for completing login challenges.
- Added email delivery through SMTP.
- Added WhatsApp delivery through the Meta WhatsApp Cloud API.
- Added one-time-use and expiry handling for verification and 2FA codes.
- Added environment-backed settings for SMTP, WhatsApp, and code expiry.
- Added `POST /api/v1/auth/resend-verification` for expired or lost codes.
- Added Swagger authorization persistence between page reloads.

### Development Practices

- Authentication and authorization changes follow test-driven development:
  write a focused failing test, implement the behavior, then run regression
  tests.
- Future changes must update this file with the behavior, endpoints,
  permissions, configuration, and tests affected.

### Refactor Test Compatibility

- Updated authentication tests to target the refactored `AuthService` rather
  than controller implementation details.
- Restored model-level `UserResponse` mapping after user-management service
  extraction.
- Corrected user-management request model usage for account enable/disable and
  role assignment.
- Removed web-layer annotations from service method parameters.

### API Error Responses

- Added a global validation exception handler for `@Valid` request failures.
- Validation errors now return JSON with HTTP status, error type, summary, and
  field-specific messages.
- Added consistent JSON responses for `ResponseStatusException` failures.

### PostgreSQL User Schema

- Renamed the JPA user table mapping from the reserved PostgreSQL table name
  `user` to `app_users`.
- Kept schema management on Hibernate/JPA `ddl-auto=update`; no Flyway
  migrations are used.
- Standardized user column mappings to snake_case.
- Removed global identifier quoting because it incorrectly quoted SQL
  `columnDefinition` expressions such as `boolean default false`.

### Asynchronous Notifications

- Enabled Spring asynchronous execution for email and WhatsApp verification
  notifications.
- Registration, verification-code resend, and 2FA login responses no longer
  block on SMTP or WhatsApp network calls.
- Added a bounded notification executor with asynchronous failure logging.

### SMTP Delivery

- Configured Gmail SMTP on port `587` with authenticated STARTTLS.
- Made SMTP host, username, password, and sender configurable through
  environment variables.
- SMTP credentials must use a Gmail App Password when two-step verification is
  enabled; normal Gmail account passwords are not accepted.
- Prevented blank `From` headers from creating `MailParseException`; when no
  sender is configured, JavaMail uses the authenticated SMTP account.

### Vehicle Management

- Added `VehicleRepository` with available-vehicle and owner queries.
- Added TDD-backed vehicle service and controller CRUD operations.
- Added authenticated owner assignment during vehicle creation.
- Added owner-or-manager authorization for vehicle updates and deletion.
- Added vehicle request and response models with validation.
- Added `VEHICLE_UPDATE` URL authorization for vehicle updates.

### Vehicle Photos, Ratings, and Reviews

- Added local configurable vehicle photo storage using
  `app.vehicle.photo-directory`.
- Added `POST /api/v1/vehicles/{id}/photos` multipart image upload for vehicle
  owners.
- Added `VehiclePhoto` metadata persistence and image-content validation.
- Added booking-backed vehicle reviews through
  `POST /api/v1/reviews/bookings/{bookingId}`.
- Reviews are limited to the renter of a completed booking, with one review per
  booking.
- Vehicle ratings are recalculated from persisted reviews after each review.
- Added `REVIEW_CREATE` and `REVIEW_READ` permissions.
