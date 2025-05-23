package org.vitrivr.engine.database.weaviate.properties.scalar

import io.weaviate.client.v1.data.model.WeaviateObject
import io.weaviate.client.v1.filters.Operator
import io.weaviate.client.v1.filters.WhereFilter
import io.weaviate.client.v1.graphql.query.argument.WhereArgument
import io.weaviate.client.v1.graphql.query.fields.Field
import io.weaviate.client.v1.schema.model.Property
import org.vitrivr.engine.core.model.descriptor.scalar.ScalarDescriptor
import org.vitrivr.engine.core.model.query.bool.SimpleBooleanQuery
import org.vitrivr.engine.core.model.retrievable.Retrieved
import org.vitrivr.engine.core.model.types.Value
import org.vitrivr.engine.database.weaviate.*
import org.vitrivr.engine.database.weaviate.LOGGER
import org.vitrivr.engine.database.weaviate.properties.AbstractDescriptorProperty
import org.vitrivr.engine.database.weaviate.toWeaviateObject
import java.util.*

sealed class AbstractScalarDescriptorProperty<D: ScalarDescriptor<D, V>, V : Value.ScalarValue<S>, S>(val connection: WeaviateConnection): AbstractDescriptorProperty<D>() {

    abstract val property: Property

    override fun initialize() {
        val result = connection.client.schema().propertyCreator()
            .withClassName(Constants.COLLECTION_NAME)
            .withProperty(this.property)
            .run()

        if (result.hasErrors()) {
            LOGGER.error {
                "Failed to initialize property '${this.property.name}' in Weaviate due to exception: ${result.error}"
            }
        }
    }

    override fun isInitialized(): Boolean {
        this.connection.client.schema().classGetter().withClassName(Constants.COLLECTION_NAME).run().let { result ->
            if (result.hasErrors()) {
                LOGGER.error { "Failed to check if property '${this.property.name}' is initialized due to exception: ${result.error}" }
                return false
            }
            return result.result.properties.any { it.name == this.property.name && it.dataType == this.property.dataType }
        }
    }


    override fun get(retrievableId: UUID): D? {
        val result = this.connection.client.data().objectsGetter()
            .withClassName(Constants.COLLECTION_NAME)
            .withID(retrievableId.toString())
            .run()

        if (result.hasErrors()) {
            LOGGER.error { "Failed to fetch descriptor $retrievableId due to error." }
            return null
        }

        result.result.first().properties[this.property.name]?.let {
            val id = UUID.fromString(result.result.first().id)
            return this.resultToDescriptor(it, id)
        } ?: return null

    }


    override fun getAll(retrievableIds: Iterable<UUID>): Sequence<D> {
        val retrievableIdsArray = retrievableIds.map { it.toString() }.toTypedArray()
        val idFilter = WhereFilter.builder()
            .path("id")
            .operator(Operator.ContainsAny)
            .valueText(*retrievableIdsArray)
            .build()

        val result = this.connection.client.graphQL().get()
            .withClassName(Constants.COLLECTION_NAME)
            .withFields(
                Field.builder().name("_additional").fields(
                    Field.builder().name("id").build()).build(),
                Field.builder().name(this.property.name).build())
            .withWhere(WhereArgument.builder().filter(idFilter).build())
            .run()

        if (result.hasErrors()) {
            LOGGER.error { "Failed to fetch descriptors $retrievableIds due to error." }
            return emptySequence()
        }
        if (result.result == null) {
            LOGGER.warn { "No descriptors found for $retrievableIds" }
            return emptySequence()
        }

        return wObjectsToDescriptors(result.toWeaviateObject() ?: emptySequence())
    }

    override fun getAll() : Sequence<D> {
        val result = this.connection.client.graphQL().get()
            .withClassName(Constants.COLLECTION_NAME)
            .withFields(
                Field.builder().name("_additional").fields(
                    Field.builder().name("id").build()).build(),
                Field.builder().name(this.property.name).build())
            .run()

        if (result.hasErrors()) {
            LOGGER.error { "Failed to fetch descriptors due to error." }
            return emptySequence()
        }
        if (result.result == null) {
            LOGGER.warn { "No descriptors found" }
            return emptySequence()
        }

        return wObjectsToDescriptors(result.toWeaviateObject() ?: emptySequence())
    }

