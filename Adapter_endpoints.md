# Softcover ↔ Hardcover REST Adapter — API Specification

This document specifies the REST API a self-hosted adapter must expose so the **Softcover** Android/KMP client can talk
to it instead of calling [Hardcover](https://hardcover.app)'s GraphQL API directly. The adapter sits in the middle:

```
Softcover app  ──REST──▶  Your adapter  ──GraphQL──▶  https://api.hardcover.app/v1/graphql
```

It is derived from every GraphQL operation the app currently issues (15 queries, 16 mutations, plus shared fragments)
and the exact domain models and mappers those operations feed. Every field name, type, nullability, enum code, and
non-obvious transformation the app relies on is documented here.

> **Design stance.** Because you control both the adapter's response shape *and* the app-side code that consumes it, the
> recommended contract is to return JSON shaped like the app's **domain models** (documented in §4). That makes the
> app-side mapping near-trivial and is the clean option. The adapter does the GraphQL → domain translation once,
> server-side, instead of every client re-implementing it. Field names below use the domain model names; the adapter is
> the single owner of that translation.

---

## 1. Current transport & what changes

| Concern    | Today (direct GraphQL)                                       | With the adapter                                   |
|------------|--------------------------------------------------------------|----------------------------------------------------|
| Endpoint   | `https://api.hardcover.app/v1/graphql` (single POST)         | Your adapter base URL, REST routes below           |
| Client lib | Apollo Kotlin v4 + generated operations + mappers            | A REST/HTTP client + thin JSON→domain mappers      |
| Auth       | `Authorization: Bearer <hardcover-api-key>` on every request | See §2                                             |
| Timeout    | 60 s                                                         | Recommend matching (≥ 60 s)                        |
| Caching    | Apollo normalized in-memory cache (10 MB)                    | Adapter can cache server-side; app may add its own |

The app's network entry points are two helpers — `safeQuery` (queries) and `safeMutation` (mutations) — in
`core/network/.../helper/ApolloExtensions.kt`. The error contract those enforce (§3) is what the REST layer must
reproduce.

---

## 2. Authentication

**Today:** During onboarding the user pastes a **personal Hardcover API key** (a long-lived bearer token from their
Hardcover account). It is stored in OS-secure storage (Android Keystore / iOS Keychain) and attached as
`Authorization: Bearer <key>` to every GraphQL call by `AuthInterceptor`. The key is validated by calling `GetUserId` (
`me { id }`) — a 200 with a user id means the key is good.

**Decision you must make for the adapter** — pick one:

1. **Pass-through (recommended, smallest trust change).** The app keeps sending `Authorization: Bearer <hardcover-key>`;
   the adapter forwards that same header to Hardcover. The adapter holds no secrets and stays a pure translator.
   Onboarding/validation flow is unchanged (`GET /me` replaces `GetUserId`).
2. **Adapter-owned auth.** The adapter issues its own tokens/sessions and stores Hardcover credentials server-side. More
   work, lets you add your own users/rate-limiting, but changes the onboarding screen and the secure-storage contract in
   the app.

This spec assumes **option 1**: every route below expects an inbound `Authorization: Bearer <token>` that the adapter
relays to Hardcover. `401` from Hardcover → propagate as `401`.

---

## 3. Conventions the adapter must honor

### 3.1 Error model

The app's `safeQuery`/`safeMutation` treat a call as failed unless it gets a data-bearing success. Mirror this with HTTP
status codes:

- **2xx** with a JSON body → success.
- **Offline** is detected client-side *before* the call (the app throws `OfflineException` when the device is offline),
  so the adapter doesn't need a special offline code — but network errors reaching the app should be ordinary connection
  failures, not 2xx-with-error-body.
- **GraphQL errors / business rejections** (e.g. `upsert_book` returns an `errors` array) must surface as a non-2xx with
  a message. Today the app reads Hardcover's `errors[]` and throws; the adapter should translate a populated `errors[]`
  into **422** (or 4xx) with `{ "error": "<joined messages>" }`. Do **not** return 200 with an empty/partial body — the
  app treats "no data and no errors" as a hard failure.
- Empty result sets are **not** errors: an empty list is `[]` with 200; a missing single resource is **404** (the app
  throws `BookNotFoundException` when `GET /books/{id}` yields nothing).

### 3.2 Scalar & value formats

The app's Apollo scalar mapping (`core/network/build.gradle.kts`) defines how Hardcover scalars become Kotlin types.
Match these in JSON:

| Hardcover scalar            | JSON representation   | Notes                                                                                                                                                                                                                                                  |
|-----------------------------|-----------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `date`                      | string `"YYYY-MM-DD"` | e.g. release dates, `last_read_date`                                                                                                                                                                                                                   |
| `timestamp` / `timestamptz` | ISO-8601 string       | e.g. `created_at`, `updated_at`, `reviewed_at`, journal `updated_at`. Both bare-date and full-offset forms appear (`"2026-05-04"` and `"2026-05-04T06:20:37.939189+00:00"`); the profile streak parser handles both — keep whatever Hardcover returns. |
| `bigint`                    | number/string Long    | Used as the **taggable id** in tag ops (book id passed as `bigint`).                                                                                                                                                                                   |
| `float8`                    | number (Double)       | Series `position`, `afterPosition`.                                                                                                                                                                                                                    |
| `numeric`                   | number (Double)       |                                                                                                                                                                                                                                                        |
| `smallint`                  | number (Int)          |                                                                                                                                                                                                                                                        |

**Dates that are unknown:** `releaseYear` uses the sentinel **`-1`** when null (both Book and Edition). Keep that
sentinel in domain-shaped responses, or send `null` and let the app default — but `-1` is what the current mappers emit.

**Rating rounding:** book-level `rating` is rounded to **1 decimal** (`(rating*10).roundToInt()/10.0`). If you return
domain-shaped JSON, do the rounding in the adapter.

### 3.3 Optional / partial-update semantics

Hardcover's update mutations use Hasura "set only the fields present" semantics (Apollo `Optional.Present` vs
`Optional.Absent`). For REST `PATCH` bodies this maps cleanly to **"include a key to change it, omit it to leave it
untouched."** Document which fields a given PATCH accepts (below) and apply only present keys. Sending `null` should
mean "set to null" only where noted; otherwise prefer omission.

