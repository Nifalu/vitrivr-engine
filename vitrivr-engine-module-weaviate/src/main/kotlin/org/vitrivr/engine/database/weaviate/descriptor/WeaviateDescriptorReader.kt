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

/**
 * A [DescriptorReader] implementation for handling [Descriptor]s in Weaviate.
 *
 * @author Nico Bachmann
 */
class WeaviateDescriptorReader<D : Descriptor<*>>(override val field: Schema.Field<*, D>, override val connection: WeaviateConnection) : DescriptorReader<D> {

    /** The [AbstractDescriptorProperty] that this reader uses to interact with Weaviate. */
    private val descriptorProperty: AbstractDescriptorProperty<D> = this.field.toDescriptorProperty()

    /**
     * Checks if a [Descriptor] with the given [DescriptorId] exists in Weaviate.
     *
     * @param descriptorId The [DescriptorId] to check.
     * @return `true` if the descriptor exists, `false` otherwise.
     */
    override fun exists(descriptorId: DescriptorId): Boolean = descriptorProperty.isSet(descriptorId)

    /**
     * Retrieves a [Descriptor] by its [DescriptorId].
     *
     * @param descriptorId The [DescriptorId] of the [Descriptor] to retrieve.
     * @return The [Descriptor] if it exists, or `null` if it does not.
     */
    override fun get(descriptorId: DescriptorId): D? = descriptorProperty.get(descriptorId)

    /**
     * Retrieves all descriptors for the given [DescriptorId]s.
     *
     * @param descriptorIds An iterable of [DescriptorId]s to retrieve [Descriptor]s for.
     * @return A sequence of [Descriptor]s corresponding to the provided [DescriptorId]s.
     */
    override fun getAll(descriptorIds: Iterable<DescriptorId>): Sequence<D> = descriptorProperty.getAll(descriptorIds)

    /**
     * Retrieves all [Descriptor]s of type [D] from Weaviate.
     *
     * @return A sequence of all [Descriptor]s of type [D].
     */
    override fun getAll(): Sequence<D> = descriptorProperty.getAll()

    /**
     * Retrieves the [Descriptor]s for a given [RetrievableId].
     *
     * @param retrievableId The [RetrievableId] for which to retrieve [Descriptor]s.
     * @return A sequence of [Descriptor]s of type [D] for the specified [RetrievableId].
     */
    override fun getForRetrievable(retrievableId: RetrievableId): Sequence<D> {
        LOGGER.warn { "This Weaviate Adapter does not (yet) support multiple descriptors of the same type for a retrievable." }
        return get(retrievableId)?.let { sequenceOf(it) } ?: emptySequence()
    }

    /**
     * Retrieves all [Descriptor]s of type [D] for the given [RetrievableId]s.
     *
     * @param retrievableIds An iterable of [RetrievableId]s to retrieve [Descriptor]s for.
     * @return A sequence of [Descriptor]s of type [D] corresponding to the provided [RetrievableId]s.
     */
    override fun getAllForRetrievable(retrievableIds: Iterable<RetrievableId>): Sequence<D> {
        LOGGER.warn { "This Weaviate Adapter does not (yet) support multiple descriptors of the same type for a retrievable." }
        return getAll(retrievableIds)
    }

    /**
     * Queries for [Descriptor]s of type [D] based on the provided [org.vitrivr.engine.core.model.query.Query].
     *
     * @param query The [org.vitrivr.engine.core.model.query.Query] to execute.
     * @return A sequence of [Descriptor]s of type [D] that match the query.
     */
    override fun query(query: org.vitrivr.engine.core.model.query.Query): Sequence<D> = this.descriptorProperty.queryProperty(query)

    /**
     * Queries [Retrieved]s based on the provided [org.vitrivr.engine.core.model.query.Query].
     *
     * @param query The [org.vitrivr.engine.core.model.query.Query] to execute.
     * @return A sequence of [Retrieved]s that match the query.
     */
    override fun queryAndJoin(query: org.vitrivr.engine.core.model.query.Query): Sequence<Retrieved> = this.descriptorProperty.queryRetrievable(query)

    /**
     * NOT YET IMPLEMENTED: Counts the number of [Descriptor]s of type [D] in Weaviate.
     *
     * @return The count of [Descriptor]s of type [D].
     */
    override fun count(): Long {
        LOGGER.warn { "Not yet implemented" }
        return 0L
    }

}