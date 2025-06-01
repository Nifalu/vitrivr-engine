package org.vitrivr.engine.database.weaviate.descriptor

import org.vitrivr.engine.core.database.descriptor.DescriptorWriter
import org.vitrivr.engine.core.model.metamodel.Schema
import org.vitrivr.engine.core.model.descriptor.Descriptor
import org.vitrivr.engine.database.weaviate.*
import org.vitrivr.engine.database.weaviate.LOGGER
import org.vitrivr.engine.database.weaviate.properties.AbstractDescriptorProperty

/**
 * A [DescriptorWriter] implementation for handling [Descriptor]s in Weaviate.
 *
 * @author Nico Bachmann
 */
class WeaviateDescriptorWriter<D : Descriptor<*>>(override val field: Schema.Field<*, D>, override val connection: WeaviateConnection) : DescriptorWriter<D> {

    /** The [AbstractDescriptorProperty] that this writer uses to interact with Weaviate. */
    private val descriptorProperty: AbstractDescriptorProperty<D> = this.field.toDescriptorProperty()

    /**
     * Adds the given [Descriptor] to the corresponding retrievable in Weaviate.
     *
     * @param item The [Descriptor] to add.
     * @return `true` if the operation was successful, `false` otherwise.
     */
    override fun add(item: D): Boolean = this.descriptorProperty.set(item)

    /**
     * Adds the given [Descriptor]s to the corresponding retrievables in Weaviate.
     *
     * WARNING: Batch updates are not supported in Weaviate. This method will do single inserts
     * which can cause significant performance issues. Also, the return value is not reliable as it
     * will return false if a single update fails. The others are not reverted though. So we have a partial success.
     *
     * @param items The [Iterable] of [Descriptor]s to add.
     * @return `true` if all items were successfully added, `false` if any item failed to be added.
     */
    override fun addAll(items: Iterable<D>): Boolean {
        LOGGER.warn { "Batch inserting properties to Weaviate objects is not supported.\n" +
        "Running batch insert as single inserts.\n" }
        return items.all { this.descriptorProperty.set(it) }
    }

    /**
     * Updates the given [Descriptor] in the corresponding retrievable in Weaviate.
     *
     * @param item The [Descriptor] to update.
     * @return `true` if the operation was successful, `false` otherwise.
     */
    override fun update(item: D): Boolean = this.descriptorProperty.set(item)

    /**
     * Deletes the given [Descriptor] from the corresponding retrievable in Weaviate.
     *
     * @param item The [Descriptor] to delete.
     * @return `true` if the operation was successful, `false` otherwise.
     */
    override fun delete(item: D): Boolean = this.descriptorProperty.unset(item)

    /**
     * Deletes the given [Descriptor]s from the corresponding retrievables in Weaviate.
     *
     * WARNING: Batch updates are not supported in Weaviate. This method will do single inserts
     * which can cause significant performance issues. Also, the return value is not reliable as it
     * will return false if a single update fails. The others are not reverted though. So we have a partial success.
     *
     * @param items The [Iterable] of [Descriptor]s to delete.
     * @return `true` if all items were successfully deleted, `false` if any item failed to be deleted.
     */
    override fun deleteAll(items: Iterable<D>): Boolean {
        LOGGER.warn { "Batch deleting properties from Weaviate objects is not supported.\n" +
                "Running batch delete as single deletes.\n" }
        return items.all { this.descriptorProperty.unset(it) }
    }



}