### 3.4 Enums & constants (authoritative table)

| Concept                                  | Value → meaning                                                                                                           | Where used                 |
|------------------------------------------|---------------------------------------------------------------------------------------------------------------------------|----------------------------|
| **Book status** (`status_id`)            | `1`=Want to Read, `2`=Currently Reading, `3`=Read, `5`=Did Not Finish, `6`=None. **`4` (legacy "Paused") maps to None.**  | user_books                 |
| **Privacy** (`privacy_setting_id`)       | `1`=Public, `2`=Followers, `3`=Private. App always writes **Public (1)**.                                                 | user_books, lists          |
| **Reading format** (`reading_format_id`) | `1`=Physical, `2`=Audiobook, `3`=Physical&Audio, `4`=E-book. `null` allowed.                                              | editions                   |
| **Tag category** (`category` string)     | `"Genre"`, `"Mood"`, `"Tag"`, `"Content Warning"`, `"Other"`. Unknown → Other. **Send the API string, not an enum name.** | tags                       |
| **Taggable type**                        | Always the string `"Book"`.                                                                                               | tags                       |
| **ISBN platform id**                     | `8` (Hardcover's ISBN external-book provider) for `upsert_book`.                                                          | scan/add-by-ISBN           |
| **Journal events**                       | `"progress_updated"`, `"user_book_read_started"`, `"user_book_read_finished"`, `"status_stopped"`.                        | user_books, profile streak |

---

## 4. Shared response object schemas

These are the JSON shapes the app ultimately needs (its domain models). Reuse them across endpoints.

### 4.1 `Book`

```jsonc
{
  "id": 12345,                       // Int, required
  "canonicalId": null,               // Int?, only set if it differs from id (see §6.2 canonical merges)
  "title": "The Great Gatsby",       // String, required ("" if missing)
  "headline": "A Jazz Age novel",    // String, "" if absent
  "description": "In my younger…",   // String, "" if absent
  "rating": 3.8,                     // Double, rounded to 1 decimal (0.0 if null)
  "releaseYear": 1925,               // Int, -1 if unknown
  "releaseDate": "1925-04-10",       // String? "YYYY-MM-DD"
  "coverUrl": "https://…",           // String, "" if absent
  "usersCount": 250000,              // Int
  "ratingsCount": 85000,             // Int
  "isCompilation": false,            // Boolean
  "authors": [ { "id": 789, "name": "F. Scott Fitzgerald" } ],   // see Author
  "bookSeries": { "id": 1, "name": "…", "amountOfBooks": 5 },    // BookSeries? (first series only)
  "positionsInSeries": [1.0],        // List<Double> (see §6.1 parsing); [] if none
  "tags": [ /* Tag */ ],             // editorial tags from taggable_counts
  "editions": [ /* BookEdition */ ], // for GET /books/{id}: single-element list = default cover edition
  "defaultEdition": { /* BookEdition */ },  // BookEdition?, == default cover edition
  "userBook": null,                  // UserBook? — null on public book fetches; populated only in library context
  "userBookRead": null               // UserBookRead? — same
}
```

### 4.2 `BookEdition`

```jsonc
{
  "id": 54321,                 // Int, required
  "canonicalId": null,         // Int?
  "bookId": 12345,             // Int, required
  "title": "The Great Gatsby", // String?
  "url": "https://…/cover.jpg",// String? — image.url, falling back to first of images[]
  "publisher": "Scribner",     // String?
  "isbn10": "0743273567",      // String?
  "isbn13": "9780743273565",   // String?
  "pages": 180,                // Int?
  "audioSeconds": null,        // Int?
  "authors": [ /* Author */ ], // edition-level contributions; [] if none
  "releaseYear": 1925,         // Int, -1 if unknown
  "releaseDate": "1925-04-10", // String?
  "format": "Hardcover",       // String, "" if absent (edition_format)
  "readingFormat": "Physical", // String? — name of reading_format_id enum, or null
  "owned": false,              // Boolean — always false from network (ownership tracked elsewhere)
  "localImagePath": null       // always null over the wire
}
```

### 4.3 `Author`

```jsonc
{ "id": 789, "name": "F. Scott Fitzgerald" }   // both required
```

### 4.4 `BookSeries`

```jsonc
{ "id": 1, "name": "Jazz Age Classics", "amountOfBooks": 5 }  // amountOfBooks = primary_books_count, 0 if null
```

### 4.5 `Tag` (editorial, read-only on books)

```jsonc
{ "id": 101, "name": "Literary Fiction", "category": "Genre", "count": 1250 }
```

`category` is the API string (§3.4); `count` defaults to 0.

### 4.6 `UserBook`

```jsonc
{
  "id": 999,                   // Int, required (the user_book id — needed for all mutations)
  "status": 3,                 // status_id Int (see §3.4); send the code, not a label
  "dateAdded": "2025-06-01",   // String, required
  "createdAt": "2025-06-01T10:30:00Z",  // String?
  "updatedAt": "2025-06-20T14:22:00Z",  // String?
  "privacySettingId": 1,       // Int
  "editionId": 98765,          // Int?
  "lastReadDate": "2025-06-15",// String?
  "rating": 4.5,               // Double? (0.0–5.0)
  "referrerUserId": null,      // Int?
  "reviewHasSpoilers": false,  // Boolean
  "reviewedAt": "2025-06-20T14:22:00Z",  // String?
  "reviewDocument": { /* ReviewDocument, see §4.11 */ },  // nullable; null/omit when blank
  "journals": [ /* ReadingJournal */ ]   // List, may be empty
}
```

### 4.7 `UserBookRead`

```jsonc
{
  "id": 54321,           // Int, required (needed for progress updates)
  "currentPage": 348,    // Int? (progress_pages)
  "currentSeconds": null,// Int? (progress_seconds, audiobooks)
  "progress": 1.0,       // Float, 0.0 if null
  "startedAt": "2025-06-01T09:00:00Z",  // String?
  "finishedAt": "2025-06-15T17:45:00Z"  // String?
}
```

### 4.8 `ReadingJournal`

```jsonc
{ "event": "user_book_read_finished", "updatedAt": "2025-06-15T18:30:00Z" }
```

`updatedAt` required; `event` nullable (one of §3.4 events).

### 4.9 `BookList`

```jsonc
{
  "id": 42,            // Int
  "name": "Sci-Fi",    // String
  "slug": "sci-fi",    // String ("" if null). slug == "owned" marks the special Owned list.
  "ranked": false,     // Boolean
  "books": [ /* ListBook */ ]
}
```

### 4.10 `ListBook`

```jsonc
{
  "listBookId": 101,   // Int (list_books row id)
  "listId": 42,        // Int
  "bookId": 5001,      // Int
  "editionId": 2003,   // Int — REQUIRED; rows with null edition_id are dropped by the app
  "position": 0,       // Int?
  "addedAt": "2025-01-15T10:30:00Z"  // String? (created_at)
  // `book` and `edition` may be attached when the adapter hydrates them; otherwise omit/null
}
```

### 4.11 `ReviewDocument` (rich-text review body)

Reviews are a Slate-style rich-text document. The app's normalized form:

```jsonc
{
  "paragraphs": [
    { "runs": [
        { "text": "Great book!", "bold": true,  "italic": false, "spoiler": false },
        { "text": " Recommended.", "bold": false, "italic": false, "spoiler": false }
    ] }
  ]
}
```

See §6.3 for the Hardcover `review_slate` wire format and the read/write asymmetry the adapter must absorb.

### 4.12 `BookReview` + `BookReviewer` (top reviews on a book)

```jsonc
{
  "id": 99999,                     // Int (user_book id)
  "reviewDocument": { /* ReviewDocument */ },  // required, non-blank
  "hasSpoilers": false,
  "rating": 5.0,                   // Double?
  "reviewedAt": "2025-05-20T10:15:00Z",  // String?
  "likesCount": 42,                // Int
  "reviewer": {
    "id": 7890, "username": "reader", "name": "Reader Name", "avatarUrl": "https://…"  // name & avatarUrl nullable
  }
}
```

### 4.13 `UserProfileData`

```jsonc
{
  "profileImageUrl": "https://…",
  "name": "John Doe",
  "username": "johndoe",
  "bio": "Book lover",
  "booksRead": 42,                 // count of user_books with status_id = 3
  "averageRating": 4.2,            // avg rating across rated books
  "totalPagesRead": 12847,         // computed — see §6.4
  "activeReadingDates": ["2025-06-10", "2025-06-11"]  // unique dates from streak journals; streak computed app-side
}
```

---

## 5. Endpoint catalog

Grouped by domain. "GraphQL op" names the operation this replaces. Paths are a recommendation; keep them RESTful and
stable.

### Auth / user

#### `GET /me`  ← `GetUserId`

Returns the authenticated user. Used at startup to validate the API key.

```jsonc
// 200
{ "id": 123 }
```

`401` if the token is invalid.

#### `GET /me/profile`  ← `GetUserProfileData`

Aggregated profile + reading-activity. The adapter must compute `totalPagesRead` (§6.4) and return the de-duplicated set
of active reading dates (§6.4); the app turns those into the streak. Needs the user id from `/me` internally (the
GraphQL query takes `$userId` for the streak-journal filter and uses `me` for the rest).
→ **`UserProfileData`** (§4.13).

### Library (the user's books)

#### `GET /me/books?status=1,2,3`  ← `GetUserBooks`

The user's library. Optional `status` CSV filters by `status_id`; omit for all. Ordered by `updated_at desc`. Each item
is a fully-hydrated `Book` **with** `userBook`, `userBookRead`, nested `book` and `edition` populated.
→ `[ Book ]` (each carrying `userBook` + `userBookRead`).
**Adapter must resolve canonical merges** before returning — see §6.2.

#### `POST /me/books`  ← `MarkBookAsWantToRead` / `MarkBookAsRead`

Add a book to the library. Body:

```jsonc
{
  "bookId": 12345,        // required
  "editionId": 98765,     // optional
  "status": 1             // 1 = Want to Read, 3 = Read (the two insert paths)
  // For status=3 (Read), the adapter sets user_date = today and privacy = Public (1).
}
```

→ `Book` (with the new `userBook`). Backing GraphQL: `insert_user_book` with
`UserBookCreateInput { book_id, edition_id?, status_id, user_date?, privacy_setting_id }`.

#### `PATCH /me/books/{userBookId}`  ← `MarkBookAsReading` / `UpdateBookEdition` / `UpdateUserBookRating` /
`UpdateUserBookReview`

Partial update of a user_book. Accept any subset; apply only present keys (§3.3):

```jsonc
{
  "status": 2,                 // → mark as reading; adapter stamps user_date = today
  "editionId": 98765,          // change selected edition
  "rating": 4.5,               // set/clear rating
  "review": { /* ReviewDocument */ }, "reviewHasSpoilers": true, "reviewedAt": "…",  // set review
  "lastReadDate": "…", "referrerUserId": null, "privacySettingId": 1, "dateAdded": "…"
}
```

→ `Book`. Backing GraphQL: `update_user_book(id, object: UserBookUpdateInput)`.
**Faithful-behavior note:** the current app, when *marking as reading*, re-sends the full prior user-book state (
edition, rating, privacy, spoilers, referrer, reviewedAt, dateAdded) alongside `status_id` and `user_date`. Hardcover's
update is partial, so this is belt-and-suspenders; a clean adapter can accept just the intent (`status: 2`) and forward
only the changed fields. Rating-only and review-only updates already send just their fields.

#### `DELETE /me/books/{userBookId}`  ← `RemoveUserBook`

Remove from library. → `204`. Backing: `delete_user_book(id)`.

#### `PATCH /me/reads/{userBookReadId}`  ← `UpdateReadingProgress`

Log reading progress for a specific read session.

```jsonc
{
  "progressPages": 120,     // pages OR seconds — mutually exclusive (audiobook ⇒ seconds)
  "progressSeconds": null,
  "startedAt": "…", "finishedAt": "…",
  "editionId": 98765
}
```

Rule the app enforces: if `progressSeconds` is present it's an audiobook (pages omitted); else pages (seconds omitted).
→ `Book` (re-derived from the updated `user_book`). Backing:
`update_user_book_read(id, object: DatesReadInput { progress_pages?, progress_seconds?, started_at, finished_at, edition_id })`.

#### `POST /me/editions/{editionId}/owned`  ← `MarkEditionAsOwned`

Mark an edition as owned (adds it to the special "Owned" list server-side).
→ `ListBook`. Backing: `edition_owned(id)`.

### Books & editions (public catalog)

#### `GET /books/{id}`  ← `GetBookById`

Single book detail. **404** if not found. Adapter resolves canonical redirect (§6.2): if the book has a `canonical` id ≠
its own, refetch the canonical and return that (with `canonicalId` nulled).
→ `Book` (`userBook`/`userBookRead` null).

#### `GET /books?ids=1,2,3`  ← `GetBooksByIds`

Batch book detail. The app chunks ids at **200** per request; the adapter can accept arbitrary counts and chunk
internally. Order is not guaranteed by Hardcover — see §6.2 for why the app re-sorts.
→ `[ Book ]`.

#### `GET /books/{id}/editions`  ← `GetEditionsByBookId`

All editions for a book, ordered by `users_count desc`. Includes edition-level `authors` (contributions).
→ `[ BookEdition ]`.

#### `GET /editions?ids=1,2,3`  ← `GetEditionsByIds`

Batch edition detail (chunked at 200 app-side). → `[ BookEdition ]`.

#### `GET /editions/{editionId}/book-id`  ← `GetBookIdByEditionId`

Resolve an edition to its parent book id.
→ `{ "bookId": 12345 }` (or `404`/`null` if unknown).

#### `GET /editions/by-isbn/{isbn}`  ← `GetEditionByIsbn`

Barcode-scan lookup. The query tries **ISBN-13 first, then ISBN-10** in one round trip; return the first hit.
→ `{ "bookId": 12345, "editionId": 54321 }`, or `404`/`null` if no edition matches.

#### `POST /books`  ← `CreateBook` (`upsert_book`)

Scan-flow: ask Hardcover to ingest a book it doesn't have yet, keyed by an external id. For the ISBN scan path the app
sends:

```jsonc
{ "externalId": "9780743273565", "platformId": 8 }   // platformId 8 = Hardcover ISBN provider; bookId optional
```

The adapter must check the response `errors[]` and fail (4xx/422) if non-empty (§3.1).
→ `{ "bookId": 555, "editionId": 777 }` (`editionId` nullable). Backing returns
`{ errors, book{id,slug,title}, edition{id,title} }`.

### Discovery

#### `GET /search?q=<text>`  ← `GetIdsForQuery` **then** `GetBooksByIds`

Full-text search. Hardcover's `search` returns **ids only** (per_page 25, weighted relevance); the app then hydrates via
`GetBooksByIds` and **re-sorts the hydrated books back into the search-result order** (the batch fetch loses ordering).
The adapter should do both steps and return fully-hydrated, correctly-ordered books.
→ `[ Book ]`.

#### `GET /books/trending?from=<date>&to=<date>&limit=<n>&offset=<n>`  ← `GetTrendingBookIds` **then** `GetBooksByIds`

Same id-only-then-hydrate pattern as search; preserve trending rank order. Books whose id isn't in the returned set (
canonical redirects) sort last.
→ `[ Book ]`.

