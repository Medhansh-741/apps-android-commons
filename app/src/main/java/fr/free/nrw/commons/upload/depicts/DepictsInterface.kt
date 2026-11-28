package fr.free.nrw.commons.upload.depicts

import fr.free.nrw.commons.wikidata.model.DepictFullTextSearchResponse
import fr.free.nrw.commons.wikidata.model.DepictSearchResponse
import fr.free.nrw.commons.wikidata.model.Entities
import io.reactivex.Single
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Manges retrofit calls for Searching of depicts from DepictsFragment
 */
interface DepictsInterface {
    /**
     * Search for depictions using the wbsearchentities API
     * @param query search for depictions based on user query
     * @param limit number of depictions to be retrieved
     * @param language current locale of the phone
     * @param uselang current locale of the phone
     * @param offset number of depictions already fetched useful in implementing pagination
     */
    @GET("/w/api.php?action=wbsearchentities&format=json&type=item&uselang=en")
    fun searchForDepicts(
        @Query("search") query: String?,
        @Query("limit") limit: String?,
        @Query("language") language: String?,
        @Query("uselang") uselang: String?,
        @Query("continue") offset: String?,
    ): Single<DepictSearchResponse>

    /**
     * Search for depictions with the full-text search used by the Wikidata website, which also
     * finds items whose words are not at the start of their label
     * @param query search for depictions based on user query
     * @param limit number of depictions to be retrieved
     */
    @GET("/w/api.php?action=query&format=json&list=search&srnamespace=0")
    fun searchForDepictsByText(
        @Query("srsearch") query: String?,
        @Query("srlimit") limit: String?,
    ): Single<DepictFullTextSearchResponse>

    @GET("/w/api.php?format=json&action=wbgetentities")
    fun getEntities(
        @Query("ids") ids: String?,
    ): Single<Entities>

    /**
     * Fetches only the labels of the given entities in one language, falling back
     * to other languages (e.g. English) when no label exists in that language.
     * @param ids pipe-separated Q-IDs, at most 50
     * @param language language code of the labels to fetch
     */
    @GET("/w/api.php?format=json&action=wbgetentities&props=labels&languagefallback=1")
    fun getLabels(
        @Query("ids") ids: String,
        @Query("languages") language: String,
    ): Single<Entities>
}
