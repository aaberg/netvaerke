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

- The current `AuthorizationEngine.authorize(actorId, tenantId, operation)` has
  multiple arguments. It becomes IFX/NATS-compatible only after IFX supports
  zero or more service arguments.
- `Operation` must have `@Serializable` before it is sent as a NATS argument.
- Define operation semantics explicitly. The current values are `ReadContacts`
  and `UpdateContacts`; decide whether create and delete are included in
  `UpdateContacts` or should have distinct operations.
- The authorization implementation must query current membership via
  `TenantAccess` and apply the chosen OWNER/MEMBER policy. A denied request must
  not call `ContactAccess`.

### Network Manager

- Add `tenant-access` and `authorization-engine` dependencies to the module.
- Implement the manager so every method first calls `AuthorizationEngine`, then
  calls the tenant-scoped `ContactAccess` operation.
- Normalize an unauthorized or cross-tenant contact lookup to the same
  not-found result so contact existence is not disclosed.
- Define and test the detail policy:
  - zero or one `WorkInfo`, `Note`, and `ContactImage`
  - zero or one primary `EmailAddress`
  - `mainEmailAddress` is derived from the primary email
  - a deterministic handling of malformed lower-layer data with duplicate
    singleton details

### IFX Multi-Argument Support

IFX currently requires exactly one source argument because `NatsTransport`
serializes only the first method argument. The proxy, tracing, and direct
transport already pass the complete argument array, so the NATS codec and
service validation are the primary changes.

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
3. Implement and test `NetworkManager` authorization, mapping, and detail rules.
4. Make contact ownership writes atomic and add persistence integration tests.
