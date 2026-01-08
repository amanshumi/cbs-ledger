# Core Banking Ledger Service

A production-grade double-entry accounting system for fintech loan operations.

## Features
* **Double-Entry bookkeeping:** Enforces Debits = Credits for every transaction.
* **Optimistic Locking:** Handles high concurrency using versioning.
* **Idempotency:** Prevents duplicate transactions (crucial for financial webshooks).
* **Reporting:** Real-time Trial Balance, Account History, and Loan Aging.

## Prerequisites
* Java 21+
* Maven or Gradle
* A database (H2 is configured by default for testing)

## Setup & Running

1. **Clone the repository.**
    ```bash
    git clone https://github.com/amanshumi/cbs-ledger.git
    ```
2. **Build the project:**
   ```bash
   ./mvnw clean install
   ```
3. **Format the code:**
   ```bash
   ./mvnw spotless:apply
   ```
4. **Run the application:**
   ```bash
   ./mvnw spring-boot:run
   ```
   
5. **Access the H2 Console (Optional):**
   * URL: http://localhost:8080/h2-console
   * JDBC URL: jdbc:h2:mem:testdb
   * User: sa
   * Password: password

6. **Explore API endpoints:**
   * Swagger UI: http://localhost:8080/swagger-ui.html 