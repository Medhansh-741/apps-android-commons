package fr.free.nrw.commons.explore.depictions

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import depictSearchItem
import entity
import fr.free.nrw.commons.mwapi.Binding
import fr.free.nrw.commons.mwapi.Result
import fr.free.nrw.commons.mwapi.SparqlResponse
import fr.free.nrw.commons.upload.depicts.DepictsInterface
import fr.free.nrw.commons.upload.structure.depictions.DepictedItem
import fr.free.nrw.commons.wikidata.model.DataValue
import fr.free.nrw.commons.wikidata.model.DepictFullTextSearchResponse
import fr.free.nrw.commons.wikidata.model.DepictSearchResponse
import fr.free.nrw.commons.wikidata.model.Entities
import fr.free.nrw.commons.wikidata.model.SnakPartial
import fr.free.nrw.commons.wikidata.model.StatementPartial
import fr.free.nrw.commons.wikidata.model.WikiBaseEntityValue
import io.reactivex.Single
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import java.io.IOException
import java.lang.reflect.Method

class DepictsClientTest {
    @Mock
    private lateinit var depictsInterface: DepictsInterface
    private lateinit var depictsClient: DepictsClient

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        depictsClient = DepictsClient(depictsInterface)
    }

    @Test
    fun searchForDepictions() {
        val depictSearchResponse = mock<DepictSearchResponse>()
        whenever(depictsInterface.searchForDepicts("query", "1", "en", "en", "0"))
            .thenReturn(Single.just(depictSearchResponse))
        whenever(depictSearchResponse.search).thenReturn(listOf(depictSearchItem("1"), depictSearchItem("2")))
        whenever(depictsInterface.searchForDepictsByText("query", "1"))
            .thenReturn(Single.just(DepictFullTextSearchResponse(null)))
        val entities = mock<Entities>()
        whenever(depictsInterface.getEntities("1|2")).thenReturn(Single.just(entities))
        whenever(entities.getEntityMap()).thenReturn(emptyMap())
        depictsClient
            .searchForDepictions("query", 1, 0)
            .test()
            .assertValue(emptyList())
    }

    @Test
    fun `searchForDepictions adds full-text matches after the label matches`() {
        givenLabelMatches("Q1", "Q2")
        whenever(depictsInterface.searchForDepictsByText("Paris Texas", "25"))
            .thenReturn(Single.just(textMatches("Q2", "Q3")))
        val entities = mock<Entities>()
        whenever(depictsInterface.getEntities("Q1|Q2|Q3")).thenReturn(Single.just(entities))
        whenever(entities.getEntityMap()).thenReturn(emptyMap())

        depictsClient.searchForDepictions("Paris Texas", 25, 0).test().assertNoErrors()

        verify(depictsInterface).getEntities("Q1|Q2|Q3")
    }

    @Test
    fun `searchForDepictions keeps the label matches when the full-text search fails`() {
        givenLabelMatches("Q1", "Q2")
        whenever(depictsInterface.searchForDepictsByText("Paris Texas", "25"))
            .thenReturn(Single.error(IOException("no network")))
        val entities = mock<Entities>()
        whenever(depictsInterface.getEntities("Q1|Q2")).thenReturn(Single.just(entities))
        whenever(entities.getEntityMap()).thenReturn(emptyMap())

        depictsClient.searchForDepictions("Paris Texas", 25, 0).test().assertNoErrors()

        verify(depictsInterface).getEntities("Q1|Q2")
    }

    @Test
    fun `searchForDepictions asks for at most 50 items at once`() {
        val labels = (1..40).map { "Q$it" }
        givenLabelMatches(*labels.toTypedArray(), limit = "50")
        whenever(depictsInterface.searchForDepictsByText("Paris Texas", "50"))
            .thenReturn(Single.just(textMatches(*(41..60).map { "Q$it" }.toTypedArray())))
        val ids = (1..50).joinToString("|") { "Q$it" }
        val entities = mock<Entities>()
        whenever(depictsInterface.getEntities(ids)).thenReturn(Single.just(entities))
        whenever(entities.getEntityMap()).thenReturn(emptyMap())

        depictsClient.searchForDepictions("Paris Texas", 50, 0).test().assertNoErrors()

        verify(depictsInterface).getEntities(ids)
    }

    @Test
    fun `searchForDepictions skips the full-text search after the first page`() {
        val response = mock<DepictSearchResponse>()
        whenever(depictsInterface.searchForDepicts("Paris Texas", "25", "en", "en", "25"))
            .thenReturn(Single.just(response))
        whenever(response.search).thenReturn(listOf(depictSearchItem("Q26")))
        val entities = mock<Entities>()
        whenever(depictsInterface.getEntities("Q26")).thenReturn(Single.just(entities))
        whenever(entities.getEntityMap()).thenReturn(emptyMap())

        depictsClient.searchForDepictions("Paris Texas", 25, 25).test().assertNoErrors()

        verify(depictsInterface, never()).searchForDepictsByText(any(), any())
    }

    private fun givenLabelMatches(
        vararg ids: String,
        limit: String = "25",
    ) {
        val response = mock<DepictSearchResponse>()
        whenever(depictsInterface.searchForDepicts("Paris Texas", limit, "en", "en", "0"))
            .thenReturn(Single.just(response))
        whenever(response.search).thenReturn(ids.map { depictSearchItem(it) })
    }

    private fun textMatches(vararg ids: String) =
        DepictFullTextSearchResponse(
            DepictFullTextSearchResponse.Query(ids.map { DepictFullTextSearchResponse.Result(it) }),
        )

    @Test
    fun getEntities() {
        val entities = mock<Entities>()
        whenever(depictsInterface.getEntities("ids")).thenReturn(Single.just(entities))
        depictsClient.getEntities("ids").test().assertValue(entities)
    }

    @Test
    fun `Test toDepictions when description is empty`() {
        val sparqlResponse = mock<SparqlResponse>()
        val result = mock<Result>()
        whenever(sparqlResponse.results).thenReturn(result)
        val binding1 = mock<Binding>()
        val binding2 = mock<Binding>()
        whenever(result.bindings).thenReturn(listOf(binding1, binding2))
        whenever(binding1.id).thenReturn("1")
        whenever(binding2.id).thenReturn("2")
        val entities = mock<Entities>()
        val entity = mock<Entities.Entity>()
        val statementPartial = mock<StatementPartial>()
        whenever(depictsInterface.getEntities("1|2")).thenReturn(Single.just(entities))
        whenever(entities.getEntityMap()).thenReturn(mapOf("en" to entity))
        whenever(entity.statements).thenReturn(mapOf("P31" to listOf(statementPartial)))
        whenever(statementPartial.mainSnak).thenReturn(
            SnakPartial(
                "test",
                "P31",
                DataValue.EntityId(
                    WikiBaseEntityValue("wikibase-entityid", "Q10", 10L),
                ),
            ),
        )
        whenever(depictsInterface.getEntities("Q10")).thenReturn(Single.just(entities))
        whenever(entity.id()).thenReturn("Q10")
        depictsClient
            .toDepictions(Single.just(sparqlResponse))
            .test()
            .assertValue(
                listOf(
                    DepictedItem(
                        "",
                        "",
                        null,
                        listOf("Q10"),
                        emptyList(),
                        false,
                        "Q10",
                    ),
                ),
            )
    }

    @Test
    fun `Test toDepictions when description is not empty`() {
        val sparqlResponse = mock<SparqlResponse>()
        val result = mock<Result>()
        whenever(sparqlResponse.results).thenReturn(result)
        val binding1 = mock<Binding>()
        val binding2 = mock<Binding>()
        whenever(result.bindings).thenReturn(listOf(binding1, binding2))
        whenever(binding1.id).thenReturn("1")
        whenever(binding2.id).thenReturn("2")
        val entities = mock<Entities>()
        val entity = mock<Entities.Entity>()
        whenever(depictsInterface.getEntities("1|2")).thenReturn(Single.just(entities))
        whenever(entities.getEntityMap()).thenReturn(mapOf("en" to entity))
        whenever(entity.descriptions()).thenReturn(
            mapOf(
                "en" to
                    Entities.Label("en", "Test description"),
            ),
        )
        whenever(entity.id()).thenReturn("Q10")
        depictsClient
            .toDepictions(Single.just(sparqlResponse))
            .test()
            .assertValue(
                listOf(
                    DepictedItem(
                        "",
                        "",
                        null,
                        listOf("Q10"),
                        emptyList(),
                        false,
                        "Q10",
                    ),
                ),
            )
    }

    @Test
    fun `Test mapToDepictItem when description is not empty`() {
        val method: Method =
            DepictsClient::class.java.getDeclaredMethod(
                "mapToDepictItem",
                Entities.Entity::class.java,
            )
        method.isAccessible = true
        method.invoke(depictsClient, entity(descriptions = mapOf("en" to "Test")))
    }

    @Test
    fun `Test mapToDepictItem when description is empty and P31 doesn't exists`() {
        val method: Method =
            DepictsClient::class.java.getDeclaredMethod(
                "mapToDepictItem",
                Entities.Entity::class.java,
            )
        method.isAccessible = true
        method.invoke(depictsClient, entity())
    }

    @Test
    fun `Test mapToDepictItem when description is empty and P31 exists`() {
        val entities = mock<Entities>()
        val entity = mock<Entities.Entity>()
        val statementPartial = mock<StatementPartial>()
        whenever(entity.statements).thenReturn(mapOf("P31" to listOf(statementPartial)))
        whenever(statementPartial.mainSnak).thenReturn(
            SnakPartial(
                "test",
                "P31",
                DataValue.EntityId(
                    WikiBaseEntityValue("wikibase-entityid", "Q10", 10L),
                ),
            ),
        )
        whenever(depictsInterface.getEntities("Q10")).thenReturn(Single.just(entities))
        whenever(entities.getEntityMap())
            .thenReturn(mapOf("test" to entity))
        whenever(entity.id()).thenReturn("Q10")
        val method: Method =
            DepictsClient::class.java.getDeclaredMethod(
                "mapToDepictItem",
                Entities.Entity::class.java,
            )
        method.isAccessible = true
        method.invoke(depictsClient, entity)
    }
}
