package org.vitrivr.engine.database.weaviate.descriptor

import org.vitrivr.engine.core.database.descriptor.DescriptorWriter
import org.vitrivr.engine.core.model.metamodel.Schema
import org.vitrivr.engine.core.model.descriptor.Descriptor
import org.vitrivr.engine.database.weaviate.*
import org.vitrivr.engine.database.weaviate.LOGGER
import org.vitrivr.engine.database.weaviate.properties.AbstractDescriptorProperty

class WeaviateDescriptorWriter<D : Descriptor<*>>(override val field: Schema.Field<*, D>, override val connection: WeaviateConnection) : DescriptorWriter<D> {

    private val descriptorProperty: AbstractDescriptorProperty<D> = this.field.toDescriptorProperty()

    /**
     * Adds the given [Descriptor] to the corresponding retrievable in Weaviate.
     */
    override fun add(item: D): Boolean = this.descriptorProperty.set(item)


    /**
     * Adds the given [Descriptor]s to the corresponding retrievables in Weaviate.
     *
     * WARNING: Batch updates are not supported in Weaviate. This method will do single inserts
     * which can cause significant performance issues. Also, the return value is not reliable as it
     * will return false if a single update fails. The others are not reverted though. So we have a partial success.
     */
    override fun addAll(items: Iterable<D>): Boolean {
        LOGGER.warn { "Batch inserting properties to Weaviate objects is not supported.\n" +
        "Running batch insert as single inserts.\n" }
        return items.all { this.descriptorProperty.set(it) }
    }


    /**
     * Updates the given [Descriptor] in the corresponding retrievable in Weaviate.
     */
    override fun update(item: D): Boolean = this.descriptorProperty.set(item)

    /**
     * Deletes the given [Descriptor] from the corresponding retrievable in Weaviate.
     */
    override fun delete(item: D): Boolean = this.descriptorProperty.unset(item)

    /**
     * Deletes the given [Descriptor]s from the corresponding retrievables in Weaviate.
     *
     * WARNING: Batch updates are not supported in Weaviate. This method will do single inserts
     * which can cause significant performance issues. Also, the return value is not reliable as it
     * will return false if a single update fails. The others are not reverted though. So we have a partial success.
     */
    override fun deleteAll(items: Iterable<D>): Boolean {
        LOGGER.warn { "Batch deleting properties from Weaviate objects is not supported.\n" +
                "Running batch delete as single deletes.\n" }
        return items.all { this.descriptorProperty.unset(it) }
    }



}