package nl.rhaydus.core.mapping

import nl.rhaydus.core.model.*
import nl.rhaydus.graphql.fragment.*

fun BookDetailFragment.toBook(): Book {
    val listFragment: BookListFragment = this.bookListFragment
    val bookAuthors = listFragment.authors()

    val defaultEdition = default_cover_edition
        ?.editionFragment
        ?.toBookEdition(authors = bookAuthors)
        ?.copy(bookId = listFragment.id)

    val editions: List<BookEdition> = listOfNotNull(defaultEdition)

    return Book(
        id = listFragment.id,
        canonicalId = listFragment.canonicalIdOrNull(),
        title = listFragment.title ?: "",
        editions = editions,
        defaultEdition = defaultEdition,
        rating = roundRating(listFragment.rating) ?: 0.0,
        headline = listFragment.headline ?: "",
        description = listFragment.description ?: "",
        releaseYear = listFragment.release_year ?: -1,
        releaseDate = listFragment.release_date,
        coverUrl = listFragment.image?.url ?: "",
        authors = bookAuthors,
        usersCount = users_count,
        ratingsCount = listFragment.ratings_count,
        bookSeries = listFragment.bookSeries(),
        positionsInSeries = listFragment.positionsInSeries(),
        isCompilation = listFragment.compilation,
        tags = listFragment.tags(),
    )
}

internal fun EditionFragment.toBookEdition(
    authors: List<Author> = emptyList(),
): BookEdition = BookEdition(
    id = id,
    canonicalId = canonical_id,
    title = title,
    url = image?.url ?: fallbackImages.firstOrNull()?.url,
    publisher = publisher?.name,
    pages = pages,
    audioSeconds = audio_seconds,
    authors = authors,
    isbn10 = isbn_10,
    isbn13 = isbn_13,
    releaseYear = release_year ?: -1,
    releaseDate = release_date,
    format = edition_format ?: "",
    readingFormatId = reading_format_id,
    bookId = book_id,
)

 fun EditionDetailFragment.toBookEdition(): BookEdition {
    val authors = contributions.mapNotNull { contribution ->
        val author = contribution.author ?: return@mapNotNull null

        Author(
            name = author.name,
            id = author.id,
        )
    }
    return editionFragment.toBookEdition(authors = authors)
}

private fun BookListFragment.canonicalIdOrNull(): Int? {
    val canonicalId = canonical?.id ?: return null

    return canonicalId.takeIf { it != id }
}

private fun BookSeriesFragment.toBookSeries(): BookSeries? {
    val series = series ?: return null

    return BookSeries(
        id = series.id,
        name = series.name,
        amountOfBooks = series.primary_books_count ?: 0,
    )
}

private fun BookListFragment.bookSeries(): BookSeries? = book_series.firstOrNull()?.bookSeriesFragment?.toBookSeries()

private fun BookListFragment.positionsInSeries(): List<Double> {
    val first = book_series.firstOrNull()?.bookSeriesFragment ?: return emptyList()

    return parsePositionDetails(
        details = first.details,
        fallback = first.position,
    )
}

private fun parsePositionDetails(
    details: String?,
    fallback: Double?,
): List<Double> {
    val parsed = details?.trim()?.takeIf { it.isNotEmpty() }?.let { raw ->
        val rangeParts = raw.split("-").map { it.trim() }

        when (rangeParts.size) {
            1 -> rangeParts[0].toDoubleOrNull()?.let { listOf(it) }
            2 -> {
                val start = rangeParts[0].toDoubleOrNull()
                val end = rangeParts[1].toDoubleOrNull()

                if (start == null || end == null || end < start) {
                    null
                } else if (start % 1.0 == 0.0 && end % 1.0 == 0.0) {
                    (start.toInt()..end.toInt()).map { it.toDouble() }
                } else {
                    listOf(start, end)
                }
            }

            else -> null
        }
    }

    return parsed ?: listOfNotNull(fallback)
}

private fun BookListFragment.tags(): List<Tag> = taggable_counts.mapNotNull { taggableCount ->
    val tag = taggableCount.bookTagFragment.tag ?: return@mapNotNull null

    Tag(
        id = tag.id,
        name = tag.tag,
        category = tag.tag_category.category ?: "",
        count = taggableCount.bookTagFragment.count,
    )
}

private fun BookListFragment.authors(): List<Author> = contributions.mapNotNull { contribution ->
    val author = contribution.author ?: return@mapNotNull null

    Author(
        name = author.name,
        id = author.id,
    )
}