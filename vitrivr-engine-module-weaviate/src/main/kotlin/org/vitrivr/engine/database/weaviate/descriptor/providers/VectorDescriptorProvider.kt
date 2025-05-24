package org.vitrivr.engine.database.weaviate.descriptor.providers

import org.vitrivr.engine.core.database.Connection
import org.vitrivr.engine.core.database.descriptor.DescriptorInitializer
import org.vitrivr.engine.core.database.descriptor.DescriptorProvider
import org.vitrivr.engine.core.database.descriptor.DescriptorReader
import org.vitrivr.engine.core.database.descriptor.DescriptorWriter
import org.vitrivr.engine.core.model.descriptor.vector.VectorDescriptor
import org.vitrivr.engine.core.model.metamodel.Schema

object VectorDescriptorProvider : DescriptorProvider<VectorDescriptor<*, *>> {
    override fun newInitializer(
        connection: Connection,
        field: Schema.Field<*, VectorDescriptor<*, *>>
    ): DescriptorInitializer<VectorDescriptor<*, *>> {
        TODO("Not yet implemented")
    }

    override fun newReader(
        connection: Connection,
        field: Schema.Field<*, VectorDescriptor<*, *>>
    ): DescriptorReader<VectorDescriptor<*, *>> {
        TODO("Not yet implemented")
    }

    override fun newWriter(
        connection: Connection,
        field: Schema.Field<*, VectorDescriptor<*, *>>
    ): DescriptorWriter<VectorDescriptor<*, *>> {
        TODO("Not yet implemented")
    }
}