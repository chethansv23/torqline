# Torqline API reference

All endpoints are reachable through the gateway at `http://localhost:8080/api`. Each service also serves
interactive Swagger UI:

| Service | Swagger UI |
|---|---|
| appointment-service | http://localhost:8081/swagger-ui.html |
| repair-order-service | http://localhost:8082/swagger-ui.html |
| parts-inventory-service | http://localhost:8083/swagger-ui.html |
| notification-service | http://localhost:8084/swagger-ui.html |

Ready-made requests for IntelliJ and VS Code are in [`api.http`](../api.http).

## Conventions

- JSON in and out. Times in requests are the **dealer's local time** (`2030-01-07T10:00`); responses include both
  local times and UTC instants.
- Every response through the gateway carries an `X-Request-Id` header. Send your own to correlate logs.
- Errors use [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) problem details with a stable `code`:

```json
{
  "status": 409,
  "title": "Conflict",
  "code": "SLOT_UNAVAILABLE",
  "detail": "No BIKE bay is free at 2030-01-07T10:00 for GENERAL_SERVICE",
  "instance": "/api/appointments"
}
```

| Code | Status | Meaning |
|---|---|---|
| `VALIDATION_FAILED` | 400 | A field is missing or malformed; `errors` maps each field to a message |
| `OFF_GRID` | 400 | Slot doesn't start on the hour or half hour |
| `OUTSIDE_HOURS` | 400 | Job wouldn't fit inside opening hours |
| `SLOT_IN_PAST` | 400 | Slot has already started |
| `SERVICE_NOT_OFFERED` | 400 | Job doesn't apply to the vehicle type, e.g. wheel alignment for a bike |
| `NO_BAYS` | 400 | Branch has no bays for that vehicle type |
| `NOT_FOUND` | 404 | Unknown id |
| `SLOT_UNAVAILABLE` | 409 | Every suitable bay is taken for that time |
| `INVALID_STATE` / `INVALID_TRANSITION` | 409 | Action not allowed in the current status |
| `CONCURRENT_MODIFICATION` | 409 | Someone else changed the record; retry |

---

## Dealers

### `GET /dealers`

```json
[{ "id": "TQ-BLR-IND", "name": "Torqline Indiranagar", "city": "Bengaluru", "timezone": "Asia/Kolkata",
   "openTime": "09:00:00", "closeTime": "18:00:00", "bayCount": { "CAR": 3, "BIKE": 2 } }]
```

### `GET /dealers/{dealerId}` · `GET /dealers/{dealerId}/bays`

### `GET /dealers/service-types`

The service catalogue with duration per vehicle type. A missing key means the job isn't offered for that vehicle.

```json
[{ "code": "GENERAL_SERVICE", "description": "Periodic maintenance service", "durationMinutes": { "CAR": 120, "BIKE": 60 } },
 { "code": "CHAIN_SPROCKET", "description": "Chain and sprocket kit replacement", "durationMinutes": { "BIKE": 60 } }]
```

## Appointments

### `GET /appointments/availability?dealerId&vehicleType&serviceType&date`

```json
{ "dealerId": "TQ-BLR-IND", "date": "2030-01-07", "vehicleType": "BIKE", "serviceType": "GENERAL_SERVICE",
  "durationMinutes": 60,
  "slots": [ { "start": "09:00:00", "end": "10:00:00", "freeBays": 1 },
             { "start": "09:30:00", "end": "10:30:00", "freeBays": 0 } ] }
```

### `POST /appointments`

Header `Idempotency-Key: <uuid>` is optional but recommended.

```json
{ "dealerId": "TQ-BLR-IND", "customerName": "Asha Rao", "customerPhone": "9845012345",
  "vehicleType": "BIKE", "vehicleNumber": "KA 03 HB 1234", "vehicleMake": "Royal Enfield",
  "vehicleModel": "Classic 350", "serviceType": "GENERAL_SERVICE", "slotStart": "2030-01-07T10:00",
  "notes": "Front brake squeals" }
```

