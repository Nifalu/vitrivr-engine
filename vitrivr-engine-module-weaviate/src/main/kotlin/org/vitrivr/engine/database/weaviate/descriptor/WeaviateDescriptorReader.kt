package org.vitrivr.engine.database.weaviate.descriptor

import org.vitrivr.engine.core.database.descriptor.DescriptorReader
import org.vitrivr.engine.core.model.descriptor.Descriptor
import org.vitrivr.engine.core.model.descriptor.DescriptorId
import org.vitrivr.engine.core.model.metamodel.Schema
import org.vitrivr.engine.core.model.retrievable.RetrievableId
import org.vitrivr.engine.core.model.retrievable.Retrieved
import org.vitrivr.engine.database.weaviate.*
import org.vitrivr.engine.database.weaviate.LOGGER
import org.vitrivr.engine.database.weaviate.properties.AbstractDescriptorProperty

class WeaviateDescriptorReader<D : Descriptor<*>>(override val field: Schema.Field<*, D>, override val connection: WeaviateConnection) : DescriptorReader<D> {

    private val descriptorProperty: AbstractDescriptorProperty<D> = this.field.toDescriptorProperty()

    override fun exists(descriptorId: DescriptorId): Boolean = descriptorProperty.isSet(descriptorId)

    override fun get(descriptorId: DescriptorId): D? = descriptorProperty.get(descriptorId)

    override fun getAll(descriptorIds: Iterable<DescriptorId>): Sequence<D> = descriptorProperty.getAll(descriptorIds)

    override fun getAll(): Sequence<D> = descriptorProperty.getAll()

    override fun getForRetrievable(retrievableId: RetrievableId): Sequence<D> {
        LOGGER.warn { "This Weaviate Adapter does not (yet) support multiple descriptors of the same type for a retrievable." }
        return get(retrievableId)?.let { sequenceOf(it) } ?: emptySequence()
    }

    override fun getAllForRetrievable(retrievableIds: Iterable<RetrievableId>): Sequence<D> {
        LOGGER.warn { "This Weaviate Adapter does not (yet) support multiple descriptors of the same type for a retrievable." }
        return getAll(retrievableIds)
    }

    override fun query(query: org.vitrivr.engine.core.model.query.Query): Sequence<D> = this.descriptorProperty.queryProperty(query)

    override fun queryAndJoin(query: org.vitrivr.engine.core.model.query.Query): Sequence<Retrieved> = this.descriptorProperty.queryRetrievable(query)

    override fun count(): Long {
        TODO("Not yet implemented")
    }

}