#### `GET /series/{seriesId}/next?afterPosition=<float>`  ← `GetNextBookInSeries`

The next book in a series after a given (fractional) position; tie-broken by `users_count desc`, limit 1.
→ `Book` or `404`/`null`.

### Reviews

#### `GET /books/{bookId}/reviews`  ← `GetTopBookReviews`

Top **3** reviews for a book (filtered to `has_review = true`, ordered by `likes_count desc`). Blank-body reviews are
dropped by the app.
→ `[ BookReview ]` (§4.12).

### Tags (per-user, per-book)

#### `GET /me/tags?type=Book&id=<bookId>`  ← `FindTagsByUserAndTaggable`

This user's tags on a given book. `type` is always `"Book"`; `id` is the book id (sent as `bigint`). Ordered by tag
category.
→ `[ UserTag ]`:

```jsonc
{ "name": "Epic", "category": "Genre", "count": 1234, "spoiler": false }
```

#### `PUT /me/tags`  ← `SaveTags` (`upsert_tags`)

Replace this user's tag set on a book.

```jsonc
{
  "type": "Book",
  "id": 5001,                         // book id (bigint)
  "tags": [
    { "tag": "Epic", "category": "Genre", "spoiler": false },
    { "tag": "Major twist", "category": "Content Warning", "spoiler": true }
  ]
}
```

