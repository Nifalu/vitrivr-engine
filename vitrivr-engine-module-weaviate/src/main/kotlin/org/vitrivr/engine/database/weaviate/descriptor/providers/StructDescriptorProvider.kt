package org.vitrivr.engine.database.weaviate.descriptor.providers

import org.vitrivr.engine.core.database.Connection
import org.vitrivr.engine.core.database.descriptor.DescriptorInitializer
import org.vitrivr.engine.core.database.descriptor.DescriptorProvider
import org.vitrivr.engine.core.database.descriptor.DescriptorReader
import org.vitrivr.engine.core.database.descriptor.DescriptorWriter
import org.vitrivr.engine.core.model.descriptor.struct.StructDescriptor
import org.vitrivr.engine.core.model.metamodel.Schema

object StructDescriptorProvider : DescriptorProvider<StructDescriptor<*>> {
    override fun newInitializer(
        connection: Connection,
        field: Schema.Field<*, StructDescriptor<*>>
    ): DescriptorInitializer<StructDescriptor<*>> {
        TODO("Not yet implemented")
    }

    override fun newReader(
        connection: Connection,
        field: Schema.Field<*, StructDescriptor<*>>
    ): DescriptorReader<StructDescriptor<*>> {
        TODO("Not yet implemented")
    }

    override fun newWriter(
        connection: Connection,
        field: Schema.Field<*, StructDescriptor<*>>
    ): DescriptorWriter<StructDescriptor<*>> {
        TODO("Not yet implemented")
    }
}