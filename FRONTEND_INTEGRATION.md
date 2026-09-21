# Ramellian Executive Travels - Frontend Integration Guide

This backend is ready for frontend integration. All protected endpoints use a
stateless JWT bearer token.

## 1. Authentication

1. Register with `POST /api/v1/auth/register`.
2. Verify the email with `POST /api/v1/auth/verify-email`.
3. Login with `POST /api/v1/auth/login`.
4. Send the returned token on every protected request:

```http
Authorization: Bearer <accessToken>
```

If two-factor authentication is enabled, complete
`POST /api/v1/auth/verify-2fa` before treating login as complete.

## 2. Renter vehicle search

Use `GET /api/v1/vehicles`. It returns only vehicles currently marked
available. The endpoint supports renter-friendly filtering:

| Parameter | Meaning |
|---|---|
| `query` | Searches model, location, color, fuel type, and transmission |
| `location` | Partial location match |
| `minPrice`, `maxPrice` | Daily price range |
| `capacity` | Minimum passenger capacity |
| `fuelType` | Exact fuel type |
| `transmissionType` | Exact transmission |
| `selfDrive` | `true` or `false` |
| `chauffeured` | `true` or `false` |
| `startTime`, `endTime` | Optional ISO-8601 rental interval; excludes overlapping bookings |
| `sort` | `recommended`, `price_asc`, `price_desc`, `rating_desc`, or `capacity_desc` |

Example:

```text
GET /api/v1/vehicles?location=Nairobi&minPrice=4000&maxPrice=12000&capacity=4&selfDrive=true&startTime=2026-10-01T09:00:00%2B03:00&endTime=2026-10-04T09:00:00%2B03:00&sort=recommended
```

Recommended UI behavior: debounce free-text search, keep filters in the URL,
show daily price and rating on cards, and use `startTime`/`endTime` before
showing a vehicle as bookable. Vehicle photos are loaded from the vehicle photo
endpoints after selecting a vehicle.

## 3. Booking and document flow

1. Upload identity documents with `POST /api/v1/documents`.
2. Wait for document approval. Self-drive requires an approved identity
   document, ID number, and driving licence. Chauffeured bookings require an
   approved identity document and ID number.
3. Create a booking with
   `POST /api/v1/bookings/create`, passing `vehicleId`, `startTime`, `endTime`,
   `bookingMode` (`SELF_DRIVE` or `CHAUFFEURED`), optional `tripType` (for
   analytics), and optional `renterNotes` (instructions or special requests to
   the owner, max 1000 characters).
4. Show the returned pending booking with `numberOfDays`, `tripType`, and
   `renterNotes`.
5. Create the frozen contract with
   `POST /api/v1/payments/bookings/{bookingId}/contract/draft`.
6. Display `contractText` and allow the renter to accept it with
   `POST /api/v1/payments/bookings/{bookingId}/contract/accept` and
   `{ "accepted": true }`.

**Renter Notes:** Use the `renterNotes` field to communicate pickup
instructions, special requirements, or trip details to the vehicle owner.
Owners see these notes when viewing booking details.

**Trip Type Tracking:** The `tripType` field records the actual purpose of each
booking for analytics. If not specified, it defaults to null. This data helps
owners and platform managers identify popular trip types (airport transfers,
weddings, corporate events, safaris, family journeys, self-drive rentals, cake
transportation) for better planning and marketing decisions.

## 4. Payment and signing flow

1. Start Pesapal checkout with
   `POST /api/v1/payments/pesapal/bookings/{bookingId}/checkout`.
2. Redirect the browser to the returned `redirectUrl`.
3. After the provider return, poll
   `GET /api/v1/payments/pesapal/bookings/{bookingId}/status`.
4. Treat `SUCCESSFUL` as paid and `PENDING` as still processing. Do not mark
   payment successful in the browser.
5. Retrieve the contract with
   `GET /api/v1/payments/contracts/{contractId}`.
6. Submit a signature image and consent to
   `PUT /api/v1/payments/contracts/{contractId}/sign`.
7. Download the unsigned or signed PDF with
   `GET /api/v1/payments/contracts/{contractId}/document?signed=false|true`.

Pesapal calls `GET` or `POST /api/v1/payments/pesapal/ipn` publicly. The backend
verifies the transaction server-to-server and processes callbacks idempotently.

## 5. Booking screens and operational roles

- Renter bookings: `GET /api/v1/bookings/mine`
- Booking detail: `GET /api/v1/bookings/{id}`
- Owner bookings: `GET /api/v1/bookings/owner`
- Renter report: `GET /api/v1/bookings/reports/mine`
- Owner report: `GET /api/v1/bookings/reports/owner`
- Admin report: `GET /api/v1/bookings/reports/admin`
- Owner/manager chauffeur assignment:
  `PUT /api/v1/bookings/{id}/chauffeur`
- Contract templates: `/api/v1/contract-templates/**`

Pending unpaid bookings expire automatically after 15 minutes by default.

## 6. Common UI error handling

Errors are JSON with `status`, `error`, `message`, and `fieldErrors`. Use
`401` to redirect to login, `403` for a permission screen, `409` for a
workflow conflict, `422` for missing approved documents, and `503`/`500` for
retryable server or provider failures.

Configure browser origins with `CORS_ALLOWED_ORIGINS`, for example:

```text
CORS_ALLOWED_ORIGINS=https://app.example.com
```

## 7. Production configuration checklist

Set a strong `JWT_SECRET`, PostgreSQL credentials, private document and
contract storage, SMTP/WhatsApp settings if used, and all Pesapal variables:
`PESAPAL_CONSUMER_KEY`, `PESAPAL_CONSUMER_SECRET`, `PESAPAL_CALLBACK_URL`, and
`PESAPAL_NOTIFICATION_ID`. The callback URL must be public HTTPS.

Owner payout amounts are currently recorded as a ledger calculation. Actual
marketplace settlement and refunds require the corresponding Pesapal merchant
capabilities and provider API approval.