Each tag is a `BasicTag { tag, category, spoiler }` — **all three required**; `category` is the API string (§3.4). The
mutation echoes back the saved tags (with `count`).
→ `[ UserTag ]`.

### Lists

#### `GET /me/lists`  ← `GetUserBookLists`

All of the user's lists with their `list_books`. (The GraphQL takes an optional `where` filter; the app passes none.)
Note `slug == "owned"` flags the special Owned list.
→ `[ BookList ]`.

#### `POST /me/lists`  ← `CreateList`

```jsonc
{ "name": "My New List" }
```

Adapter fills the rest the way the app does: `description=""`, `privacy_setting_id=1` (Public), `ranked=false`,
`default_view="card"`, `url=""`.
→ `BookList`.

#### `PATCH /me/lists/{id}`  ← `UpdateList`

Sparse update. Today only `ranked` is changed:

```jsonc
{ "ranked": true }
```

→ `BookList`. (`ListInput` also supports `name`, `description`, `privacy_setting_id`, `default_view`, `url`,
`featured_profile` if you want to expose them.)

#### `POST /me/lists/{id}/books`  ← `CreateListBook`

```jsonc
{ "bookId": 5003, "editionId": 2005 }   // listId comes from the path; position omitted
```

→ `ListBook`.

#### `DELETE /me/list-books/{listBookId}`  ← `RemoveListBook`

