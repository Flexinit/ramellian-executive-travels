# Ramellian Executive Travels

*Specification-Driven Agile Delivery Framework with TDD*

Prepared: 2026-09-19 18:12 UTC | Scope: backend first

Assumption: PEC means a specification-driven development framework. Every increment begins with an agreed specification, produces one usable backend capability, and is accepted through automated tests and a working demonstration.

## **Delivery promise**

![][image1]

# **1\. Product and technical baseline**

* Backend: Java, Spring Framework with virtual threads enabled, JDBC, PostgreSQL, Flyway, JWT authentication, and REST/JSON.  
* Architecture: modular monolith first, with clear module ownership and future extraction points.  
* Delivery unit: one vertical slice that crosses controller, application service, domain rule, JDBC repository, migration, and tests.  
* Frontend is intentionally deferred until the backend API and acceptance behavior are stable.

## **Vertical slice**

![][image2]

# **2\. Specification unit**

A specification is small enough to finish in one sprint and precise enough to test without guessing. Each specification must include:

* User outcome: who needs what and why.  
* Scope: included behavior and explicitly excluded behavior.  
* API contract: endpoint, request, response, status codes, validation, and authorization.  
* Data impact: tables, fields, relationships, migration, and ownership.  
* Business rules: states, permissions, errors, and side effects.  
* Acceptance examples: successful, invalid, unauthorized, duplicate, and failure cases.  
* Operational notes: logs, audit event, metrics, timeout, retry, and security considerations.

## **Specification approval**

![][image3]

# **3\. TDD working agreement**

## **One feature TDD loop**

![][image4]

* Start with an API or application-level acceptance test that expresses the user outcome.  
* Add unit tests for domain decisions before implementation: state transitions, authorization, pricing, and eligibility.  
* Add JDBC integration tests for SQL, migrations, constraints, and transaction behavior.  
* Use virtual threads for concurrency management in tests and application logic.  
* Use standard Spring testing tools for endpoint behavior, JWT claims, status codes, validation, and error responses.  

# **4\. Agile delivery increments**

Deliver the increments in order. Each increment is independently demonstrable and leaves the system in a usable state.

## **Increment 0: Project foundation**

Deliverable: the application starts, connects safely to PostgreSQL, and has a repeatable test pipeline.

* Create Spring project structure with module packages.  
* Add Flyway migrations, JDBC connection pool, configuration profiles, health endpoint, logging, and correlation ID.  
* Add unit-test, integration-test, and standard Spring test conventions.  
* Acceptance: clean checkout builds, migrations run on an empty database, and the health test passes.

## **Increment 1: JWT user management**

Deliverable: users can authenticate and receive authorized access to the API.

* Implement AuthController, UserController, JWT filter, password hashing, roles, organizations, and refresh-token rotation.  
* Add users, organizations, organization_members, roles, refresh_tokens, and token_revocations migrations.  
* Acceptance: valid login returns tokens; invalid credentials fail; expired or invalid tokens are rejected; role-protected endpoints enforce permissions; logout revokes refresh access.

### **User Roles**

The system includes a `UserRole` entity to manage roles such as "Admin," "Renter," and "Chauffeur." Each user is assigned a role, enabling role-based access control. The `UserRole` entity includes:

* `roleName`: The name of the role (e.g., "Admin").  
* `description`: A brief description of the role's purpose.  

This ensures that permissions and access levels are clearly defined and enforced.

## **Increment 2: Owner, chauffeur, and vehicle records**

Deliverable: approved users can create the people and vehicles needed to offer transport.

* Implement KycController, VehicleController, and ChauffeurController with role checks.  
* Add organizations, vehicles, chauffeurs, vehicle_documents, chauffeur_documents, and review records.  
* Acceptance: an owner can submit records; an administrator can approve or reject them; unpublished or unapproved vehicles cannot enter availability search.

## **Increment 3: Availability and quote**

Deliverable: a customer can search eligible vehicles and receive a reliable quote.

