# Implementation Plan — Softcover ↔ Hardcover Adapter

A step-by-step, **hand-implemented** build order for the endpoints in
[`Adapter_endpoints.md`](./Adapter_endpoints.md). Each step is sized so you can
implement it and **test that one endpoint in isolation** before moving on.

This plan follows the spec's suggested build order (§8) but slices it into the
smallest independently-testable units, and maps each unit onto the architecture
already in the repo.

---

## How this maps to the existing architecture

You already have the skeleton in place. Every new endpoint reuses the same moving parts:

| Concern              | Where it lives today                                                  | What you add per feature                          |
|----------------------|----------------------------------------------------------------------|---------------------------------------------------|
| GraphQL operation    | `src/main/graphql/*.graphql` → codegen into `nl.rhaydus.graphql`      | One `.graphql` file per Hardcover op              |
| Domain models (JSON) | `feature/user/HardcoverUser.kt`                                       | `@Serializable` data classes per §4 of the spec   |
| GraphQL → domain map  | `HardcoverClient.fetchUser()`                                         | A mapper function (keep it next to the model)     |
| Network call         | `HardcoverClient`                                                     | A `suspend fun` per op, forwarding the token      |
| REST route           | `feature/<domain>/<Domain>Routes.kt`                                  | `Route.xxxRoutes(client)` extension               |
| Wiring               | `plugins/Routing.kt`                                                  | Add `xxxRoutes(client)` inside `authenticate`     |

**Token plumbing.** Routes pull the bearer token from `call.principal<UserPrincipal>()?.token`
(see `MeRoutes.kt`) and pass it into `HardcoverClient`, which forwards it as
`Authorization: Bearer <token>` to Hardcover (see `fetchUser`). `UserPrincipal`
also carries `userId`, so for ops needing `$userId` you don't need an extra round trip.

**Suggested refactor before Step 1.** `HardcoverClient` is currently one file doing
auth + caching + the `me` query. As you add ~30 ops it will bloat. Consider either:
- splitting per-domain data sources (`BookDataSource`, `LibraryDataSource`, …) that
  share the one `ApolloClient`, with `HardcoverClient` keeping only `resolveUser` + cache; or
- keeping `HardcoverClient` thin and putting fetch/map logic in each feature package.

Either is fine — decide now so the pattern is consistent. The steps below assume
**per-feature data sources** but the route/model shape is identical regardless.

---

## Conventions to lock in early (do these before Step 2)

These four cross-cutting behaviors (spec §6) are where a naive 1:1 translation
breaks. Build them as reusable helpers the first time you need them, not inline:

1. **Error model (§3.1)** — a small helper that maps Apollo results to HTTP:
   - data present → 200 with body
   - populated `errors[]` → `422 { "error": "<joined messages>" }`
   - missing single resource → `404`
   - empty list → `200 []`
   Put it somewhere shared (e.g. `core/network/`).
2. **Scalar formats (§3.2)** — date/timestamp as strings, `releaseYear = -1` sentinel,
   `rating` rounded to 1 decimal. Centralize the rounding + sentinel logic in mappers.
3. **Canonical merge (§6.2)** — needed first in Step 2 (`GET /books/{id}`), reused in
   `/me/books`, search, trending. Write it once.
4. **Series position parsing (§6.1)** — pure function, fully unit-testable on its own.

Steps below call out exactly when each is first needed.

---

## Phase 0 — Foundations (no new endpoint, but unblocks everything)

- [ ] **0a. Error helper (§3.1).** Apollo result → Ktor response mapper. Unit-test it.
- [ ] **0b. Shared scalar/mapping helpers (§3.2):** rating rounding, `-1` release-year
      sentinel, date passthrough. Unit-test the pure bits.
- [ ] **0c. Decide the data-source layout** (per above) and document it in a one-liner.

*Test:* unit tests only; no HTTP surface yet.

---

## Phase 1 — Auth proven end-to-end

### Step 1 — `GET /me` ✅ (already done)

Already implemented in `MeRoutes.kt` + `HardcoverClient.resolveUser`. Treat as the
reference implementation for every step below.

*Test:* `curl -H "Authorization: Bearer <key>" localhost:<port>/me` → `{ "id": ... }`.
Bad token → `401`.

---

## Phase 2 — Catalog read path (unblocks most screens)

Order chosen so each step is testable with nothing but a known book/edition id.

