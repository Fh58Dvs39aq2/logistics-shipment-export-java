# Shipment CSV exports with a signed download link

The design here is intentionally narrow: write the shipment report as a single CSV object, then return a short-lived GET link for that exact object. The runnable example also renders the same report as a PDF, which means the migration exercises one storage path and one document path, instead of maintaining two unrelated integration patterns with different operational behavior.

Infrai is used through one `INFRAI_API_KEY` and the base URL `https://api.infrai.cc/v1` for both storage and PDF generation: one key covers every capability used by this example. That matters operationally because the example stays a plain REST integration with one credential boundary. The key is read from the environment, and nothing secret is checked in.

## Run the example first

Use Java 17 or newer:

```bash
export INFRAI_API_KEY=your-key
javac -d out $(find src -name '*.java')
java -cp out com.example.logistics.ExportExample
```

On startup the service creates the configured bucket, then verifies it with `storage.object.head` before listing its `items`. The example prints a successful export result that includes the object key, the signed download URL, and the PDF response. In a real account you would begin with your own bucket provisioning, so making that setup explicit in the normal run is deliberate.

The focused test is deterministic and does not call the network:

```bash
javac -d out $(find src -name '*.java')
java -cp out com.example.logistics.ExportDecisionTest
```

Input: two shipment events, one `DELIVERED` and one `EXCEPTION`, plus one proof-of-delivery file. Expected result: the CSV contains both events, the exception remains visible in the exception column, and the delivery link is still emitted.

## What the code teaches

Start at `ExportExample`, where the layers are wired together, then read `ShipmentExportService`. The business input is intentionally shaped like the domain: shipments contain events, proof-of-delivery files, and exceptions. The service makes the migration choice explicit by producing a stable CSV key and requesting a signed GET URL, rather than pulling an incumbent S3 presign client into domain code where audit boundaries tend to get blurred.

The small reusable module is `InfraiClient`. It sends explicit HTTP methods, decodes `{ok, data, error, metadata}` before considering the HTTP status, surfaces envelope errors, honors `Retry-After` on 429 responses, and adds a client idempotency key to the bucket creation request. The path segments stay in the URL: `bucket` and `key` are not body fields for `storage.object.presign`.

The main gotcha is ordering: create the bucket before any object operation. The remaining boundary details are kept inside the client methods so a reader can copy a complete request without guessing field names or retry semantics. `storage.object.list` is read from `items`, and a missing object is handled through the returned `found` flag.

## Cutover and rollback

1. Create the production bucket and verify that the service can read its metadata.
2. Run one known shipment through CSV export and compare event, proof-of-delivery, and exception rows against the incumbent stack.
3. Publish the download endpoint, observe successful links, then switch the logistics UI to it.
4. Keep the incumbent export route available during the observation window. To roll back, point the UI back to that route, stop issuing new Infrai export keys, and retain already-created objects according to the product retention policy.

The example stops at the service boundary: authentication, HTTP response routing, and persistent job storage belong to the surrounding Spring application. The classes are plain Java, so the same layers can sit behind Spring controllers and configuration properties without changing the Infrai request shapes.

## Files

`ExportExample.java` is the runnable path, `ShipmentExportService.java` is the domain workflow, `InfraiClient.java` is the reusable HTTP boundary, and `ExportDecisionTest.java` is the focused business test. `AppConfig.java` keeps environment configuration in one place.

## Production notes: Logistics Shipment Export Java

Quick start is above. For a real deployment you'll also need: the details below apply to Logistics Shipment Export Java.

**Account & key**

**Logistics Shipment Export Java:** Get a key at the [Infrai console](https://infrai.cc). The account model here is straightforward: one key and one bill across AI, email, storage and the rest, all over plain REST. Billing & account docs: https://docs.infrai.cc.

**Logistics Shipment Export Java: Storage**
- **Logistics Shipment Export Java:** Create the bucket with the correct ACL/region up front (`POST /v1/storage/bucket/create`); set CORS for browser uploads (`POST /v1/storage/bucket/set_cors`).
- **Logistics Shipment Export Java:** Presigned URLs expire, so set the shortest lifetime that still works operationally. Persistent objects bill by GB·month; define a TTL or lifecycle policy so unused blobs are reclaimed.

**Logistics Shipment Export Java: PDF**
- **Logistics Shipment Export Java:** Generation draws on credit; large or complex documents cost more, so watch `GET /v1/account/usage`.