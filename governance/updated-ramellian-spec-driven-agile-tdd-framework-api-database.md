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
