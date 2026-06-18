# Legacy Payment Processing Service

> **WARNING**: This is a legacy monolithic application. It is currently in maintenance mode.
> We are planning a migration to microservices (see JIRA epic PAY-4521).

## Overview

This service handles all payment processing for BigFake Company, including:
- Credit card, debit, wire transfer, and ACH payments
- Refund processing
- Merchant management
- Webhook notifications to downstream systems

## Tech Stack

- **Java 17**
- **Spring Boot 4.1.x**
- **Hibernate / Spring Data JPA**
- **H2** (dev) / **PostgreSQL** (prod)
- **Flyway** for database migrations
- **Maven** build system

## Known Tech Debt

- [ ] PaymentServiceImpl is too large and has complex branching logic (PAY-3201)
- [ ] No circuit breaker for external payment gateway calls (PAY-4010)
- [ ] Currency conversion uses hardcoded rates (PAY-2876)
- [ ] Missing integration tests for refund flow (PAY-3455)
- [ ] Notification service is synchronous - should use message queue (PAY-4102)

## Building

```bash
mvn clean install
```

## Running Locally

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=default
```

The service starts on port 8080. Swagger UI available at `/swagger-ui.html`.

## API Authentication

All endpoints require Basic Auth. Default dev credentials:
- Username: `admin`
- Password: `admin123`

## Database

- Dev: H2 in-memory (console at `/h2-console`)
- Prod: PostgreSQL (see `application-prod.yml`)

## Deployment

Docker image built via:
```bash
docker build -t legacy-payments .
docker run -p 8080:8080 legacy-payments
```

## Contact

- Team: Payments Platform (payments-platform@bigfake.com)
- On-call: PagerDuty rotation "payments-legacy"