    override fun wObjectsToRetrieved(weaviateObjects: Sequence<WeaviateObject>): Sequence<Retrieved> {
        return weaviateObjects.map { retrievable ->
            val type = retrievable.properties[RETRIEVABLE_TYPE_PROPERTY_NAME] as? String ?: run {
                LOGGER.error { "Retrievable object ${retrievable.id} does not have a type property set." }
                "unknown"
            }
            Retrieved(
                id = UUID.fromString(retrievable.id),
                type = type,
                descriptors = this.wObjectsToDescriptors(sequenceOf(retrievable)).toSet()
            )
        }
    }

    override fun wObjectsToDescriptors(weaviateObjects: Sequence<WeaviateObject>): Sequence<D> = weaviateObjects.mapNotNull { wObj ->
            val value = wObj.properties[this.property.name] ?: return@mapNotNull null
            val id = UUID.fromString(wObj.id)
            resultToDescriptor(value, id)
        }


    /**
     * Update the given descriptor in the corresponding retrievable in Weaviate.
     */
    override fun set(descriptor: D): Boolean {
        val result = this.connection.client.data().updater()
            .withClassName(Constants.COLLECTION_NAME)
            .withID(descriptor.retrievableId.toString())
            .withMerge()
            .withProperties(
                mapOf(
                    this.property.name to descriptor.values().values
                )
            )
            .run()

        if (result.hasErrors()) {
            LOGGER.error {
                "Failed to update retrievable '${descriptor.retrievableId}' with descriptor ${this.property.name} due to exception: ${result.error}"
            }
        }

        return result.result
    }

    override fun isSet(retrievableId: UUID): Boolean {
        /* get the retrievable */
        val result = this.connection.client.data().objectsGetter()
            .withClassName(Constants.COLLECTION_NAME)
            .withID(retrievableId.toString())
            .run()

        if (result.hasErrors()) {
            LOGGER.error { "Failed to fetch descriptor $retrievableId due to error." }
            return false
        }

        return result.result.first().properties.containsKey(this.property.name)
    }


    /**
     * Delete the given descriptor from the corresponding retrievable in Weaviate.
     */
    override fun unset(descriptor: D): Boolean {
        val result = this.connection.client.data().objectsGetter()
            .withClassName(Constants.COLLECTION_NAME)
            .withID(descriptor.retrievableId.toString())
            .run()

        if (result.hasErrors()) {
            println("failed to delete descriptor ${this.property.name} from retrievable ${descriptor.retrievableId}: ${result.error}")
            return false
        }

        val wObject = result.result.firstOrNull() ?: return true // nothing to delete
        wObject.properties.remove(this.property.name) // remove the property
        val update = this.connection.client.data().updater() // replace the entire object.
            .withClassName(Constants.COLLECTION_NAME)
            .withID(descriptor.retrievableId.toString())
            .withProperties(wObject.properties)
            .run()

        if (update.hasErrors()) {
            println("failed to delete descriptor ${this.property.name} from retrievable ${descriptor.retrievableId}: ${update.error}")
            return false
        }

        return true
    }

    override fun queryRetrievable(query: org.vitrivr.engine.core.model.query.Query): Sequence<Retrieved> = wObjectsToRetrieved(query(query))

    override fun queryProperty(query: org.vitrivr.engine.core.model.query.Query): Sequence<D> = wObjectsToDescriptors(query(query))

    private fun query(query: org.vitrivr.engine.core.model.query.Query): Sequence<WeaviateObject> = when(query) {
        is SimpleBooleanQuery<*> -> {
            val whereFilter = this.buildWhereFilter(query)

            val result = this.connection.client.graphQL().get()
                .withClassName(Constants.COLLECTION_NAME)
                .withFields(
                    Field.builder().name("_additional").fields(
                        Field.builder().name("id").build()).build(),
                    Field.builder().name(this.property.name).build())
                .withWhere(WhereArgument.builder().filter(whereFilter).build())
                .run()

            if (result.hasErrors()) {
                LOGGER.error { "Failed to fetch descriptors due to error." }
                emptySequence<D>()
            }
            if (result.result == null) {
                LOGGER.warn { "No descriptors found" }
                emptySequence<D>()
            }
            result.toWeaviateObject() ?: emptySequence()
        }
        else -> run {
            LOGGER.error { "Unsupported query type: ${query::class.simpleName}" }
            emptySequence()
        }
    }

    protected abstract fun buildWhereFilter(query: SimpleBooleanQuery<*>): WhereFilter

}