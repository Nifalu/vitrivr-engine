package org.vitrivr.engine.database.weaviate.properties

import io.weaviate.client.v1.data.model.WeaviateObject
import org.vitrivr.engine.core.model.descriptor.Descriptor
import org.vitrivr.engine.core.model.retrievable.Retrieved
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

    abstract fun wObjectsToRetrieved(weaviateObjects: Sequence<WeaviateObject>): Sequence<Retrieved>

    abstract fun wObjectsToDescriptors(weaviateObjects: Sequence<WeaviateObject>): Sequence<D>

    abstract fun queryRetrievable(query: org.vitrivr.engine.core.model.query.Query): Sequence<Retrieved>

    abstract fun queryProperty(query: org.vitrivr.engine.core.model.query.Query): Sequence<D>

}