Remove a single list_books row. → returns the updated `BookList` (Hardcover returns the parent list).

#### `PUT /me/lists/{id}/order`  ← `UpdateListBookPositions`

Reorder a list. The app sends an ordered array of `listBookId`s and a start position; the adapter performs Hardcover's *
*two-step** reorder (see §6.5):

```jsonc
{ "startPosition": 0, "orderedListBookIds": [101, 102, 103] }
```

→ `204` (or `{ "cleared": n, "applied": m }` if you want affected-row counts).

---

## 6. Cross-cutting behaviors the adapter must replicate

These are the non-obvious bits that currently live in the app. Moving them server-side is what makes the REST contract
clean.

### 6.1 Series position parsing (`positionsInSeries`)

Built from the first `book_series` entry's `details` string (fallback to its numeric `position`):

- `"1"` → `[1.0]`
- `"1-3"` (whole-number range) → `[1.0, 2.0, 3.0]` (expanded)
- `"1.5-2.5"` (fractional range) → `[1.5, 2.5]` (endpoints only, not expanded)
- empty/unparseable → `[position]` if present, else `[]`
- a malformed range where `end < start` falls back to `[position]`/`[]`.

### 6.2 Canonical merges

Hardcover merges duplicate book records; a book can carry a `canonical` id pointing at the surviving record. The app
handles this in three places — the adapter should do the same so clients never see a stale merged record:

