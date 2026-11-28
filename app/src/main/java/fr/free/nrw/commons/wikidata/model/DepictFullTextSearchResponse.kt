package fr.free.nrw.commons.wikidata.model

/**
 * Model class for the full-text search (list=search) used to find more depictions.
 * Only the item ids are needed, they are in the title of each result.
 */
class DepictFullTextSearchResponse(
    val query: Query?,
) {
    class Query(
        val search: List<Result>?,
    )

    class Result(
        val title: String,
    )

    fun ids(): List<String> = query?.search.orEmpty().map { it.title }
}
