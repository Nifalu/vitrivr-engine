package org.vitrivr.engine.database.weaviate.properties.vector

import io.weaviate.client.v1.data.model.WeaviateObject
import io.weaviate.client.v1.filters.Operator
import io.weaviate.client.v1.filters.WhereFilter
import io.weaviate.client.v1.graphql.query.argument.WhereArgument
import io.weaviate.client.v1.graphql.query.fields.Field
import org.vitrivr.engine.core.model.descriptor.vector.VectorDescriptor
import org.vitrivr.engine.core.model.retrievable.Retrieved
import org.vitrivr.engine.core.model.types.Value
import org.vitrivr.engine.database.weaviate.*
import org.vitrivr.engine.database.weaviate.LOGGER
import org.vitrivr.engine.database.weaviate.properties.AbstractDescriptorProperty
import org.vitrivr.engine.database.weaviate.toWeaviateObject
import java.util.*

abstract class AbstractVectorDescriptorProperty<D: VectorDescriptor<D, V>, V: Value.Vector<S>, S>(val connection: WeaviateConnection): AbstractDescriptorProperty<D>() {

    override fun initialize() {
        if (!this.isInitialized()) {
            LOGGER.error {"Named Vector ${this.name} does not exist in class ${Constants.getCollectionName()}. This must be declared upfront via the config file"}
        }
    }

    override fun isInitialized(): Boolean{
        val result = connection.client.schema().classGetter()
            .withClassName(Constants.getCollectionName())
            .run()

        if (isValid(result)) {
            return result.result.vectorConfig?.containsKey(this.name) ?: false
        }
        return false
    }

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

    override fun queryRetrievable(query: org.vitrivr.engine.core.model.query.Query): Sequence<Retrieved> = wObjectsToRetrieved(query(query))

    override fun queryProperty(query: org.vitrivr.engine.core.model.query.Query): Sequence<D> = wObjectsToDescriptors(query(query))

    abstract fun query(query: org.vitrivr.engine.core.model.query.Query): Sequence<WeaviateObject>

    override fun wObjectsToDescriptors(weaviateObjects: Sequence<WeaviateObject>) = weaviateObjects.mapNotNull { wObj ->
        val value = wObj.vectors[this.name] ?: return@mapNotNull null
        val id = UUID.fromString(wObj.id)
        this.resultToDescriptor(value, id)
    }

}