- **`GET /books/{id}`**: if `canonical.id` ≠ `id`, refetch the canonical book and return it with `canonicalId = null`.
- **`GET /me/books`**: after loading the library, collect every `canonicalId`, batch-fetch those canonical books, and
  overwrite each merged user-book's *catalog metadata* (title, rating, description, release info, cover, authors,
  counts, series, compilation) with the canonical's — **keeping** the user-specific fields (`userBook`, `userBookRead`).
  Set `canonicalId = null` afterward.
- **`GET /books?ids=…`, `/search`, `/books/trending`**: the by-ids batch fetch can return rows in a different order and
  can return a canonical redirect whose id wasn't requested. The app re-sorts results back into the requested/ranked
  order; ids not in the original set sort last.

### 6.3 Review rich-text (Slate) format & read/write asymmetry

`review_slate` is a Slate document. The wire format is **asymmetric**, which the adapter must absorb so the app only
ever sees the normalized `ReviewDocument` (§4.11):

- **Reading** (Hardcover → app): wrapped — `{ "document": { "children": [ <block>, … ] } }`. Parser also tolerates a
  bare top-level list and recurses into nested `children`.
- **Writing** (app → Hardcover): a **bare list** of paragraph blocks:

```jsonc
[ { "object": "block", "type": "paragraph", "data": {},
    "children": [ { "object": "text", "text": "Hello ", "bold": true },
                  { "object": "text", "text": "world", "spoiler": true } ] } ]
```

