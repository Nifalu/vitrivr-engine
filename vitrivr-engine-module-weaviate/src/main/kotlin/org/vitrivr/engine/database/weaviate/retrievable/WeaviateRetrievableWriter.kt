package org.vitrivr.engine.database.weaviate.retrievable

import io.weaviate.client.v1.data.model.WeaviateObject
import io.weaviate.client.v1.filters.Operator
import io.weaviate.client.v1.filters.WhereFilter
import io.weaviate.client.v1.schema.model.Property
import org.vitrivr.engine.core.database.retrievable.RetrievableWriter
import org.vitrivr.engine.core.model.relationship.Relationship
import org.vitrivr.engine.core.model.retrievable.Retrievable
import org.vitrivr.engine.database.weaviate.*
import org.vitrivr.engine.database.weaviate.LOGGER

internal class WeaviateRetrievableWriter(override val connection: WeaviateConnection) : RetrievableWriter {

    /**
     * Connect two [Retrievable]s in Weaviate using a [Relationship].
     *
     * This first tries to add connect the two [Retrievable]s using the [Relationship] predicate.
     * If the predicate property does not exist in the schema yet (i.E Weaviate sees it the first time),
     * it will throw an error which we catch,we then create the property and retry the connection.
     */
    override fun connect(relationship: Relationship): Boolean {
        /* Check if the predicate already exists as property*/
        val predicate = relationship.predicate.firstLowerCase()
        val result = connection.client.data().referenceCreator()
            .withClassName(RETRIEVABLE_ENTITY_NAME)
            .withID(relationship.subjectId.toString())
            .withReferenceProperty(predicate)
            .withReference(
                connection.client.data().referencePayloadBuilder()
                    .withClassName(RETRIEVABLE_ENTITY_NAME)
                    .withID(relationship.objectId.toString())
                    .payload()
            )
            .run()

        if (!result.hasErrors()) {
            return true
        }

        /* if the reference property (predicate) was never seen before, the above will fail.
        * we need to create the reference property first. This is kinda ugly, but more efficient
        * than checking for existence every time again */
        if (result.error.messages.any {it.message.contains(predicate)}) {
            LOGGER.info { "Creating new reference property $predicate for collection $RETRIEVABLE_ENTITY_NAME" }
            val property = Property.builder()
                .name(predicate)
                .description("Reference property for $predicate")
                .dataType(listOf(RETRIEVABLE_ENTITY_NAME))
                .build()

            /* Create the reference property */
            val propertyResult = connection.client.schema().propertyCreator()
                .withClassName(RETRIEVABLE_ENTITY_NAME)
                .withProperty(property)
                .run()

            if (propertyResult.hasErrors()) {
                LOGGER.error { "Failed to create reference property $predicate for collection $RETRIEVABLE_ENTITY_NAME due to error.\n" +
                        propertyResult.error }
                return false
            }

            /* Retry the reference creation */
            val retryResult = connection.client.data().referenceCreator()
                .withClassName(RETRIEVABLE_ENTITY_NAME)
                .withID(relationship.subjectId.toString())
                .withReferenceProperty(predicate)
                .withReference(
                    connection.client.data().referencePayloadBuilder()
                        .withClassName(RETRIEVABLE_ENTITY_NAME)
                        .withID(relationship.objectId.toString())
                        .payload()
                )
                .run()

            if (retryResult.hasErrors()) {
                LOGGER.error { "Failed to insert relationship ${relationship.subjectId} -($predicate-> ${relationship.objectId} due to error.\n" +
                        result.error }
                return false
            }
        } else {
            LOGGER.error { "Failed to insert relationship ${relationship.subjectId} -($predicate-> ${relationship.objectId} due to error.\n" +
                    result.error }
            return false
        }
        return true
    }


    /**
     *  Connects a collection of [Relationship]s to Weaviate.
     *
     *  THIS IS CURRENTLY NOT BATCHED AND MIGHT BE SLOW.
     *
     *  @param relationships The [Iterable] of [Relationship]s to connect.
     */
    override fun connectAll(relationships: Iterable<Relationship>): Boolean {

        LOGGER.warn {" !! There is no useful batcher for weaviate references yet. This might be slow !!" +
        "Adding ${relationships.count()} relationships individually to Weaviate."}

        relationships.forEach {
            val predicate = it.predicate.firstLowerCase()
            if (!connect(it)) {
                LOGGER.error { "Failed to insert relationship ${it.subjectId} -> $predicate -> ${it.objectId} due to error." }
                return false
            }
        }
        return true
    }


