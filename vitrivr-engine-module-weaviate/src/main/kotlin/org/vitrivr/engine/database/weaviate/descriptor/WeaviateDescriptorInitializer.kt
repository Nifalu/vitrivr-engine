package org.vitrivr.engine.database.weaviate.descriptor

import org.vitrivr.engine.core.database.descriptor.DescriptorInitializer
import org.vitrivr.engine.core.model.descriptor.Descriptor
import org.vitrivr.engine.core.model.metamodel.Schema
import org.vitrivr.engine.database.weaviate.LOGGER
import org.vitrivr.engine.database.weaviate.WeaviateConnection
import org.vitrivr.engine.database.weaviate.properties.AbstractDescriptorProperty
import org.vitrivr.engine.database.weaviate.toDescriptorProperty
import io.weaviate.client.v1.schema.model.Property



/**
 * A [DescriptorInitializer] implementation for handling [Descriptor]s in Weaviate.
 *
 * @author Nico Bachmann
 */
open class WeaviateDescriptorInitializer<D : Descriptor<*>>(
    final override val field: Schema.Field<*, D>,
    protected val connection: WeaviateConnection
) : DescriptorInitializer<D> {


    /** The [AbstractDescriptorProperty] that this initializer uses to interact with Weaviate. */
    private val descriptorProperty: AbstractDescriptorProperty<D> = this.field.toDescriptorProperty()

    /**
     * Adds the [Property] for this [Descriptor] to Weaviate.
     */
    override fun initialize() = this.descriptorProperty.initialize()


    /**
     * De-initialization of Weaviate properties is not supported.
     */
    override fun deinitialize() {
        LOGGER.warn {
            "De-initialization of Weaviate property '${this.descriptorProperty.name}' is not supported. " +
                    "Remove it from affected retrievables by updating those retrievables."
        }
    }

    /**
     * Checks if the descriptor property is initialized in Weaviate.
     *
     * @return `true` if the property is initialized, `false` otherwise.
     */
    override fun isInitialized(): Boolean = this.descriptorProperty.isInitialized()

    /**
     * Truncation of Weaviate properties is not supported.
     */
    override fun truncate() {
        LOGGER.warn {
            "Truncation of Weaviate property '${this.descriptorProperty.name}' is not supported. " +
                    "Clear it from affected retrievables by updating those retrievables."
        }
    }

}