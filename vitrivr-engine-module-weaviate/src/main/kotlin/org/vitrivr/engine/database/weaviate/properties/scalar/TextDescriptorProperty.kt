package org.vitrivr.engine.database.weaviate.properties.scalar

import io.weaviate.client.v1.filters.Operator
import io.weaviate.client.v1.filters.WhereFilter
import io.weaviate.client.v1.schema.model.DataType
import io.weaviate.client.v1.schema.model.Property
import org.vitrivr.engine.core.model.descriptor.scalar.TextDescriptor
import org.vitrivr.engine.core.model.metamodel.Schema
import org.vitrivr.engine.core.model.query.bool.SimpleBooleanQuery
import org.vitrivr.engine.core.model.query.fulltext.SimpleFulltextQuery
import org.vitrivr.engine.core.model.types.Value
import org.vitrivr.engine.database.weaviate.LOGGER
import org.vitrivr.engine.database.weaviate.WeaviateConnection
import org.vitrivr.engine.database.weaviate.toWeaviateOperator
import java.util.*

/**
 * A [TextDescriptorProperty] implementation for handling [TextDescriptor]s in Weaviate.
 *
 * @author Nico Bachmann
 */
class TextDescriptorProperty(val field: Schema.Field<*, TextDescriptor>, connection: WeaviateConnection) : AbstractScalarDescriptorProperty<TextDescriptor, Value.Text, String>(connection) {

    /** The name of this [Property] */
    override val name = field.fieldName

    /** The [Property] in Weaviate that this descriptor corresponds to */
    override val property: Property = Property.builder()
        .name(field.fieldName)
        .description("This ${field.fieldName} describes a text feature of a retrievable")
        .dataType(listOf(DataType.TEXT))
        .build()

    /**
     * Converts a result value to a [TextDescriptor].
     *
     * @param value The value to convert, expected to be a [String].
     * @param id The [UUID] identifier for the descriptor.
     * @return A [TextDescriptor] if the value is a [String], otherwise null.
     */
    override fun resultToDescriptor(value: Any, id: UUID): TextDescriptor? {
        return if (value is String) {
            TextDescriptor(id, id, Value.Text(value), field)
        } else {
            null
        }
    }

    /**
     * Builds a [WhereFilter] for a [org.vitrivr.engine.core.model.query.Query].
     *
     * @param query The query to build the filter for.
     * @return A [WhereFilter] that can be used in Weaviate queries.
     */
    override fun buildWhereFilter(query: org.vitrivr.engine.core.model.query.Query): WhereFilter = when (query) {
        is SimpleBooleanQuery<*> -> this.buildWhereFilter(query)
        is SimpleFulltextQuery -> this.buildWhereFilter(query)
        else -> throw UnsupportedOperationException("Unsupported query type ${query::class.simpleName}.")
    }

    /**
     * Builds a [WhereFilter] for a [SimpleBooleanQuery].
     *
     * @param query The [SimpleBooleanQuery] to build the filter for.
     * @return A [WhereFilter] that can be used in Weaviate queries.
     */
    private fun buildWhereFilter(query: SimpleBooleanQuery<*>): WhereFilter {
        val value = query.value.value as? Boolean ?: run {
            LOGGER.error { "Invalid value for text query: ${query.value.value}. Expected a Boolean." }
            return WhereFilter.builder().build() // Return an empty filter if the value is invalid
        }
        return WhereFilter.builder()
            .path(this.property.name)
            .operator(query.comparison.toWeaviateOperator())
            .valueBoolean(value)
            .build()
    }

    /**
     * Builds a [WhereFilter] for a [SimpleFulltextQuery].
     *
     * @param query The [SimpleFulltextQuery] to build the filter for.
     * @return A [WhereFilter] that can be used in Weaviate queries.
     */
    private fun buildWhereFilter(query: SimpleFulltextQuery): WhereFilter {
        val value = query.value.value as? String ?: run {
            LOGGER.error { "Invalid value for text query: ${query.value.value}. Expected a String." }
            return WhereFilter.builder().build() // Return an empty filter if the value is invalid
        }
        return WhereFilter.builder()
            .path(this.property.name)
            .operator(Operator.Like)
            .valueText(value)
            .build()
    }
}