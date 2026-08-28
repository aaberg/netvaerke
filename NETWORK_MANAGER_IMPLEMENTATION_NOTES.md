# Network Manager Implementation Notes

This document records the agreed direction and the remaining work for the
network manager, authorization engine, IFX transport, and contact persistence.

## Agreed Boundaries

- The layer above `NetworkManager` authenticates the end user and supplies the
  authenticated subject as `actorId`. Browser input must never supply `actorId`.
- `NetworkManager` authorizes every operation against current tenant membership
  before reading or mutating contacts.
- `AuthorizationEngine` owns the policy decision. `NetworkManager` supplies the
  actor, tenant, and requested operation.
- `NetworkManager` owns the contact-detail cardinality policy. The contact access
  layer intentionally remains flexible.
- NATS access to the NetworkManager subject must be limited to trusted backend
  services. Membership is still checked by `NetworkManager` on each request.
- `FileStorage` is a generic, non-IFX utility library used directly by the Ktor
  application. It uploads, deletes, and creates signed Garage URLs, but neither
  authorizes requests nor applies image policy.
- Contact-image uploads pass through Ktor. Ktor obtains an authorized,
  server-generated file key from `NetworkManager`, validates the image, and
  streams it to Garage through `FileStorage`. It then asks `NetworkManager` to
  associate the key with the contact.
- Contact-image downloads remain direct browser-to-Garage requests. Ktor maps a
  `fileKey` from the trusted `NetworkManager` response to a one-hour signed URL
  using `FileStorage`; Ktor owns this client-facing URL-expiry policy.
- A contact persists only its durable image `fileKey`. It never persists a
  signed URL or MIME type. `NetworkManager` exposes the key only to trusted
  backend callers; Ktor client-facing DTOs expose only a freshly issued URL.

## Current Contract Status

- `NetworkManager` now consistently carries `tenantId` and `actorId` for all
  operations. This makes authorization-before-contact-access possible.
- `getContact` now returns a nullable result and `createNewContact` returns the
  created contact DTO.
- Network manager input and output DTOs are serializable.
- `AuthorizationEngine.authorize` now includes `Operation`, so its policy can
  distinguish reads from mutations.
- `AuthorizationResponseDto` is serializable.
- IFX now supports zero, one, or multiple source arguments over Direct and NATS.
  NATS sends a positional JSON argument envelope and resolves serializers from
  Kotlin types, including top-level nullable values.

## Remaining Findings

### Authorization Engine

- `AuthorizationEngine.authorize(actorId, tenantId, operation)` is compatible
  with IFX/NATS multi-argument transport.
- `Operation` must have `@Serializable` before it is sent as a NATS argument.
- Define operation semantics explicitly. The current values are `READ_CONTACTS`
  and `UPDATE_CONTACTS`; decide whether create and delete are included in
  `UPDATE_CONTACTS` or should have distinct operations.
- The authorization implementation must query current membership via
  `TenantAccess` and apply the chosen OWNER/MEMBER policy. A denied request must
  not call `ContactAccess`.

### Network Manager

- Add the missing `tenant-access` dependency to the module.
- Implement the manager so every method first calls `AuthorizationEngine`, then
  calls the tenant-scoped `ContactAccess` operation.
- Normalize an unauthorized or cross-tenant contact lookup to the same
  not-found result so contact existence is not disclosed.
- Define and test the detail policy:
  - zero or one `WorkInfo`, `Note`, and `ContactImage`
  - zero or one primary `EmailAddress`
  - `primaryEmailAddress` is derived from the primary email
  - a deterministic handling of malformed lower-layer data with duplicate
    singleton details

### Contact Images

- Remove `mimeType` from `ContactImage`; its only persisted storage reference is
  `fileKey`. Do not accept a `ContactImage` in contact create/update DTOs, as a
  browser must not select a durable storage key.
- Add a generic `FileStorage` utility module and make it available to Ktor. It
  provides suspending streaming `putFile` and `deleteFile` operations, plus
  signed GET URL generation. It receives a bucket, file key, accepted content
  type where relevant, and file content; it must not depend on IFX or Ktor.
- Add an IFX/NATS-safe manager operation that authorizes an image update and
  returns a unique tenant/contact-scoped `fileKey`. Ktor must obtain this key
  before reading the upload body. The operation contains no file bytes.