    /**
     * Removes a [Relationship] from Weaviate.
     *
     * @param relationship The [Relationship] to disconnect.
     */
    override fun disconnect(relationship: Relationship): Boolean {
        val predicate = relationship.predicate.firstLowerCase()
        val result = connection.client.data().referenceDeleter()
            .withClassName(RETRIEVABLE_ENTITY_NAME)
            .withID(relationship.subjectId.toString())
            .withReferenceProperty(predicate)
            .withReference(
                connection.client.data().referencePayloadBuilder()
                    .withClassName(RETRIEVABLE_ENTITY_NAME)
                    .withID(relationship.objectId.toString())
                    .payload()
            )
            .run()
        if (result.hasErrors()) {
            LOGGER.error { "Failed to delete relationship ${relationship.subjectId} -> $predicate -> ${relationship.objectId} due to error." +
                    result.error }
            return false
        }
        return true
    }


    /**
     * Removes a collection of [Relationship]s from Weaviate.
     *
     * WARNING: THIS IS CURRENTLY NOT BATCHED AND MIGHT BE SLOW.
     *
     * @param relationships The [Iterable] of [Relationship]s to disconnect.
     */
    override fun disconnectAll(relationships: Iterable<Relationship>): Boolean {
        relationships.forEach {
            if (!disconnect(it)) {
                val predicate = it.predicate.firstLowerCase()
                LOGGER.error { "Failed to delete relationship ${it.subjectId} -> $predicate -> ${it.objectId} due to error." }
                return false
            }
        }
        return true
    }


    /**
     * Persists a single [Retrievable] to Weaviate.
     *
     * @param item The [Retrievable] to persist.
     */
    override fun add(item: Retrievable): Boolean {
        val result = connection.client.data().creator()
            .withClassName(RETRIEVABLE_ENTITY_NAME)
            .withID(item.id.toString())
            .withProperties(
                mapOf(
                    RETRIEVABLE_TYPE_PROPERTY_NAME to item.type
                )
            )
            .run()
        if (result.hasErrors()) {
            LOGGER.error { "Error persisting retrievable ${item.id} to Weaviate: ${result.error}\n" }
            return false
        }
        return true
    }


    /**
     * Persists a collection of [Retrievable]s to Weaviate.
     *
     * @param items The [Iterable] of [Retrievable]s to persist.
     */
    override fun addAll(items: Iterable<Retrievable>): Boolean {
        /* Write all retrievables to the batcher */
        val batcher = connection.client.batch().objectsBatcher()
        items.forEach { item ->
            val wObject = WeaviateObject.builder()
                .className(RETRIEVABLE_ENTITY_NAME)
                .id(item.id.toString())
                .properties(
                    mapOf(
                        RETRIEVABLE_TYPE_PROPERTY_NAME to item.type
                    )
                )
                .build()
            batcher.withObject(wObject)
        }

        /* Execute the batcher */
        val result = batcher.run()

        if (result.hasErrors()) {
            LOGGER.error { "Error persisting retrievables to Weaviate: ${result.error}\n" }
            return false
        }

        return true
    }


    /**
     * Update a [Retrievable]s type in Weaviate.
     *
     * @param item The updated [Retrievable].
     */
    override fun update(item: Retrievable): Boolean {
        val result = connection.client.data().updater()
            .withMerge() // Keep existing properties
            .withClassName(RETRIEVABLE_ENTITY_NAME)
            .withID(item.id.toString())
            .withProperties(
                mapOf(
                    RETRIEVABLE_TYPE_PROPERTY_NAME to item.type
                )
            )
            .run()
        if (result.hasErrors()) {
            LOGGER.error { "Error updating retrievable ${item.id} in Weaviate: ${result.error}\n" }
            return false
        }
        return true
    }


    /**
     * Deletes a [Retrievable] from Weaviate.
     *
     * @param item The [Retrievable] to delete.
     */
    override fun delete(item: Retrievable): Boolean {
        val result = connection.client.data().deleter()
            .withClassName(RETRIEVABLE_ENTITY_NAME)
            .withID(item.id.toString())
            .run()
        if (result.hasErrors()) {
            LOGGER.error { "Error deleting retrievable ${item.id} from Weaviate: ${result.error}\n" }
            return false
        }
        return true
    }


    /**
     * Deletes a selection of [Retrievable]s from Weaviate.
     *
     * @param items The [Iterable] of [Retrievable]s to delete.
     */
    override fun deleteAll(items: Iterable<Retrievable>): Boolean {

        val uuids = items.map { it.id.toString() }.toTypedArray()

        val whereFilter = WhereFilter.builder()
            .path("id")
            .operator(Operator.ContainsAny)
            .valueText(*uuids)
            .build()

        val result = connection.client.batch().objectsBatchDeleter()
            .withClassName(RETRIEVABLE_ENTITY_NAME)
            .withWhere(whereFilter)
            .run()

        if (result.hasErrors()) {
            LOGGER.error { "Error deleting retrievables from Weaviate: ${result.error}\n" }
            return false
        }

        return true
    }
}