### Step 2 — `GET /books/{id}`  ← `GetBookById`
First real catalog read. Introduces the full `Book` model (§4.1) and its nested
types (`Author` §4.3, `BookSeries` §4.4, `Tag` §4.5, `BookEdition` §4.2).
- First use of **canonical merge (§6.2)** — single-book variant (refetch canonical,
  null out `canonicalId`).
- First use of **series parsing (§6.1)**.
- `404` when not found.

*Test:* `GET /books/<knownId>`; verify a known canonical-redirect id returns the
survivor; verify a bogus id → `404`.

### Step 3 — `GET /books?ids=1,2,3`  ← `GetBooksByIds`
Reuses the `Book` model and mapper from Step 2. Adds: chunking at 200 internally,
and **re-sorting results into requested order** (canonical/missing ids sort last, §6.2).

*Test:* request a deliberately out-of-order id list incl. one bogus id; verify
output order matches input and the missing one is absent/last.

### Step 4 — `GET /editions/{editionId}/book-id`  ← `GetBookIdByEditionId`
Tiny, isolated. Returns `{ "bookId": ... }` or `404`.

*Test:* one known edition id; one bogus.

### Step 5 — `GET /books/{id}/editions`  ← `GetEditionsByBookId`
Returns `[ BookEdition ]` ordered by `users_count desc`, including edition-level authors.
Reuses the `BookEdition` model from Step 2.

*Test:* known book id with multiple editions; check ordering + author contributions.

### Step 6 — `GET /editions?ids=1,2,3`  ← `GetEditionsByIds`
Batch editions, chunked at 200. Reuses `BookEdition` mapper.

*Test:* multi-id request incl. a bogus id.

### Step 7 — `GET /editions/by-isbn/{isbn}`  ← `GetEditionByIsbn`
Barcode lookup: try ISBN-13 then ISBN-10 in one query, return first hit.
Returns `{ "bookId", "editionId" }` or `404`.

*Test:* a known ISBN-13 and a known ISBN-10; one unknown.

---

## Phase 3 — Library reads (the user's own data)

These need `userBook`/`userBookRead` (§4.6/§4.7) populated.

### Step 8 — `GET /me/books?status=1,2,3`  ← `GetUserBooks`
The big one. Fully-hydrated `Book` **with** `userBook` + `userBookRead`, nested
`book`/`edition`. Optional `status` CSV filter; ordered `updated_at desc`.
- Reuses canonical merge (§6.2) — **library variant**: overwrite catalog metadata
  with canonical's, *keep* user-specific fields, then null `canonicalId`.

*Test:* `GET /me/books`; then `?status=2`, `?status=1,3`. Verify your own library,
ordering, and that a known merged book shows canonical metadata but your own status/progress.

### Step 9 — `GET /me/lists`  ← `GetUserBookLists`
Returns `[ BookList ]` (§4.9) with nested `ListBook` (§4.10). Drop list-book rows
with null `editionId`. `slug == "owned"` flags the Owned list.

*Test:* `GET /me/lists`; verify Owned list flagged, null-edition rows dropped.

### Step 10 — `GET /me/profile`  ← `GetUserProfileData`
`UserProfileData` (§4.13). Computes `totalPagesRead` and `activeReadingDates` (§6.4).
Uses `userId` from the principal for the streak-journal filter.

*Test:* `GET /me/profile`; sanity-check `booksRead`, `averageRating`, and that
`activeReadingDates` is de-duplicated and newest-first (cap 1000).

---

## Phase 4 — Discovery (id-then-hydrate flows)

All three reuse Step 3's batch-fetch-and-resort.

### Step 11 — `GET /search?q=<text>`  ← `GetIdsForQuery` + `GetBooksByIds`
Get ids (per_page 25, weighted), hydrate via the Step 3 path, **re-sort into
search order**. Returns `[ Book ]`.

*Test:* a query with a predictable top hit; confirm order preserved.

### Step 12 — `GET /books/trending?from&to&limit&offset`  ← `GetTrendingBookIds` + hydrate
Same pattern, trending rank order; canonical-redirect ids sort last.

*Test:* a date window; verify rank order and pagination params.

### Step 13 — `GET /series/{seriesId}/next?afterPosition=<float>`  ← `GetNextBookInSeries`
Next book after a fractional position, tie-broken `users_count desc`, limit 1.
Returns `Book` or `404`.

*Test:* a series + position you know the answer for; a position past the end → `404`.

---

## Phase 5 — Writes (mutations)