- Ktor enforces a maximum byte size while streaming and validates allowed image
  formats and dimensions from the actual bytes before calling `FileStorage`.
  JPEG, PNG, and WebP are the initial candidate allowlist; SVG is excluded
  unless explicitly required.
- Ktor stores the validated image with its accepted HTTP `Content-Type`, so
  Garage returns the correct type on the signed download URL. This upload
  metadata is not part of the contact domain model.
- After a successful Garage write, Ktor calls `NetworkManager` to associate the
  generated `fileKey` with the contact. That operation reauthorizes the update.
  If it fails, Ktor deletes the newly stored object. Replacing or deleting a
  contact image requires deletion of the former object after the contact update;
  failed deletion requires a retry or cleanup job.
- `NetworkManager` response DTOs used over NATS carry `fileKey` for trusted
  backend callers. Ktor maps it to a one-hour signed URL using `FileStorage` in
  its client-facing DTO. Never return the storage key or persist/log the signed
  URL outside the trusted backend boundary.

### IFX Multi-Argument Support

IFX supports zero or more source arguments. NATS serializes them in a positional
JSON envelope; the proxy, tracing, and direct transport pass the complete
argument array.

Use a positional request envelope:

```json
{"arguments":[/* values in declared method-parameter order */]}
```

The NATS operation subject identifies the method. The server uses locally known
serializers for each position; it must not trust serializers supplied by a
client.

#### Completed: Transport Contract and Codec

1. [x] Relax `ServiceContract` to allow a suspend method with zero or more source
   arguments followed by the compiler-generated continuation.
2. [x] Replace the single request serializer in `MethodCodec` with an ordered list
   of argument serializers.
3. [x] Encode all non-continuation client arguments as `JsonElement` values in the
   request envelope.
4. [x] Validate the envelope argument count server-side, decode every argument with
   its local serializer, append the continuation, and invoke the service.
5. [x] Keep reply envelopes unchanged.
6. [x] Treat this as a wire-protocol change. Deploy NATS clients and servers
   together, or use a versioned subject.

#### Completed: Kotlin Type Fidelity

1. [x] Resolve argument and return serializers from Kotlin `KType` rather than Java
   reflection `Type`.
2. [x] Add `kotlin-reflect` to IFX for Kotlin method metadata.
3. [x] This preserves top-level nullable argument and response types. Fail clearly
   for unsupported Java interfaces, unnamed/unresolved parameters, or types
   without serializers.

#### Completed: IFX Tests

1. [x] Change the existing no-request validation test from rejection to acceptance.
2. [x] Add Direct transport tests for zero, one, and multiple arguments.
3. [x] Add NATS round-trip tests for zero, one, and multiple arguments, nullable
   values, `Unit` responses, and remote exceptions.
4. [x] Add malformed-envelope tests for an incorrect argument count and invalid
   argument JSON.

### Contact Tenant Integrity

`ContactAccessImpl.saveContact` reads a contact and then performs an upsert in a
separate database operation. Two concurrent writers can both see a missing
contact ID, then the losing upsert can update the stored tenant to its own
tenant. Tenant ownership must never be updated by the conflict path.

#### Increment 4: Atomic Contact Writes

1. Remove the preliminary read from `saveContact`.
2. Use one conditional upsert that updates name/details only when the existing
   row has the same tenant.
3. Treat a zero affected-row count as an existing contact in another tenant.
4. Scope the contact read in SQL with both `id` and `tenant` rather than loading
   by ID and filtering in Kotlin.
5. Make delete return an affected-row count and avoid its read-then-delete race.
6. Add integration tests for cross-tenant writes/deletes and concurrent saves of
   the same contact ID.

## Suggested Delivery Order

1. Mark `Operation` serializable and finalize its operation vocabulary.
2. Add authorization and tenant dependencies, then implement and test
   `AuthorizationEngine`.
3. Make contact ownership writes atomic and add persistence integration tests.
4. Change the contact-image persistence and DTO contracts to retain only a
   server-generated `fileKey`; add the NATS-safe image-key authorization and
   association operations.
5. Add and test the generic `FileStorage` utility for Garage streaming writes,
   deletes, and signed GET URLs.
6. Implement and test the Ktor image-upload endpoint, including authorization
   calls, byte and image validation, Garage cleanup, and no byte transfer over
   NATS.
7. Implement and test `NetworkManager` authorization, mapping, and detail
   rules; implement Ktor mapping from trusted file keys to signed image URLs.
