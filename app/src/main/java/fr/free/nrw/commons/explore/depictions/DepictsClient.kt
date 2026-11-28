package fr.free.nrw.commons.explore.depictions

import android.annotation.SuppressLint
import fr.free.nrw.commons.mwapi.Binding
import fr.free.nrw.commons.mwapi.SparqlResponse
import fr.free.nrw.commons.upload.depicts.DepictsInterface
import fr.free.nrw.commons.upload.structure.depictions.DepictedItem
import fr.free.nrw.commons.upload.structure.depictions.get
import fr.free.nrw.commons.wikidata.WikidataProperties
import fr.free.nrw.commons.wikidata.model.DataValue
import fr.free.nrw.commons.wikidata.model.DepictSearchItem
import fr.free.nrw.commons.wikidata.model.Entities
import fr.free.nrw.commons.wikidata.model.StatementPartial
import io.reactivex.Single
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

// wbgetentities accepts at most 50 ids per request
private const val MAX_ENTITIES_PER_REQUEST = 50

/**
 * Depicts Client to handle custom calls to Commons Wikibase APIs
 */
@Singleton
class DepictsClient
    @Inject
    constructor(
        private val depictsInterface: DepictsInterface,
    ) {
        /**
         * Search for depictions using the search item
         * @return list of depicted items
         */
        fun searchForDepictions(
            query: String?,
            limit: Int,
            offset: Int,
        ): Single<List<DepictedItem>> {
            val language = Locale.getDefault().language
            val labelMatches =
                depictsInterface
                    .searchForDepicts(query, "$limit", language, language, "$offset")
                    .map { it.search.map(DepictSearchItem::id) }
            // Only the first page gets the full-text results, so later pages don't repeat them
            val ids =
                if (offset == 0) {
                    labelMatches.zipWith(textMatches(query, limit)) { labels, text ->
                        (labels + text).distinct().take(MAX_ENTITIES_PER_REQUEST)
                    }
                } else {
                    labelMatches
                }
            return ids
                .map { it.joinToString("|") }
                .mapToDepictions()
        }

        /**
         * Items found by the Wikidata website's full-text search, for searches like "Paris Texas"
         * that match no label from its start. If this search fails, the label matches still show.
         */
        private fun textMatches(
            query: String?,
            limit: Int,
        ): Single<List<String>> =
            depictsInterface
                .searchForDepictsByText(query, "$limit")
                .map { it.ids() }
                .onErrorReturnItem(emptyList())

        fun getEntities(ids: String): Single<Entities> = depictsInterface.getEntities(ids)

        fun toDepictions(sparqlResponse: Single<SparqlResponse>): Single<List<DepictedItem>> =
            sparqlResponse
                .map {
                    it.results.bindings.joinToString("|", transform = Binding::id)
                }.mapToDepictions()

        /**
         * Fetches Entities from ids ex. "Q1233|Q546" and converts them into DepictedItem
         */
        @SuppressLint("CheckResult")
        private fun Single<String>.mapToDepictions() =
            flatMap(::getEntities)
                .map { entities ->
                    entities.getEntityMap().values.map { entity ->
                        mapToDepictItem(entity)
                    }
                }

        /**
         * Convert different entities into DepictedItem
         */
        private fun mapToDepictItem(entity: Entities.Entity): DepictedItem =
            if (entity.descriptions().byLanguageOrFirstOrEmpty() == "") {
                val instanceOfIDs =
                    entity[WikidataProperties.INSTANCE_OF]
                        .toIds()
                if (instanceOfIDs.isNotEmpty()) {
                    val entities: Entities = getEntities(instanceOfIDs[0]).blockingGet()
                    val nameAsDescription =
                        entities
                            .getEntityMap()
                            .values
                            .first()
                            .labels()
                            .byLanguageOrFirstOrEmpty()
                    DepictedItem(
                        entity,
                        entity.labels().byLanguageOrFirstOrEmpty(),
                        nameAsDescription,
                    )
                } else {
                    DepictedItem(
                        entity,
                        entity.labels().byLanguageOrFirstOrEmpty(),
                        "",
                    )
                }
            } else {
                DepictedItem(
                    entity,
                    entity.labels().byLanguageOrFirstOrEmpty(),
                    entity.descriptions().byLanguageOrFirstOrEmpty(),
                )
            }

        /**
         * Tries to get Entities.Label by default language from the map.
         * If that returns null, Tries to retrieve first element from the map.
         * If that still returns null, function returns "".
         */
        private fun Map<String, Entities.Label>.byLanguageOrFirstOrEmpty() =
            let {
                it[Locale.getDefault().language] ?: it.values.firstOrNull()
            }?.value() ?: ""

        /**
         * returns list of id ex. "Q2323" from Statement_partial
         */
        private fun List<StatementPartial>?.toIds(): List<String> =
            this
                ?.map { it.mainSnak.dataValue }
                ?.filterIsInstance<DataValue.EntityId>()
                ?.map { it.value.id }
                ?: emptyList()
    }
