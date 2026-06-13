# Folder Structure — Softcover ↔ Hardcover Adapter

The layout that the codebase already follows, projected forward to all ~30
endpoints in [`Adapter_endpoints.md`](./Adapter_endpoints.md). The principle:
**one package per feature domain**, with shared cross-cutting code under `core/`,
the GraphQL transport isolated in `hardcover/`, and Ktor wiring in `plugins/`.

Adding an endpoint never means inventing a new shape — you drop a `.graphql` op,
extend a feature package's `DataSource`, and wire one `Route.xxxRoutes(...)` call.

---

## Top-level layout

```
softcover_backend/
├── Adapter_endpoints.md          # API spec (source of truth for shapes)
├── Implementation_plan.md        # build order, step by step
├── Folder_structure.md           # this file
├── build.gradle.kts              # incl. Apollo codegen + custom scalar mapping
├── src/
│   ├── main/
│   │   ├── graphql/              # Hardcover operations + shared fragments → codegen
│   │   ├── kotlin/
│   │   │   ├── Application.kt     # entry point; installs plugins, builds DI
│   │   │   ├── core/             # cross-cutting, domain-agnostic code
│   │   │   ├── feature/          # one package per domain (the bulk of the app)
│   │   │   ├── hardcover/        # thin GraphQL transport (auth + error→exception)
│   │   │   └── plugins/          # Ktor plugin config + route/DI wiring
│   │   └── resources/            # application.conf, logback, etc.
│   └── test/
│       └── kotlin/               # mirrors main package layout
└── ...
```

---

## `src/main/graphql/` — GraphQL operations

One `.graphql` file per Hardcover operation, plus reusable fragments. Apollo
codegen turns these into `nl.rhaydus.graphql.*` classes. Fragments are the unit
of reuse — define a field set once (e.g. `BookDetailFragment`) and reference it
from every op that returns that shape.

```
graphql/
├── BookDetailFragment.graphql        ┐
├── EditionFragment.graphql           │ shared field sets — reuse across ops
├── BookSeriesFragment.graphql        │
├── BookListFragment.graphql          │
├── BookTagFragment.graphql           ┘
├── me.graphql                        # GetUserId
├── GetBookById.graphql               # one file per operation…
├── GetBooksByIds.graphql
│   …
│   # to add per remaining steps:
├── GetBookIdByEditionId.graphql      # Step 4
├── GetEditionsByBookId.graphql       # Step 5
├── GetEditionsByIds.graphql          # Step 6
├── GetEditionByIsbn.graphql          # Step 7
├── GetUserBooks.graphql              # Step 8
├── UserBookFragment.graphql          # (+ fragment for userBook/userBookRead)
├── GetUserBookLists.graphql          # Step 9
├── GetUserProfileData.graphql        # Step 10
├── GetIdsForQuery.graphql            # Step 11
├── GetTrendingBookIds.graphql        # Step 12
├── GetNextBookInSeries.graphql       # Step 13
├── InsertUserBook.graphql            # Step 14
├── UpdateUserBook.graphql            # Step 15
├── UpdateUserBookRead.graphql        # Step 16
├── DeleteUserBook.graphql            # Step 17
├── UpsertBook.graphql                # Step 18
├── EditionOwned.graphql              # Step 19
├── FindTagsByUserAndTaggable.graphql # Step 20
├── UpsertTags.graphql                # Step 20
├── CreateList.graphql                # Step 21
├── UpdateList.graphql                # Step 21
├── CreateListBook.graphql            # Step 21
├── RemoveListBook.graphql            # Step 21
└── UpdateListBookPositions.graphql   # Step 21
```

---

## `core/` — cross-cutting, domain-agnostic code

Nothing here knows about routes or a specific feature. Two sub-packages today;
they grow only when a shape/helper is shared by **more than one** feature.

