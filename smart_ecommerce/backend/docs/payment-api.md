# Payment API — current source contract

Updated 2026-11-03 from PaymentService, OrderService and ShippingService. HTTP statuses below describe the source contract and expected test results, not a claim that these requests have passed against a running database. Runtime checks remain blocked until an approved test/demo target and credentials are confirmed. Do not run migrations, seed data or create accounts as part of this document.

New online payment requests record an attempt; retries with the same idempotency key reuse that attempt. COD is recorded as part of order creation, not by creating a gateway attempt. The amount is copied from `Order.totalAmount`; clients cannot submit or change the amount. `Payment.status` tracks an attempt, while `Order.paymentStatus` remains the order-level summary (`UNPAID` or `PAID`).

## Endpoints

| Method | Endpoint | Auth | Request | Response | Purpose |
|---|---|---|---|---|---|
| POST | `/api/payments` | CUSTOMER bearer JWT, `Idempotency-Key` required | `{ "orderId": "<uuid>", "paymentMethod": "VNPAY" }` | `201 PaymentResponse` | Create an online attempt for the order's selected method. Repeating the same key and payload returns the same attempt. A new COD gateway request is rejected; read the record created with the order instead. |
| GET | `/api/payments/{paymentId}` | CUSTOMER owner or ADMIN | — | `200 PaymentResponse` | Read one payment attempt; a customer sees only their own. |
| GET | `/api/payments/order/{orderId}` | CUSTOMER owner or ADMIN | — | `200 PaymentResponse[]` | List attempts for an order. A foreign customer's order is returned as 404. |
| POST | `/api/payments/callback/{provider}` | Provider callback, no customer JWT | HMAC-signed callback body and `X-Payment-Signature` header | `200 PaymentResponse` | Verify a sandbox callback and update the attempt and order atomically. The implemented provider code is `MOCK`. |

Create request fields:

```json
{
  "orderId": "<order UUID>",
  "paymentMethod": "VNPAY"
}
```

For new online attempts, `paymentMethod` must match the order's selected `BANK_TRANSFER`, `VNPAY`, or `MOMO` method. Orders also accept `COD`, but a new COD gateway attempt through this endpoint is rejected with 409. Send a unique `Idempotency-Key` header (1–100 characters) for each new online attempt; retries reuse the same key. The response includes the server-calculated amount and currency. Payment idempotency is not a claim that POST `/api/orders` has the same retry contract.

Callback body:

```json
{
  "paymentId": "<payment UUID>",
  "providerTransactionId": "sandbox-txn-0001",
  "amount": "125000.00",
  "currency": "VND",
  "status": "PAID"
}
```

For the `MOCK` callback, sign the canonical UTF-8 text below with HMAC-SHA256 using `PAYMENT_MOCK_CALLBACK_SECRET`; send the lowercase hexadecimal signature in `X-Payment-Signature`:

```text
{paymentId}|{providerTransactionId}|{amount with exactly 2 decimals}|{currency uppercase}|{status uppercase}
```

Example canonical text: `11111111-1111-1111-1111-111111111111|sandbox-txn-0001|125000.00|VND|PAID`.
Configure `PAYMENT_MOCK_CALLBACK_SECRET` with a private value before using the callback. The callback endpoint rejects requests while the secret is unset. For a Postman pre-request script, store that secret in a local environment variable named `paymentMockSecret`:

```javascript
const callback = JSON.parse(pm.request.body.raw);
const amountParts = String(callback.amount).trim().split('.');
if (!/^\d+$/.test(amountParts[0]) || !/^\d*$/.test(amountParts[1] || '')
    || (amountParts[1] || '').length > 2) {
  throw new Error('Callback amount must have at most 2 decimal places');
}
const amount = `${amountParts[0]}.${(amountParts[1] || '').padEnd(2, '0')}`;
const canonical = [
  callback.paymentId,
  callback.providerTransactionId.trim(),
  amount,
  callback.currency.trim().toUpperCase(),
  callback.status.trim().toUpperCase()
].join('|');
const signature = CryptoJS.HmacSHA256(
  canonical,
  pm.environment.get('paymentMockSecret')
).toString(CryptoJS.enc.Hex);
pm.request.headers.upsert({ key: 'X-Payment-Signature', value: signature });
```