* Implement AvailabilityController and PricingController.  
* Add service areas, rate cards, availability blocks, vehicle holds, and price quotes.  
* Acceptance: search respects location, time, service mode, and approval status; overlapping confirmed reservations are rejected; quote totals are reproducible.

---

# **Spike: Conversational Booking & Reporting via Chatbot**

*Added: 2026-10-05 | Status: Spike — to explore, not yet committed to a delivery increment*

## Overview

Explore delivering the entire renter user flow (search, quote, book, pay, view receipt) and admin reporting through a conversational chatbot interface, in addition to the existing REST API. Two channels are in scope: a **WhatsApp chatbot** and a **Ramellian custom in-app bot**.

## Motivation

* Executive travel customers expect low-friction, conversational interactions — especially on WhatsApp which is dominant in the East Africa market.
* A custom in-app bot surfaces booking, payment, and report data without requiring users to navigate a full UI.
* Both channels reuse the existing backend service layer — no business logic duplication.

## Proposed Architecture

The chatbot layer sits on top of the existing service boundaries. An LLM with function/tool calling acts as the orchestration brain; it receives a user message, decides which backend tool to invoke, executes it, and returns a natural-language response.

```
WhatsApp / Custom UI
        ↓
  ChatController (Spring Boot)
        ↓
  LLM Client (Groq + Llama 3.1 — free tier)
  with tool definitions mapped to:
        ├── VehicleService.search()
        ├── BookingService.createQuote()
        ├── BookingService.confirm()
        ├── PesapalPaymentService.checkout()   → returns payment link
        ├── PaymentService.getStatus()
        └── ReportService (revenue, bookings)  → admin-only tools
        ↓
  Reply sent back to channel
```

**Conversation state** (which step of the flow a user is on) is stored per phone number / session in Redis or a `chat_session` table, since neither WhatsApp nor a stateless REST endpoint carries context natively.

## Channel 1: WhatsApp Bot

### Approach

**Start with Baileys (free, unofficial)** running as a lightweight Node.js microservice alongside the Spring Boot backend. Spring Boot communicates with it over a simple internal REST API.

```
WhatsApp ←→ Baileys (Node.js) ←→ Spring Boot ChatController
```

**Migration path:** when the product scales or reliability guarantees are needed, switch to the **Meta Cloud API** (official). The first 1,000 user-initiated conversations per month are free; beyond that the cost is fractions of a cent per conversation.

### Payment

The bot drops the Pesapal checkout URL as a plain clickable link. No special handling needed:

```
Bot: "Complete your payment here 👇
     https://pay.pesapal.com/iframe/...?OrderTrackingId=xxx"
```

Once the user pays, the existing Pesapal IPN webhook fires and the backend confirms the booking automatically.

### Receipt Delivery

On payment confirmation, the generated PDF receipt is sent **directly to WhatsApp as a document** — not just a summary text. Baileys supports sending files (buffers/streams); the backend reads the receipt PDF from disk and pushes it via Baileys:

```
Bot: [sends PDF: Receipt-ToyotaLandCruiserV8.pdf]
     "Payment confirmed ✓ Your receipt is attached. Enjoy your ride!"
```

### Vehicle Search — Images & Details

When the bot returns available vehicles, each vehicle is sent as a **WhatsApp image message** with a caption containing the key details (model, price, features). Baileys supports sending images from URL or buffer:

```
Bot: [image: toyota_land_cruiser.jpg]
     "1. Toyota Land Cruiser V8
         Rate: KES 12,000/day
         Mode: Chauffeur-driven / Self-drive
         Seats: 7 | AC | WiFi"

    [image: mercedes_gle.jpg]
     "2. Mercedes GLE 400
         Rate: KES 15,000/day
         Mode: Chauffeur-driven
         Seats: 5 | AC | Leather"

    "Reply 1 or 2 to select."
```

### Contract Signing

After the user selects a vehicle and confirms the quote, the bot sends a **contract signing link** — a URL to the frontend contract page where the user signs digitally. The existing `POST /api/v1/payments/contracts/{contractId}/sign` endpoint handles the signature upload.