Save these for last: they change real Hardcover data, so test against a throwaway
book or be ready to undo. Order is chosen so each write is verifiable via a read
endpoint you already built.

### Step 14 — `POST /me/books`  ← `MarkBookAsWantToRead` / `MarkBookAsRead`
Create a user_book (status 1 or 3). For status 3, set `user_date = today`,
privacy = Public. Returns `Book` with new `userBook`.

*Test:* add a book; verify with `GET /me/books`. Then `DELETE` it in Step 17.

### Step 15 — `PATCH /me/books/{userBookId}`  ← reading / edition / rating / review
Partial update, apply only present keys (§3.3). Start with the simple fields
(`status`, `rating`, `editionId`); add **review** (Slate, §6.3) once §6.3 helpers exist.
- First use of **Slate review parse/serialize (§6.3)** — build the read/write
  asymmetry absorber here. Consider doing rating/status first, review as a sub-step.

*Test:* patch status→2, then rating, then a review; verify each via `GET /me/books`.

### Step 16 — `PATCH /me/reads/{userBookReadId}`  ← `UpdateReadingProgress`
Progress for a read session. Pages XOR seconds (audiobook ⇒ seconds). Returns `Book`.

*Test:* update progress on a book you're reading; verify `userBookRead` in `GET /me/books`.

### Step 17 — `DELETE /me/books/{userBookId}`  ← `RemoveUserBook`
Returns `204`. Closes the loop on Step 14.

*Test:* delete the book added in Step 14; confirm gone from `GET /me/books`.

### Step 18 — `POST /books`  ← `CreateBook` (`upsert_book`)
Scan ingest by external ISBN (`platformId = 8`). Must check `errors[]` → 422 (§3.1).
Returns `{ bookId, editionId? }`.

*Test:* an ISBN Hardcover already has (idempotent-ish); confirm error path on a bad payload.

### Step 19 — `POST /me/editions/{editionId}/owned`  ← `MarkEditionAsOwned`
Adds edition to the Owned list. Returns `ListBook`.

*Test:* mark an edition owned; verify it appears in the Owned list via `GET /me/lists`.

### Step 20 — Tags: `GET /me/tags` then `PUT /me/tags`  ← `FindTagsByUserAndTaggable` / `SaveTags`
GET first (read, safe), then PUT (replace set). `category` is the API string (§3.4).

*Test:* read tags on a book, PUT a new set, GET again to confirm.

### Step 21 — Lists writes
In dependency order, each verifiable via `GET /me/lists`:
1. `POST /me/lists` (`CreateList`) — fills defaults (§ endpoint doc).
2. `POST /me/lists/{id}/books` (`CreateListBook`).
3. `PATCH /me/lists/{id}` (`UpdateList`) — today only `ranked`.
4. `DELETE /me/list-books/{listBookId}` (`RemoveListBook`) — returns parent `BookList`.
5. `PUT /me/lists/{id}/order` (`UpdateListBookPositions`) — **two-step reorder (§6.5)**;
   build the clear-then-apply internally.

*Test:* create a list → add books → reorder → remove a row → check ordering each step.

---

## Cross-reference: cross-cutting behaviors and where they first appear

| Behavior (spec §)            | First needed in   | Reused in                          |
|------------------------------|-------------------|------------------------------------|
| Error model (§3.1)           | Step 2            | everywhere                          |
| Scalars/rounding/sentinel (§3.2) | Step 2        | all catalog/library reads           |
| Series parsing (§6.1)        | Step 2            | every `Book` response               |
| Canonical merge (§6.2)       | Step 2            | Steps 3, 8, 11, 12                  |
| Slate review (§6.3)          | Step 15           | reused in library reads (Step 8)*   |
| Profile pages/streak (§6.4)  | Step 10           | —                                   |
| List reorder two-step (§6.5) | Step 21.5         | —                                   |

\* Slate **read** parsing is technically first hit in Step 8 if a user_book has a
review; if so, pull the §6.3 read-path helper forward into Step 8 and add the write
path in Step 15.

---

## Definition of done per step

For each step, you've finished when:
1. The `.graphql` op compiles (codegen runs).
2. The domain model(s) are `@Serializable` and match §4.
3. The mapper handles nulls/sentinels/rounding per §3.2.
4. The route is wired in `Routing.kt` under `authenticate("external")`.
5. You can `curl` it with a real token and get the spec-shaped JSON.
6. Error cases return the right status (`401` / `404` / `422`).