Normalization rules: a text run carries `text` + optional `bold`/`italic`/`spoiler` booleans (only `true` flags are
emitted on write; absent = false). Empty paragraphs serialize as a single empty text node. Adjacent runs with identical
marks are merged. Blank documents are treated as "no review" (dropped).

If you return domain-shaped JSON (`ReviewDocument`), the adapter owns this parse/serialize entirely and the app never
touches Slate.

### 6.4 Profile: pages-read & reading streak

For `GET /me/profile`:

- **`totalPagesRead`** = sum over the user's books of: `max(progress_pages)` across that book's `user_book_reads`; **but
  ** for books with `status_id == 3` (Read), credit at least the full book length —
  `max(maxProgressPages, edition.pages ?? book.pages ?? 0)` — so imported "Read" books with no logged progress still
  count.
- **`activeReadingDates`** = the set of unique calendar dates from `reading_journals` filtered to events
  `progress_updated` and `user_book_read_finished`, newest first, **capped at 1000 rows** (longer streaks truncate).
  Parse `action_at` as either a bare date or a full ISO timestamp (convert to UTC date). The app computes the actual
  streak number from this set, so just return the de-duplicated dates.
- `booksRead` = count of user_books with `status_id == 3`; `averageRating` = avg of non-null ratings.

### 6.5 List reorder is two-step

`UpdateListBookPositions` runs as one mutation with two aliased operations, to avoid violating a unique-position
constraint mid-update:

1. **clear**: set `position = null` for the list's rows currently occupying the target position range (
   `clearedPositions` = `startPosition until startPosition + count`).
2. **apply**: `update_list_books_many` with one entry per row — `_set { position: startPosition + index }` where
   `id _eq listBookId` — in the new order.