The current provider implementation is `MOCK`; there is no verified production VNPAY/MOMO gateway flow in this document. `MOCK` only marks a sandbox-ready attempt; it does not automatically make a payment successful.

## Status and Order behavior

- A new online attempt starts `PENDING`. Order creation calls `recordCodPayment` for COD and records provider `COD`, amount equal to the order total and status `PENDING`; the order remains `UNPAID`. COD does not use the gateway callback.
- When the management/shipping flow validly moves the order to `DELIVERED`, `completeCodPayment` marks the COD payment and order payment summary `PAID`. All shop segments must be `READY` before the order can move to shipping. The ADMIN UI labels shipping as simulated and requires confirmation; it does not expose arbitrary manual payment-status editing.
- A valid `MOCK` callback with `status: PAID`, matching amount/currency and an unused transaction ID changes the attempt to `PAID` and `Order.paymentStatus` to `PAID`. It leaves `Order.status` unchanged and rejects callbacks for cancelled orders.
- A valid `FAILED` callback sets only the attempt to `FAILED`; the Order remains `UNPAID`. A new attempt can then be created with a new idempotency key.
- An identical callback is idempotent. Reusing its transaction ID for another attempt, changing amount, or sending a conflicting callback returns an error.
- Order creation already reserves/decrements inventory. Payment callbacks never change inventory. Paid Orders cannot be cancelled until a refund flow exists.
- These payment endpoints do not expose a refund operation. Do not describe production refund processing as verified.

## Postman test plan

This is an unexecuted plan requiring a separately approved disposable test/demo database, compatible schema and permission for all listed writes. Hibernate uses `ddl-auto=validate`; schema preparation is a separate approved task, not an automatic step of this plan. Use private local/sensitive environment values `baseUrl`, `customerToken`, and `adminToken`; do not export or display credentials. Set the collection's bearer token per request.

1. Create an Order with `paymentMethod: COD`; expect `PENDING` and `paymentStatus: UNPAID`.
2. Read `/api/payments/order/{orderId}`; expect the COD record automatically created with the order, `PENDING`, provider `COD`, amount equal to the Order total. A new POST `/api/payments` request for COD using a fresh key is expected to return `409`; it is not the COD creation flow.
3. Get the Payment by ID; expect `200` for the owning customer.
4. Get `/api/payments/order/{orderId}` again; expect the same COD record, without creating a new gateway attempt.
5. Authenticate as Customer A and request Customer B's Payment ID; expect `404`.
6. Authenticate as Customer A and try creating a Payment for Customer B's Order; expect `404`.
7. Create a Payment with a random, nonexistent order ID; expect `404`.
8. Create a second Order using `VNPAY`, create its Payment, then send a correctly signed `PAID` callback to `/api/payments/callback/MOCK`; expect Payment `PAID`, Order `paymentStatus: PAID`, and unchanged Order status.
9. Re-send the exact callback from step 8; expect `200` and the same Payment ID/status.
10. Create another attempt for the Order paid in step 8; expect `409`.
11. Sign a callback whose amount differs from the Payment amount; expect `409` and no state change.
12. Reuse a transaction ID already attached to another Payment; expect `409`. Re-send the same transaction ID for the same terminal Payment with the same result; expect idempotent `200`.
13. Create a `MOMO` or `BANK_TRANSFER` Order and send a correctly signed `FAILED` callback; expect Payment `FAILED` and Order still `UNPAID`.
14. Try cancelling the paid Order as a customer and transition it to `CANCELLED` as an admin; both expect `409`.
15. Record Product/Variant quantity after Order creation and after successful callback; the quantity must be identical between those two checks.
16. Authenticate as ADMIN and get a Payment owned by a customer; expect `200`.
17. For a separately approved COD demo order, have every owning SELLER confirm and mark their segment `READY`; use the management shipping flow to create shipping, move it to `IN_TRANSIT`, then `DELIVERED`. Expect the order `DELIVERED` and COD payment/order summary `PAID`. Do not execute against real orders or mark delivery complete without the required confirmation.

No shared database migration or runtime request is performed by this documentation update. Existing SQL module files are references, not authorization to apply them to development data. Confirm schema/target/permissions separately before any checks. See [the demo script](../../docs/demo/DEMO-SCRIPT.md) and [the evidence table](../../docs/demo/EVIDENCE-AND-LIMITATIONS.md) for the current limits.