```
core/
├── model/                  # @Serializable domain models (the JSON the app sees)
│   ├── Book.kt             # §4.1
│   ├── BookEdition.kt      # §4.2
│   ├── Author.kt           # §4.3
│   ├── BookSeries.kt       # §4.4
│   ├── Tag.kt              # §4.5
│   ├── ReadingFormat.kt    # reading_format_id enum → name
│   ├── ErrorResponse.kt    # { "error": "…" } body
│   ├── HardcoverException.kt   # sealed: upstream failures → 422/401/502
│   ├── SoftcoverException.kt   # sealed: adapter decisions → 404/401
│   ├── UserPrincipal.kt    # token + userId, from auth
│   │   # to add as steps need them:
│   ├── UserBook.kt         # §4.6        (Step 8)
│   ├── UserBookRead.kt     # §4.7        (Step 8)
│   ├── ReadingJournal.kt   # §4.8        (Step 8/10)
│   ├── BookList.kt         # §4.9        (Step 9)
│   ├── ListBook.kt         # §4.10       (Step 9)
│   ├── ReviewDocument.kt   # §4.11       (Step 15, read path Step 8)
│   ├── UserProfileData.kt  # §4.13       (Step 10)
│   └── UserTag.kt          # GET /me/tags shape (Step 20)
│
└── mapping/                # GraphQL fragment → core/model translators + helpers
    ├── Scalars.kt          # roundRating, releaseYearOrSentinel(-1), passthroughDate
    ├── SeriesPosition.kt   # §6.1 parsePositionDetails
    ├── BookMapper.kt       # BookDetailFragment → Book (toBook())
    │   # to add:
    ├── EditionMapper.kt    # EditionFragment → BookEdition (Step 5/6)
    ├── UserBookMapper.kt   # userBook/userBookRead → §4.6/4.7 (Step 8)
    ├── CanonicalMerge.kt   # §6.2 library-variant merge helper (Step 8)
    ├── ReviewSlate.kt      # §6.3 Slate parse/serialize asymmetry (Step 15)
    ├── ListMapper.kt       # §4.9/4.10 (Step 9)
    └── ProfileMapper.kt    # §6.4 pages-read + activeReadingDates (Step 10)
```

**Rule of thumb:** a mapper lives next to the model it produces, in `core/mapping`,
because models are shared across features. A mapper used by exactly one feature
*could* live in that feature package, but keeping all of them here keeps the
catalog/library merge logic discoverable.

---

## `feature/<domain>/` — one package per domain

This is where most new code lands. Each domain package holds the same four kinds
of file (the `Settings` feature also shows the DB-backed variant with a `Table`):

| File                  | Role                                                                 |
|-----------------------|----------------------------------------------------------------------|
| `XDataSource.kt`      | interface — the suspend ops the routes call                          |
| `XDataSourceImpl.kt`  | impl — calls `HardcoverClient`, maps via `core/mapping`, caches      |
| `XRoutes.kt`          | `fun Route.xRoutes(dataSource)` — parses params, calls DS, responds  |
| `XDataSourceKey.kt`   | `AttributeKey` + `Application.xDataSource` accessor (DI, optional)    |

Recommended domain split for the full endpoint set:

```
feature/
├── user/                   # GET /me, GET /me/profile
│   ├── HardcoverUser.kt
│   ├── UserDataSource.kt
│   ├── UserDataSourceImpl.kt
│   ├── UserDataSourceKey.kt
│   └── MeRoutes.kt         # meRoutes() + profileRoutes()  (Step 10)
│
├── books/                  # public catalog: /books*, /search, /trending, /series
│   ├── BookDataSource.kt   # getBookById, getBooksByIds, + search/trending/next
│   ├── BookDataSourceImpl.kt
│   └── BookRoutes.kt
│
├── editions/               # /editions*, /editions/by-isbn, /editions/{id}/book-id
│   ├── EditionDataSource.kt        # Steps 4–7
│   ├── EditionDataSourceImpl.kt
│   └── EditionRoutes.kt
│
├── library/                # the user's own books: /me/books, /me/reads
│   ├── LibraryDataSource.kt        # Steps 8, 14–17
│   ├── LibraryDataSourceImpl.kt    # uses CanonicalMerge (library variant)
│   └── LibraryRoutes.kt
│
├── lists/                  # /me/lists, /me/list-books, /me/editions/{id}/owned
│   ├── ListDataSource.kt           # Steps 9, 19, 21
│   ├── ListDataSourceImpl.kt       # owns §6.5 two-step reorder
│   └── ListRoutes.kt
│
├── tags/                   # /me/tags
│   ├── TagDataSource.kt            # Step 20
│   ├── TagDataSourceImpl.kt
│   └── TagRoutes.kt
│
├── reviews/                # /books/{id}/reviews
│   ├── ReviewDataSource.kt         # GetTopBookReviews
│   ├── ReviewDataSourceImpl.kt
│   └── ReviewRoutes.kt
│
└── settings/               # existing local app settings (DB-backed example)
    ├── AppTheme.kt
    ├── UserSettings.kt
    ├── SettingsTable.kt            # Exposed table definition
    ├── SettingsRepository.kt
    ├── SettingsRepositoryImpl.kt
    └── SettingsRoutes.kt
```

