package org.vitrivr.engine.database.weaviate.retrievable

import org.vitrivr.engine.core.database.retrievable.RetrievableInitializer
import org.vitrivr.engine.database.weaviate.*
import org.vitrivr.engine.database.weaviate.LOGGER

internal class WeaviateRetrievableInitializer(private val connection: WeaviateConnection): RetrievableInitializer {


    /**
     * Redundant initialization method. We do the initialization in the [WeaviateConnection] class.
     */
    override fun initialize() {
        val classExists = connection.client.schema().exists().withClassName(Constants.COLLECTION_NAME).run()
        if (classExists.hasErrors()) {
            LOGGER.error { "The $Constants.COLLECTION_NAME Collection should be initialized by now." }
            return
        }
    }


    /**
     * De-initialization method. As Weaviate stores everything in the same collection, we don't need to do anything here.
     */
    override fun deinitialize() {
        try {
            connection.client.schema().classDeleter().withClassName(Constants.COLLECTION_NAME).run()
        } catch (e: Throwable) {
            LOGGER.error(e) { "Failed to de-initialize retrievable entities due to exception." }
        }
    }


    /**
     * Returns true if the retrievable collection is initialized.
     */
    override fun isInitialized(): Boolean =
        connection.client.schema().exists().withClassName(Constants.COLLECTION_NAME).run().result


    /**
     * Empty the retrievable collection.
     */
    override fun truncate() {
        try {
            connection.client.batch().objectsBatchDeleter()
                .withClassName(Constants.COLLECTION_NAME)
                .run()
        } catch (e: Throwable) {
            LOGGER.error(e) { "Failed to truncate retrievable entities due to exception." }
        }
    }

}