```
Bot: "Before we finalise, please review and sign your rental agreement:
     https://app.ramellian.com/contracts/{contractId}/sign
     Reply DONE once signed."
```

The backend detects the contract signature via the existing flow; the bot can also poll `GET /api/v1/payments/contracts/{contractId}` to confirm signature status before proceeding to payment.

### Example Full Flow

```
User:  "I need a chauffeur-driven SUV from Nairobi CBD to JKIA tomorrow at 7 AM"
Bot:   [image: land_cruiser.jpg] "1. Toyota Land Cruiser V8 — KES 12,000/day ..."
       [image: mercedes_gle.jpg] "2. Mercedes GLE 400 — KES 15,000/day ..."
       "Reply 1 or 2 to select."
User:  "1"
Bot:   "Quote: Toyota Land Cruiser V8 | 6 Oct 7:00 AM → 7 Oct 7:00 AM
        Rental: KES 12,000 | Fee: KES 180 | Total: KES 12,180
        Reply YES to confirm."
User:  "YES"
Bot:   "Please sign your rental agreement:
        https://app.ramellian.com/contracts/abc123/sign
        Reply DONE when signed."
User:  "DONE"
Bot:   "Contract signed ✓ Complete payment here:
        https://pay.pesapal.com/iframe/...?OrderTrackingId=xxx"
        ... user pays in browser ...
Bot:   [sends PDF: Receipt-ToyotaLandCruiserV8.pdf]
       "Payment confirmed ✓ Receipt attached. Have a great trip!"
```

## Channel 2: Ramellian Custom In-App Bot

A chat widget embedded in the Ramellian web/mobile frontend. Uses the same `ChatController` and LLM pipeline as WhatsApp — only the transport layer differs (WebSocket or REST polling instead of WhatsApp messages).

**Additional capabilities over WhatsApp** (because it's inside an authenticated session):
* Inline payment — no redirect needed if a browser payment modal is used.
* File downloads — bot can surface receipt PDF links directly.
* Admin/owner reporting — role-based tool exposure from the JWT claims:
  * `get_revenue_report(dateRange)` — total earnings, platform fees, payouts.
  * `list_pending_bookings()` — bookings awaiting confirmation.
  * `get_payment_status(ref)` — look up a specific Pesapal transaction.
  * `list_vehicles(status)` — fleet overview with approval status.

## LLM Choice

**Groq + Llama 3.1 (free tier)** — 14,400 free requests/day, fastest inference available (LPU chips), solid tool/function-calling support. **Google Gemini Flash** is a viable fallback (also free tier, comparable tool-calling quality).

No OpenAI/Anthropic dependency — zero LLM cost until scale demands it.

## Key Questions for Spike

1. Does Baileys account stability hold for a business WhatsApp number under moderate load?
2. What is the acceptable latency for a Groq LLM round-trip inside a WhatsApp conversation?
3. How is conversation state modelled — Redis TTL sessions vs. a `chat_session` DB table?
4. How are LLM tool definitions versioned as the backend API evolves?
5. At what conversation volume does Baileys → Meta Cloud API migration become necessary?
6. How does the custom bot authenticate — does it reuse the existing JWT flow or introduce a WebSocket session token?

## Out of Scope for Spike

* Full multi-turn booking history / memory across sessions.
* Voice channel.
* Fine-tuning the LLM on Ramellian-specific data.

## Proposed Spike Deliverable

A proof-of-concept (not production) demonstrating:

1. A WhatsApp message received by Baileys → forwarded to `ChatController`.
2. `ChatController` calls Groq with tools: `search_vehicles`, `create_quote`.
3. Vehicle results returned as images with captions via Baileys.
4. Contract signing link sent; bot polls for signature confirmation.
5. Payment link dropped as plain text; Pesapal IPN fires and bot sends PDF receipt to WhatsApp.
6. The same `ChatController` serves the Ramellian custom in-app bot widget.

This validates the full happy-path architecture before committing to a delivery increment.
