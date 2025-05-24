package org.vitrivr.engine.database.weaviate.properties.scalar

import io.weaviate.client.v1.filters.WhereFilter
import io.weaviate.client.v1.schema.model.DataType
import io.weaviate.client.v1.schema.model.Property
import org.vitrivr.engine.core.model.descriptor.scalar.BooleanDescriptor
import org.vitrivr.engine.core.model.metamodel.Schema
import org.vitrivr.engine.core.model.query.bool.SimpleBooleanQuery
import org.vitrivr.engine.core.model.types.Value
import org.vitrivr.engine.database.weaviate.LOGGER
import org.vitrivr.engine.database.weaviate.WeaviateConnection
import org.vitrivr.engine.database.weaviate.toWeaviateOperator

import java.util.*

class BooleanDescriptorProperty(val field: Schema.Field<*, BooleanDescriptor>, connection: WeaviateConnection) : AbstractScalarDescriptorProperty<BooleanDescriptor, Value.Boolean, Boolean>(connection) {
    override val property: Property = Property.builder()
        .name(field.fieldName)
        .description("This ${field.fieldName} describes a boolean feature of a retrievable")
        .dataType(listOf(DataType.BOOLEAN))
        .build()

    override fun resultToDescriptor(value: Any, id: UUID): BooleanDescriptor? {
        return if (value is Boolean) {
            BooleanDescriptor(id, id, Value.Boolean(value), field)
        } else {
            null
        }
    }

    override fun buildWhereFilter(query: SimpleBooleanQuery<*>): WhereFilter {
        val value = query.value.value as? Boolean ?: run {
            LOGGER.error { "Invalid value for boolean query: ${query.value.value}. Expected a Boolean." }
            return WhereFilter.builder().build() // Return an empty filter if the value is invalid
        }
        return WhereFilter.builder()
            .path(this.property.name)
            .operator(query.comparison.toWeaviateOperator())
            .valueBoolean(value)
            .build()
    }
}