The adapter exposes the friendly `{ startPosition, orderedListBookIds }` body and builds both steps internally.

---

## 7. Operation → endpoint cross-reference

| GraphQL operation                       | Kind | REST endpoint                     |
|-----------------------------------------|------|-----------------------------------|
| `GetUserId`                             | Q    | `GET /me`                         |
| `GetUserProfileData`                    | Q    | `GET /me/profile`                 |
| `GetUserBooks`                          | Q    | `GET /me/books`                   |
| `GetUserBookLists`                      | Q    | `GET /me/lists`                   |
| `GetBookById`                           | Q    | `GET /books/{id}`                 |
| `GetBooksByIds`                         | Q    | `GET /books?ids=`                 |
| `GetBookIdByEditionId`                  | Q    | `GET /editions/{id}/book-id`      |
| `GetEditionByIsbn`                      | Q    | `GET /editions/by-isbn/{isbn}`    |
| `GetEditionsByBookId`                   | Q    | `GET /books/{id}/editions`        |
| `GetEditionsByIds`                      | Q    | `GET /editions?ids=`              |
| `GetIdsForQuery` (+`GetBooksByIds`)     | Q    | `GET /search?q=`                  |
| `GetTrendingBookIds` (+`GetBooksByIds`) | Q    | `GET /books/trending`             |
| `GetNextBookInSeries`                   | Q    | `GET /series/{id}/next`           |
| `GetTopBookReviews`                     | Q    | `GET /books/{id}/reviews`         |
| `FindTagsByUserAndTaggable`             | Q    | `GET /me/tags`                    |
| `MarkBookAsWantToRead`                  | M    | `POST /me/books` (status 1)       |
| `MarkBookAsRead`                        | M    | `POST /me/books` (status 3)       |
| `MarkBookAsReading`                     | M    | `PATCH /me/books/{id}` (status 2) |
| `UpdateBookEdition`                     | M    | `PATCH /me/books/{id}`            |
| `UpdateUserBookRating`                  | M    | `PATCH /me/books/{id}`            |
| `UpdateUserBookReview`                  | M    | `PATCH /me/books/{id}`            |
| `RemoveUserBook`                        | M    | `DELETE /me/books/{id}`           |
| `UpdateReadingProgress`                 | M    | `PATCH /me/reads/{id}`            |
| `MarkEditionAsOwned`                    | M    | `POST /me/editions/{id}/owned`    |
| `CreateBook`                            | M    | `POST /books`                     |
| `CreateList`                            | M    | `POST /me/lists`                  |
| `UpdateList`                            | M    | `PATCH /me/lists/{id}`            |
| `CreateListBook`                        | M    | `POST /me/lists/{id}/books`       |
| `RemoveListBook`                        | M    | `DELETE /me/list-books/{id}`      |
| `UpdateListBookPositions`               | M    | `PUT /me/lists/{id}/order`        |
| `SaveTags`                              | M    | `PUT /me/tags`                    |

**Offline replay note:** the app also has four `replay*` data-source methods (progress, mark-read, rating, review) used
by an offline write queue. They reuse the same mutations above and add **no new endpoints** — they just re-issue
`PATCH /me/reads/{id}`, `POST /me/books`, and `PATCH /me/books/{id}` when connectivity returns.

---

## 8. Suggested build order

1. **Auth + `GET /me`** — proves the pass-through token works end-to-end.
2. **Read path for the catalog** — `GET /books/{id}`, `/books?ids=`, `/editions*`, with canonical-merge (§6.2) and
   series parsing (§6.1). This unblocks most screens.
3. **Library reads** — `GET /me/books`, `GET /me/profile` (with §6.4), `GET /me/lists`.
4. **Discovery** — `/search`, `/books/trending`, `/series/{id}/next` (the id-then-hydrate flows).
5. **Writes** — user_book create/update/delete, progress, lists, tags, reviews (Slate, §6.3), scan/`POST /books`.

Get the read paths and the four cross-cutting behaviors (canonical merge, id-then-hydrate ordering, Slate asymmetry,
list-reorder two-step) right first — those are where a naive 1:1 translation breaks.