**Why these boundaries**

- `books` vs `editions` — both public catalog, but editions have their own id
  space and isbn/book-id lookups; splitting keeps each `DataSource` focused.
  Search/trending/series-next return `Book`, so they belong with `books` and
  reuse its batch-fetch-and-resort path.
- `library` groups everything keyed to *the signed-in user's* `user_book`
  (reads + the create/update/delete writes), because they share the canonical
  library-merge and the `userBook`/`userBookRead` hydration.
- `lists` owns the Owned-list write (`/me/editions/{id}/owned`) since that's a
  list operation under the hood (`edition_owned`).

If a domain's `Impl` grows unwieldy, split *within* the package (e.g.
`LibraryReadDataSource` / `LibraryWriteDataSource`) before adding a new top-level
package — keep one package per REST domain.

---

## `hardcover/` — GraphQL transport

Stays thin: it holds one `ApolloClient`, attaches the bearer token, executes
queries/mutations, and converts Apollo failures + populated `errors[]` into the
typed `HardcoverException`s. **It contains no domain knowledge** — feature
`DataSource`s pass in generated operations and get back generated data.

```
hardcover/
├── HardcoverClient.kt      # query/mutate (+ Catching variants), error→exception
└── HardcoverClientKey.kt   # AttributeKey + Application.hardcoverClient accessor
```

Do not add per-op methods here. New operations are just generated classes handed
to `client.query(...)` / `client.mutate(...)` from a feature `DataSourceImpl`.

---

## `plugins/` — Ktor configuration & wiring

One file per installed plugin, plus `Routing.kt` as the single composition root
where data sources are constructed and routes registered under
`authenticate("external")`.

```
plugins/
├── Authentication.kt   # "external" bearer scheme → UserPrincipal(token, userId)
├── Database.kt         # Exposed/HikariCP datasource
├── NetworkClient.kt    # builds ApolloClient + setHardcoverClient(...)
├── Resources.kt
├── Serialization.kt    # kotlinx.serialization JSON config
├── StatusPages.kt      # maps HardcoverException/SoftcoverException → HTTP status
└── Routing.kt          # constructs DataSources, wires xxxRoutes(...) — edit per step
```

**Wiring an endpoint** (the only edit outside the feature package):

```kotlin
// in configureRouting()
val libraryService = LibraryDataSourceImpl(client = hardcoverClient)

routing {
    authenticate("external") {
        bookRoutes(bookDataSource = bookService)
        libraryRoutes(dataSource = libraryService)   // ← add this line
    }
}
```

`StatusPages.kt` rarely changes: both exception hierarchies are sealed, so adding
an endpoint that throws an existing exception type needs no new handler.

---

## `src/test/kotlin/` — tests mirror main

Same package path as the code under test. Pure helpers get fast unit tests
(`ScalarsTest`, `SeriesPositionTest`); mappers and the canonical/Slate/reorder
logic are the highest-value additions as those steps land.

```
test/kotlin/
└── core/mapping/
    ├── ScalarsTest.kt
    ├── SeriesPositionTest.kt
    │   # to add alongside the steps that introduce them:
    ├── ReviewSlateTest.kt      # §6.3 read/write asymmetry, run-merging
    ├── CanonicalMergeTest.kt   # §6.2 library variant keeps user fields
    └── ProfileMapperTest.kt    # §6.4 pages-read + de-duped dates
```

---

## Where each new endpoint touches the tree (summary)

For nearly every step you create/modify exactly these:

1. `src/main/graphql/<Op>.graphql` — the operation (+ a fragment if shared).
2. `core/model/<Model>.kt` — only if the step introduces a new shape.
3. `core/mapping/<Mapper>.kt` — fragment → model translation.
4. `feature/<domain>/<Domain>DataSource(.kt + Impl.kt)` — a new suspend op.
5. `feature/<domain>/<Domain>Routes.kt` — the REST route.
6. `plugins/Routing.kt` — one line to register the route.

Steps that don't introduce a new domain (3, 4, 11–13, the write steps) skip #2
and reuse the existing package — confirming the layout holds for all 21 steps.
