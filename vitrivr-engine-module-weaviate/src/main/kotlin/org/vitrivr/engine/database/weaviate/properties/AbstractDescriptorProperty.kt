package org.vitrivr.engine.database.weaviate.properties

import io.weaviate.client.base.Result
import io.weaviate.client.v1.data.model.WeaviateObject
import org.vitrivr.engine.core.model.descriptor.Descriptor
import org.vitrivr.engine.core.model.retrievable.Retrieved
import org.vitrivr.engine.database.weaviate.LOGGER
import org.vitrivr.engine.database.weaviate.RETRIEVABLE_TYPE_PROPERTY_NAME
import java.util.*

abstract class AbstractDescriptorProperty<D : Descriptor<*>> {

    abstract val name: String

    abstract fun initialize()

    abstract fun isInitialized() : Boolean

    abstract fun isSet(retrievableId: UUID) : Boolean

    abstract fun set(descriptor: D) : Boolean

    abstract fun get(retrievableId: UUID): D?

    abstract fun getAll(retrievableIds: Iterable<UUID>): Sequence<D>

    abstract fun getAll(): Sequence<D>

    abstract fun unset(descriptor: D): Boolean

    abstract fun resultToDescriptor(value: Any, id: UUID): D?

    abstract fun wObjectsToDescriptors(weaviateObjects: Sequence<WeaviateObject>): Sequence<D>

    abstract fun queryRetrievable(query: org.vitrivr.engine.core.model.query.Query): Sequence<Retrieved>

    abstract fun queryProperty(query: org.vitrivr.engine.core.model.query.Query): Sequence<D>

    fun wObjectsToRetrieved(weaviateObjects: Sequence<WeaviateObject>): Sequence<Retrieved> {
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

    fun isValid(result: Result<*>) : Boolean {
        if (result.hasErrors()) {
            LOGGER.error { "Failed to fetch descriptor due to error: ${result.error}" }
            return false
        }
        if (result.result == null) {
            LOGGER.warn { "Got null result from Weaviate $result" }
            return false
        }
        return true
    }
}