package org.vitrivr.engine.database.weaviate.properties.vector

import io.weaviate.client.v1.data.model.WeaviateObject
import io.weaviate.client.v1.filters.Operator
import io.weaviate.client.v1.filters.WhereFilter
import io.weaviate.client.v1.graphql.query.argument.WhereArgument
import io.weaviate.client.v1.graphql.query.fields.Field
import io.weaviate.client.v1.schema.model.Property
import org.vitrivr.engine.core.model.descriptor.vector.VectorDescriptor
import org.vitrivr.engine.core.model.retrievable.Retrieved
import org.vitrivr.engine.core.model.types.Value
import org.vitrivr.engine.core.model.retrievable.RetrievableId
import org.vitrivr.engine.core.model.descriptor.DescriptorId
import org.vitrivr.engine.database.weaviate.*
import org.vitrivr.engine.database.weaviate.LOGGER
import org.vitrivr.engine.database.weaviate.properties.AbstractDescriptorProperty
import org.vitrivr.engine.database.weaviate.toWeaviateObject
import java.util.*

/**
 * An abstract implementation of a [Property] for [VectorDescriptor]s in Weaviate.
 *
 * @author Nico Bachmann
 */
abstract class AbstractVectorDescriptorProperty<D: VectorDescriptor<D, V>, V: Value.Vector<S>, S>(val connection: WeaviateConnection): AbstractDescriptorProperty<D>() {

    /**
     * Initializes the [Property] for this [VectorDescriptor] in Weaviate.
     */
    override fun initialize() {
        if (!this.isInitialized()) {
            LOGGER.error {"Named Vector ${this.name} does not exist in class ${Constants.getCollectionName()}. This must be declared upfront via the config file"}
        }
    }

    /**
     * Checks if the [Property] is initialized in Weaviate.
     */
    override fun isInitialized(): Boolean{
        val result = connection.client.schema().classGetter()
            .withClassName(Constants.getCollectionName())
            .run()

        if (isValid(result)) {
            return result.result.vectorConfig?.containsKey(this.name) ?: false
        }
        return false
    }

    /**
     * Returns the [VectorDescriptor] for the given [retrievableId].
     *
     * Note: As descriptors are stored as properties of retrievables, the [DescriptorId] equals the [RetrievableId].
     *
     * @param retrievableId The [RetrievableId] of the [VectorDescriptor] to retrieve.
     * @return The [VectorDescriptor] or null if not found.
     */
    override fun get(retrievableId: UUID): D? {
        val result = this.connection.client.data().objectsGetter()
            .withClassName(Constants.getCollectionName())
            .withID(retrievableId.toString())
            .withVector()
            .run()

        if (isValid(result)) {
            result.result.first().vectors[this.name]?.let {
                val id = UUID.fromString(result.result.first().id)
                return this.resultToDescriptor(it, id)
            } ?: run {
                LOGGER.warn { "No vector found for retrievableId $retrievableId with property ${this.name}" }
                return null
            }
        }
        return null
    }

    /**
     * Returns all [VectorDescriptor]s for the given [retrievableIds].
     *
     * @param retrievableIds The [Iterable] of [RetrievableId]s to retrieve.
     * @return A [Sequence] of [VectorDescriptor]s.
     */
    override fun getAll(retrievableIds: Iterable<UUID>): Sequence<D> {
        val retrievableIdsArray = retrievableIds.map { it.toString() }.toTypedArray()
        val idFilter = WhereFilter.builder()
            .path("id")
            .operator(Operator.ContainsAny)
            .valueText(*retrievableIdsArray)
            .build()

        val result = this.connection.client.graphQL().get()
            .withClassName(Constants.getCollectionName())
            .withFields(
                Field.builder().name("_additional").fields(
                    Field.builder().name("id").build(),
                    Field.builder().name("vectors").fields(
                        Field.builder().name(this.name).build()
                    ).build()
                ).build())
            .withWhere(WhereArgument.builder().filter(idFilter).build())
            .run()

        return if (isValid(result)) {
            wObjectsToDescriptors(result.toWeaviateObject() ?: emptySequence())
        } else {
            emptySequence()
        }
    }

