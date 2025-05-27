package org.vitrivr.engine.database.weaviate
import org.vitrivr.engine.core.model.descriptor.Descriptor
import org.vitrivr.engine.core.model.descriptor.scalar.*
import org.vitrivr.engine.core.model.metamodel.Schema

import io.weaviate.client.WeaviateClient
import io.weaviate.client.v1.data.model.WeaviateObject
import io.weaviate.client.v1.graphql.model.GraphQLResponse
import io.weaviate.client.base.Result
import io.weaviate.client.v1.filters.Operator
import org.vitrivr.engine.core.model.descriptor.vector.FloatVectorDescriptor
import org.vitrivr.engine.core.model.query.basics.ComparisonOperator
import org.vitrivr.engine.database.weaviate.properties.AbstractDescriptorProperty
import org.vitrivr.engine.database.weaviate.properties.scalar.BooleanDescriptorProperty
import org.vitrivr.engine.database.weaviate.properties.vector.FloatVectorDescriptorProperty
import java.util.logging.Logger


@Suppress("UNCHECKED_CAST")
internal fun <D: Descriptor<*>> Schema.Field<*, D>.toDescriptorProperty() = when (this.analyser.prototype(this)) {
    is BooleanDescriptor -> BooleanDescriptorProperty(this as Schema.Field<*, BooleanDescriptor>, this.connection as WeaviateConnection)
    is FloatVectorDescriptor -> FloatVectorDescriptorProperty(this as Schema.Field<*, FloatVectorDescriptor>, this.connection as WeaviateConnection)
    /*
    is ByteDescriptor -> ByteProperty(this as Schema.Field<*, ByteDescriptor>, this.connection as WeaviateConnection)
    is DoubleDescriptor -> DoubleProperty(this as Schema.Field<*, DoubleDescriptor>, this.connection as WeaviateConnection)
    is FloatDescriptor -> FloatProperty(this as Schema.Field<*, FloatDescriptor>, this.connection as WeaviateConnection)
    is IntDescriptor -> IntProperty(this as Schema.Field<*, IntDescriptor>, this.connection as WeaviateConnection)
    is LongDescriptor -> LongProperty(this as Schema.Field<*, LongDescriptor>, this.connection as WeaviateConnection)
    is ShortDescriptor -> ShortProperty(this as Schema.Field<*, ShortDescriptor>, this.connection as WeaviateConnection)
    is StringDescriptor -> StringProperty(this as Schema.Field<*, StringDescriptor>, this.connection as WeaviateConnection)
    is TextDescriptor -> TextProperty(this as Schema.Field<*, TextDescriptor>, this.connection as WeaviateConnection)
    is StructDescriptor<*> -> StructDescriptorTable(this as Schema.Field<*, StructDescriptor<*>>, this.connection as WeaviateConnection)
     */
    else -> throw IllegalArgumentException("Unsupported descriptor type: ${this.analyser.prototype(this)}")
} as AbstractDescriptorProperty<D>

/**
 * Extension function that converts a [Result] of type [GraphQLResponse] to a sequence of [WeaviateObject].
 * 
 * The [GraphQLResponse] is in JSON format which this function parses.
 */
internal fun <T>  Result<GraphQLResponse<T>?>.toWeaviateObject(): Sequence<WeaviateObject>? {
    
    /* First check if the response has errors */
    if (this.hasErrors()) {
        LOGGER.error { "Error parsing GraphQL Response: $this" }
        return null
    }

    /* Extract the different levels of the Json */
    val data = this.result?.data
    if (data !is Map<*, *>) {
        LOGGER.error { "GraphQL Response is not a Map: $data" }
        return null
    }

    val getSection = data["Get"]
    if (getSection !is Map<*, *>) {
        LOGGER.error { "Get section in GraphQL Response is not a Map: $getSection" }
        return null
    }

    /* Get the list with all the retrievables in the response */
    val retrievables = getSection[Constants.getCollectionName()]
    if (retrievables !is List<*>) {
        LOGGER.error { "No objects found in '${Constants.getCollectionName()}'... : $retrievables" }
        return null
    }


    return retrievables.asSequence().mapNotNull { item ->
        val contents = item as? Map<*, *> ?: return@mapNotNull warnAndSkip(item)
        val additional = contents["_additional"] as? Map<*,*> ?: return@mapNotNull warnAndSkip(item)
        val id = additional["id"] as? String ?: return@mapNotNull warnAndSkip(item)
        val distance = additional["distance"] as? Double // vector similarity score

        /* If the object has namedVectors extract them, otherwise just take an emptyMap */
        val vectorList = additional["vectors"] as? Map<*,*>
        val vectors: Map<String, Array<Float>> =
            vectorList?.filterValues { it != null }?.mapKeys { it.key.toString() }?.mapValues { (_, value) ->
                (value as List<*>).map {
                    when (it) {
                        is Float -> it
                        is Number -> it.toFloat()
                        else -> throw IllegalArgumentException("Invalid element type: ${it?.javaClass} within vector")
                    }
                }.toTypedArray() }
                ?: emptyMap()

        /* other properties are right in the item map*/
        val properties = item
            .filterKeys { it != "_additional" }
            .filterValues { it != null }
            .mapKeys { it.key.toString() }
            .mapValues { it.value as Any }

        WeaviateObject.builder()
            .className(Constants.getCollectionName())
            .id(id)
            .additional(mapOf("distance" to (distance)))
            .properties(properties)
            .vectors(vectors)
            .build()
    }
}


private fun warnAndSkip(item: Any?): Nothing? {
    LOGGER.warn { "GraphQLResponse Parser found an unexpected item: ${item.toString()}" }
    return null
}

internal fun WeaviateClient.findPredicateProperties(): List<String> {
    this.schema().classGetter().withClassName(Constants.getCollectionName()).run().let { result ->

        if (result.hasErrors()) {
            LOGGER.error { "Error retrieving schema: ${result.error}" }
            return emptyList()
        }

        if (result.result == null) {
            LOGGER.error { "Error retrieving schema: ${result.error}" }
            return emptyList()
        }

        return result.result.properties
            .filter { it.dataType.contains(Constants.getCollectionName()) }
            .map { it.name }
    }
}

internal fun ComparisonOperator.toWeaviateOperator(): String = when (this) {
    ComparisonOperator.EQ -> Operator.Equal
    ComparisonOperator.NEQ -> Operator.NotEqual
    ComparisonOperator.GEQ -> Operator.GreaterThanEqual
    ComparisonOperator.GR -> Operator.GreaterThan
    ComparisonOperator.LEQ -> Operator.LessThanEqual
    ComparisonOperator.LE -> Operator.LessThan
    ComparisonOperator.LIKE -> Operator.Like
    else -> throw IllegalArgumentException("Unsupported comparison operator: $this")
}

/**
 * Extension function that ensures the first character of a string is lowercase.
 */
internal fun String.firstLowerCase(): String {
    if (this.isEmpty()) {
        return this
    }
    return this[0].lowercaseChar() + this.substring(1)
}

/**
 * Extension function that ensures the first character of a string is uppercase.
 */
internal fun String.firstUpperCase(): String {
    if (this.isEmpty()) {
        return this
    }
    return this[0].uppercaseChar() + this.substring(1)
}