package org.vitrivr.engine.database.weaviate

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging.logger
import io.weaviate.client.WeaviateClient
import io.weaviate.client.v1.misc.model.InvertedIndexConfig
import io.weaviate.client.v1.misc.model.VectorIndexConfig
import io.weaviate.client.v1.misc.model.PQConfig
import io.weaviate.client.v1.misc.model.ShardingConfig
import io.weaviate.client.v1.schema.model.DataType
import io.weaviate.client.v1.schema.model.Property
import io.weaviate.client.v1.schema.model.WeaviateClass
import org.vitrivr.engine.core.database.AbstractConnection
import org.vitrivr.engine.core.database.retrievable.RetrievableInitializer
import org.vitrivr.engine.core.database.retrievable.RetrievableReader
import org.vitrivr.engine.core.database.retrievable.RetrievableWriter


/** Defines [KLogger] of the class. */
internal val LOGGER: KLogger = logger("org.vitrivr.engine.database.weaviate.WeaviateConnection")

/**
 * A [WeaviateConnection] to connect to a Weaviate instance.
 *
 * @author Nico Bachmann
 * @version 1.1.0
 */
class WeaviateConnection(provider: WeaviateConnectionProvider, className: String, internal val client: WeaviateClient, namedVectors: List<String>) : AbstractConnection(className, provider) {

    init {
        Constants.setCollectionName(className)
        /* Check if we need to create the collection ourselves */
        val exists = client.schema().exists().withClassName(className).run()
        if (exists.hasErrors()) {
            LOGGER.error { "Error checking for existence of collection '$className': ${exists.error}" }
        }
        if (exists.result) {
            LOGGER.info { "Found and loaded existing collection: $className" }
        } else {
            /* Index for the retrievables in the collection */
            val invertedIndexConfig = InvertedIndexConfig.builder()
                .indexNullState(true)
                .indexPropertyLength(true)
                .build()

            /* IndexConfig for named Vectors */
            val hnswConfigBuilder = VectorIndexConfig.builder()
                .distance(DISTANCE)
                .efConstruction(128)
                .maxConnections(64)
                .ef(64)

            val pqConfigBuilder = PQConfig.builder()
                .enabled(true)
                .segments(128)
                .centroids(256)
                .trainingLimit(250000)

            hnswConfigBuilder.pq(pqConfigBuilder.build())

            /* Vector spaces need to be defined up front. So here we define a named vector for each vector descriptor */
            val vectorConfig = mutableMapOf<String, WeaviateClass.VectorConfig>()
            namedVectors.forEach { vectorName ->
                val name = "${DESCRIPTOR_ENTITY_PREFIX}_${vectorName}"
                vectorConfig[name] = WeaviateClass.VectorConfig.builder()
                    .vectorIndexType("hnsw")
                    .vectorIndexConfig(hnswConfigBuilder.build())
                    .vectorizer(noVectorizer())
                    .build()
            }

            val shardingConfig = ShardingConfig.builder()
                .actualCount(3)
                .desiredCount(3)
                .build()

            /* Put everything together */
            val wClass = WeaviateClass.builder()
                .className(className)
                .description(RETRIEVABLE_ENTITY_DESCRIPTION)
                .invertedIndexConfig(invertedIndexConfig)
                .vectorConfig(vectorConfig)
                .shardingConfig(shardingConfig)
                .properties(listOf(
                    Property.builder()
                        .name(RETRIEVABLE_TYPE_PROPERTY_NAME)
                        .description(RETRIEVABLE_TYPE_PROPERTY_DESCRIPTION)
                        .dataType(listOf(DataType.TEXT))
                        .build()
                ))
                .build()

            /* Create the collection */
            client.schema().classCreator().withClass(wClass).run().let {
                if (it.hasErrors()) {
                    LOGGER.error { "Error creating collection '$className': ${it.error}" }
                } else {
                    LOGGER.info { "Collection '$className' created successfully." }
                }
            }
        }
    }

    /**
     * Small helper function that returns a map with the vectorizer set to none.
     */
    private fun noVectorizer(): Map<String, Any> = mapOf("none" to emptyMap<String, Any>())

    /**
     * Tries to execute a given action within a database transaction.
     *
     * @param action The action to execute within the transaction.
     */
    @Synchronized
    override fun <T> withTransaction(action: () -> T): T {
        LOGGER.warn {"Weaviate does not support transactions. Action will be executed without transaction."}
        return action()
    }

    /**
     * Generates and returns a [RetrievableInitializer] for this [WeaviateConnection].
     *
     * @return [RetrievableInitializer]
     */
    override fun getRetrievableInitializer(): RetrievableInitializer
            = org.vitrivr.engine.database.weaviate.retrievable.WeaviateRetrievableInitializer(this)

    /**
     * Generates and returns a [RetrievableWriter] for this [WeaviateConnection].
     *
     * @return [RetrievableWriter]
     */
    override fun getRetrievableWriter(): RetrievableWriter
            = org.vitrivr.engine.database.weaviate.retrievable.WeaviateRetrievableWriter(this)

    /**
     * Generates and returns a [RetrievableWriter] for this [WeaviateConnection].
     *
     * @return [RetrievableReader]
     */
    override fun getRetrievableReader(): RetrievableReader
            = org.vitrivr.engine.database.weaviate.retrievable.WeaviateRetrievableReader(this)

    /**
     * Returns the human-readable description of this [WeaviateConnection].
     */
    override fun description(): String = this.client.misc().toString()

    /**
     * Closes this [WeaviateConnection]
     */
    override fun close()  {
        /* No op. */
    }
}