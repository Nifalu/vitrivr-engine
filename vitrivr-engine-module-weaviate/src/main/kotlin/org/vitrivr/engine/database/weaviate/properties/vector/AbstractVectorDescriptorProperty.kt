package org.vitrivr.engine.database.weaviate.properties.vector

import org.vitrivr.engine.core.model.descriptor.vector.VectorDescriptor
import org.vitrivr.engine.core.model.metamodel.Schema
import org.vitrivr.engine.core.model.types.Value
import org.vitrivr.engine.database.weaviate.properties.AbstractDescriptorProperty

abstract class AbstractVectorDescriptorProperty<D: VectorDescriptor<D, V>, V: Value.Vector<S>, S>(field: Schema.Field<*, D>): AbstractDescriptorProperty<D> {
}