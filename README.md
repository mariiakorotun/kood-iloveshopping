# I Love Shopping — Commerce API

A Spring Boot 3 commerce backend for catalog search, temporary guest carts, persistent customer carts, checkout, asynchronous simulated payment processing, stock-safe order management, and cancellations/refunds.

## Run it

Docker is the only application prerequisite. Set a non-default `DATA_ENCRYPTION_KEY` for any non-development environment, then start the complete stack:

```bash
docker compose up --build
```

The storefront is at `http://localhost:3000`, the API is at `http://localhost:8080`, RabbitMQ management is at `http://localhost:15672` (guest/guest), and Mailpit's email inbox is at `http://localhost:8025`. Stop the stack with `docker compose down`. Add `-v` only when intentionally deleting local database data.

For local development, provide PostgreSQL and RabbitMQ, then run `./mvnw test` or `./mvnw spring-boot:run`.

## Model

```text
User (1) ── (0..1) Cart ── (*) CartItem ── (1) Product ── (*) ProductImage
Guest session ID (1) ── (0..1) Cart
User (1) ── (*) Order ── (*) OrderItem ── snapshot of Product
```

`Cart.user_id` gives authenticated shoppers one persistent cart. `Cart.guest_session_id` gives anonymous shoppers a temporary cart; send it in `X-Guest-Session-Id` and merge it after sign-in. A cart item references the live product for its thumbnail, price, and availability, while an order item preserves the purchase-time name, price, and quantity.

## Main API flow

1. Browse `GET /api/v1/products/search`.
2. For a guest, create a random client-side session ID and send it as `X-Guest-Session-Id`; call `POST /api/v1/cart/items` with `{ "productId": "UUID", "quantity": 1 }`. `GET`, `PUT /items/{id}`, and `DELETE /items/{id}` return recalculated subtotals and cart total.
3. Submit `POST /api/v1/orders/checkout` with the session header (or a Bearer token for a signed-in customer):

```json
{
  "email": "buyer@example.test",
  "firstName": "Ada",
  "lastName": "Lovelace",
  "address": "1 Main Street",
  "city": "Kyiv",
  "zipCode": "01001",
  "shippingOptionId": "standard",
  "paymentMethodToken": "tok_sandbox_success"
}
```

4. The order starts as `PENDING_PAYMENT`, inventory is locked/decremented atomically, and an order-created event is published to RabbitMQ. The payment consumer publishes the result; the order consumer changes it to `PAYMENT_SUCCESSFUL` or `PAYMENT_FAILED` and assigns tracking on success.
5. Signed-in customers can use `GET /api/v1/orders?status=&startDate=YYYY-MM-DD&endDate=YYYY-MM-DD` and `POST /api/v1/orders/{id}/cancel`. Cancellation restores inventory; a paid cancellation is marked for refund workflow.

Sandbox failure tokens are `tok_insufficient_funds`, `tok_invalid_card`, `tok_expired_card`, and `tok_gateway_timeout`. They intentionally exercise a failed payment without accepting raw card data. A browser client must use Stripe/PayPal hosted elements to tokenize card details, validate number/expiry/CVV before tokenization, and send only the returned token to this API.

## Web storefront

The React/Vite client in `frontend/` makes the complete customer flow available in the browser: product search, guest cart, registration/sign-in, guest-cart merging, persistent cart updates, single-page checkout, payment-result simulation, order filtering, and cancellation/refund requests. It is built and served by Nginx in the `web-app` Docker service; Nginx proxies `/api` to the backend so no browser CORS configuration is required.

## Security and reliability

- HTTPS/TLS terminates at the deployment edge; never transmit payment details without it.
- Card PAN, CVV, and expiry are never persisted or accepted by this API, keeping payment entry within provider-hosted PCI scope.
- Shipping addresses are encrypted at rest with AES-GCM through a JPA converter. Set `DATA_ENCRYPTION_KEY` as a managed secret and rotate it via a planned migration.
- Pessimistic product locks prevent concurrent successful checkouts from overselling stock.
- Bean Validation returns clear 400 responses for required or invalid checkout fields; stock and state conflicts return 409 with an actionable error.

## Tests

`./mvnw test` runs cart total unit coverage and critical validation/registration flow tests. Tests use an isolated H2 database and do not require Docker services.
