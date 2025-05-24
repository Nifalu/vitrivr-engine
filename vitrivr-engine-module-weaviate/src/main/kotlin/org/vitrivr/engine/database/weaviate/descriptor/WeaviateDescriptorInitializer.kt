package org.vitrivr.engine.database.weaviate.descriptor

import org.vitrivr.engine.core.database.descriptor.DescriptorInitializer
import org.vitrivr.engine.core.model.descriptor.Descriptor
import org.vitrivr.engine.core.model.metamodel.Schema
import org.vitrivr.engine.database.weaviate.LOGGER
import org.vitrivr.engine.database.weaviate.WeaviateConnection
import org.vitrivr.engine.database.weaviate.properties.AbstractDescriptorProperty
import org.vitrivr.engine.database.weaviate.toDescriptorProperty

open class WeaviateDescriptorInitializer<D : Descriptor<*>>(
    final override val field: Schema.Field<*, D>,
    protected val connection: WeaviateConnection
) : DescriptorInitializer<D> {


    private val descriptorProperty: AbstractDescriptorProperty<D> = this.field.toDescriptorProperty()

    /**
     * Add the property to the Weaviate class.
     */
    override fun initialize() = this.descriptorProperty.initialize()


    /**
     * Nothing to do here as we don't have separate tables in Weaviate for the descriptors.
     */
    override fun deinitialize() {
        LOGGER.warn {
            "De-initialization of Weaviate property '${this.descriptorProperty.name}' is not supported. " +
                    "Remove it from affected retrievables by updating those retrievables."
        }
    }

    /**
     * Weaviate is always initialized.
     */
    override fun isInitialized(): Boolean = this.descriptorProperty.isInitialized()

    /**
     * Nothing to do here as we don't have separate tables in Weaviate for the descriptors.
     */
    override fun truncate() {
        LOGGER.warn {
            "Truncation of Weaviate property '${this.descriptorProperty.name}' is not supported. " +
                    "Clear it from affected retrievables by updating those retrievables."
        }
    }

}