| Response | When |
|---|---|
| `201 Created` | New booking |
| `200 OK` | Same `Idempotency-Key` seen before; returns the original booking |
| `409 SLOT_UNAVAILABLE` | Every bay of that vehicle type is taken |

```json
{ "id": "de63d0bd-…", "dealerId": "TQ-BLR-IND", "bayId": 4, "status": "BOOKED",
  "vehicleType": "BIKE", "vehicleNumber": "KA03HB1234", "serviceType": "GENERAL_SERVICE",
  "localStart": "2030-01-07T10:00:00", "localEnd": "2030-01-07T11:00:00",
  "slotStart": "2030-01-07T04:30:00Z", "slotEnd": "2030-01-07T05:30:00Z", "…": "…" }
```

### `GET /appointments?dealerId&date` · `GET /appointments/{id}`

### `POST /appointments/{id}/check-in`

Body (optional): `{ "odometerKm": 18500 }`. Moves `BOOKED → CHECKED_IN` and opens a repair order asynchronously.

### `POST /appointments/{id}/cancel`

Body: `{ "reason": "Customer travelling" }`. Moves `BOOKED → CANCELLED` and frees the bay.

## Repair orders

### `GET /repair-orders?dealerId[&status]` · `GET /repair-orders/{id}`

```json
{ "id": "dfbb355a-…", "roNumber": "RO-2026-001002", "status": "COMPLETED", "vehicleType": "BIKE",
  "vehicleNumber": "KA05TR4242", "serviceType": "GENERAL_SERVICE", "technician": "Ravi K",
  "parts": [ { "sku": "BRAKE-PAD-BIKE", "name": "Disc brake pad set (bike)", "quantity": 1,
               "unitPrice": 650.00, "status": "RESERVED" } ],
  "labourAmount": 400.00, "partsAmount": 1100.00, "taxAmount": 270.00, "totalAmount": 1770.00 }
```

### `POST /repair-orders/{id}/assign`

`{ "technician": "Ravi K" }` · `OPEN → IN_PROGRESS`

### `POST /repair-orders/{id}/parts` → `202 Accepted`

`{ "lines": [ { "sku": "OIL-10W30-1L", "quantity": 1 } ] }` · `IN_PROGRESS → PARTS_PENDING`. Poll the order: it returns to
`IN_PROGRESS` with each line `RESERVED`, or all lines `REJECTED` and a `note` explaining why.

### `POST /repair-orders/{id}/complete`

`IN_PROGRESS → COMPLETED`; fills in `partsAmount`, `taxAmount` and `totalAmount`.

### `POST /repair-orders/{id}/cancel`

`{ "reason": "…" }`. Any reserved parts are released.

## Parts and reservations

| Endpoint | Description |
|---|---|
| `GET /parts?dealerId[&fitment=CAR\|BIKE\|UNIVERSAL]` | Stock list; `CAR` and `BIKE` also include universal parts |
| `GET /parts/low-stock?dealerId` | Parts at or below the reorder level |
| `GET /parts/{dealerId}/{sku}` | One part |
| `POST /parts/{dealerId}/{sku}/restock` | `{ "quantity": 10 }` |
| `GET /reservations?repairOrderId` | Reservations held for a job |

## Notifications

`GET /notifications[?recipient][&limit=50]`: sent messages, newest first.

## Events (Kafka)

Services also communicate through these topics. Payloads are JSON; `eventId` and `eventType` travel as headers.

| Topic | Events |
|---|---|
| `torqline.appointment.events` | `AppointmentBooked`, `AppointmentCancelled`, `AppointmentCheckedIn` |
| `torqline.repair-order.events` | `RepairOrderCreated`, `PartsReservationRequested`, `RepairOrderCompleted`, `RepairOrderCancelled` |
| `torqline.inventory.events` | `PartsReserved`, `PartsReservationFailed`, `PartLowStock` |

Messages that keep failing are moved to `<topic>-dlt` after 3 retries. Browse topics with `make tools` → http://localhost:8090.
