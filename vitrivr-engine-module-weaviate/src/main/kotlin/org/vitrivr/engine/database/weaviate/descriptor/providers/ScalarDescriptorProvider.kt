package org.vitrivr.engine.database.weaviate.descriptor.providers

import org.vitrivr.engine.core.database.Connection
import org.vitrivr.engine.core.database.descriptor.DescriptorProvider
import org.vitrivr.engine.core.model.descriptor.scalar.ScalarDescriptor
import org.vitrivr.engine.core.model.metamodel.Schema
import org.vitrivr.engine.database.weaviate.WeaviateConnection
import org.vitrivr.engine.database.weaviate.descriptor.WeaviateDescriptorInitializer
import org.vitrivr.engine.database.weaviate.descriptor.WeaviateDescriptorReader
import org.vitrivr.engine.database.weaviate.descriptor.WeaviateDescriptorWriter

object ScalarDescriptorProvider : DescriptorProvider<ScalarDescriptor<*, *>> {
    override fun newInitializer(connection: Connection, field: Schema.Field<*, ScalarDescriptor<*, *>>) = WeaviateDescriptorInitializer(field, connection as WeaviateConnection)
    override fun newReader(connection: Connection, field: Schema.Field<*, ScalarDescriptor<*, *>>) = WeaviateDescriptorReader(field, connection as WeaviateConnection)
    override fun newWriter(connection: Connection, field: Schema.Field<*, ScalarDescriptor<*, *>>) = WeaviateDescriptorWriter(field, connection as WeaviateConnection)
}