    /**
     * Returns all [VectorDescriptor]s in the collection.
     *
     * @return A [Sequence] of all [VectorDescriptor]s.
     */
    override fun getAll() : Sequence<D> {
        val result = this.connection.client.graphQL().get()
            .withClassName(Constants.getCollectionName())
            .withFields(
                Field.builder().name("_additional").fields(
                    Field.builder().name("id").build(),
                    Field.builder().name("vectors").fields(
                        Field.builder().name(this.name).build()
                    ).build()
                ).build())
            .run()

        return if (isValid(result)) {
            wObjectsToDescriptors(result.toWeaviateObject() ?: emptySequence())
        } else {
            emptySequence()
        }
    }

    /**
     * Checks if the [Property] is set for the given [retrievableId].
     *
     * @param retrievableId The [RetrievableId] to check.
     * @return True if the [Property] is set, false otherwise.
     */
    override fun isSet(retrievableId: UUID): Boolean {
        val result = this.connection.client.data().objectsGetter()
            .withClassName(Constants.getCollectionName())
            .withID(retrievableId.toString())
            .withVector()
            .run()

        if (isValid(result)) {
            return result.result.first().vectors.containsKey(this.name)
        }
        return false
    }

    /**
     * Removes the [Property] for the given [VectorDescriptor].
     *
     * @param descriptor The [VectorDescriptor] to unset.
     * @return True if the [Property] was successfully removed, false otherwise.
     */
    override fun unset(descriptor: D): Boolean {
        val result = this.connection.client.data().objectsGetter()
            .withClassName(Constants.getCollectionName())
            .withID(descriptor.retrievableId.toString())
            .withVector()
            .run()

        if (!isValid(result)) {
            return false
        }

        val wObject = result.result.firstOrNull() ?: return true // nothing to delete
        wObject.vectors.remove(this.name) // remove the property
        val update = this.connection.client.data().updater() // replace the entire object.
            .withClassName(Constants.getCollectionName())
            .withID(descriptor.retrievableId.toString())
            .withProperties(wObject.properties)
            .withVectors(wObject.vectors)
            .run()

        return isValid(update)
    }

    /**
     * Queries for [Retrieved]s based on the provided [org.vitrivr.engine.core.model.query.Query].
     *
     * @param query The [org.vitrivr.engine.core.model.query.Query] to execute.
     * @return A [Sequence] of [Retrieved]s that match the query.
     */
    override fun queryRetrievable(query: org.vitrivr.engine.core.model.query.Query): Sequence<Retrieved> = wObjectsToRetrieved(query(query))

    /**
     * Queries for [VectorDescriptor]s based on the provided [org.vitrivr.engine.core.model.query.Query].
     *
     * @param query The [org.vitrivr.engine.core.model.query.Query] to execute.
     * @return A [Sequence] of [VectorDescriptor]s that match the query.
     */
    override fun queryProperty(query: org.vitrivr.engine.core.model.query.Query): Sequence<D> = wObjectsToDescriptors(query(query))

    /**
     * Executes the provided [org.vitrivr.engine.core.model.query.Query] and returns a [Sequence] of [WeaviateObject]s.
     *
     * @param query The [org.vitrivr.engine.core.model.query.Query] to execute.
     * @return A [Sequence] of [WeaviateObject]s that match the query.
     */
    abstract fun query(query: org.vitrivr.engine.core.model.query.Query): Sequence<WeaviateObject>

    /**
     * Converts a [WeaviateObject] to a [VectorDescriptor] of type [D].
     */
    override fun wObjectsToDescriptors(weaviateObjects: Sequence<WeaviateObject>) = weaviateObjects.mapNotNull { wObj ->
        val value = wObj.vectors[this.name] ?: return@mapNotNull null
        val id = UUID.fromString(wObj.id)
        this.resultToDescriptor(value, id)
    }

}