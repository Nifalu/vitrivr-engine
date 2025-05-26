package org.vitrivr.engine.database.weaviate.descriptor.providers

import org.vitrivr.engine.core.database.Connection
import org.vitrivr.engine.core.database.descriptor.DescriptorProvider
import org.vitrivr.engine.core.model.descriptor.vector.VectorDescriptor
import org.vitrivr.engine.core.model.metamodel.Schema
import org.vitrivr.engine.database.weaviate.WeaviateConnection
import org.vitrivr.engine.database.weaviate.descriptor.WeaviateDescriptorInitializer
import org.vitrivr.engine.database.weaviate.descriptor.WeaviateDescriptorReader
import org.vitrivr.engine.database.weaviate.descriptor.WeaviateDescriptorWriter

object VectorDescriptorProvider : DescriptorProvider<VectorDescriptor<*, *>> {
    override fun newInitializer(connection: Connection, field: Schema.Field<*, VectorDescriptor<*, *>>) = WeaviateDescriptorInitializer(field, connection as WeaviateConnection)
    override fun newReader(connection: Connection, field: Schema.Field<*, VectorDescriptor<*, *>>) = WeaviateDescriptorReader(field, connection as WeaviateConnection)
    override fun newWriter(connection: Connection, field: Schema.Field<*, VectorDescriptor<*, *>>) = WeaviateDescriptorWriter(field, connection as WeaviateConnection)

}