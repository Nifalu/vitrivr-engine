package org.vitrivr.engine.database.weaviate.properties.vector

import io.weaviate.client.v1.schema.model.Property
import io.weaviate.client.v1.data.model.WeaviateObject
import io.weaviate.client.v1.graphql.query.argument.NearVectorArgument
import io.weaviate.client.v1.graphql.query.fields.Field
import org.vitrivr.engine.core.model.descriptor.vector.FloatVectorDescriptor
import org.vitrivr.engine.core.model.metamodel.Schema
import org.vitrivr.engine.core.model.query.proximity.ProximityQuery
import org.vitrivr.engine.core.model.retrievable.RetrievableId
import org.vitrivr.engine.core.model.types.Value
import org.vitrivr.engine.database.weaviate.*
import org.vitrivr.engine.database.weaviate.LOGGER
import java.util.UUID

/**
 * A [AbstractVectorDescriptorProperty] implementation for [FloatVectorDescriptor]s in Weaviate.
 *
 * @author Nico Bachmann
 */
class FloatVectorDescriptorProperty(val field: Schema.Field<*, FloatVectorDescriptor>, connection: WeaviateConnection): AbstractVectorDescriptorProperty<FloatVectorDescriptor, Value.FloatVector, FloatArray>(connection) {

    /** The name for the descriptor [Property] in Weaviate. */
    override val name = "${DESCRIPTOR_ENTITY_PREFIX}_${field.fieldName}"

    /**
     * Convert a result value to a [FloatVectorDescriptor].
     */
    override fun resultToDescriptor(value: Any, id: UUID): FloatVectorDescriptor? {
        return when (value) {
            is FloatArray -> {
                FloatVectorDescriptor(id, id, Value.FloatVector(value), field)
            }
            is Array<*> -> {
                // Direct unsafe cast since Weaviate should only provide valid vectors
                @Suppress("UNCHECKED_CAST")
                val floatArray = (value as Array<Float>).toFloatArray()
                FloatVectorDescriptor(id, id, Value.FloatVector(floatArray), field)
            }
            else -> {
                LOGGER.warn { "Unexpected vector type: ${value::class}" }
                null
            }
        }
    }

    /**
     * Sets the [FloatVectorDescriptor] for the given [RetrievableId].
     */
    override fun set(descriptor: FloatVectorDescriptor): Boolean {
        val result = this.connection.client.data().updater()
            .withClassName(Constants.getCollectionName())
            .withID(descriptor.retrievableId.toString())
            .withMerge()
            .withVectors(
                mapOf(
                    this.name to descriptor.vector.value.toTypedArray()
                )
            ).run()

        if (result.hasErrors()) {
            LOGGER.error {
                "Failed to update retrievable '${descriptor.retrievableId}' with descriptor ${this.name} due to exception: ${result.error}"
            }
        }

        return result.result
    }

    /**
     * Proximity query for [FloatVectorDescriptor]s.
     */
    override fun query(query: org.vitrivr.engine.core.model.query.Query): Sequence<WeaviateObject> = when(query) {
        is ProximityQuery<*> -> {
            val queryVector = query.value.value as? FloatArray ?: run {
                LOGGER.error { "Invalid value for proximity query: ${query.value.value}. Expected a FloatArray." }
                return emptySequence()
            }

            val fields = mutableListOf<Field>()
            fields.add(Field.builder().name("_additional").fields(
                Field.builder().name("id").build(),
                Field.builder().name("distance").build()
            ).build())// get the id + distance
            fields.add(Field.builder().name(RETRIEVABLE_TYPE_PROPERTY_NAME).build()) // get the type

            if (query.fetchVector) {
                fields.add(
                    Field.builder().name("_additional").fields(Field.builder().name("vectors").fields(
                        Field.builder().name(this.name).build()).build()).build() // get the vectors if requested
                )
            }

            val result = this.connection.client.graphQL().get()
                .withClassName(Constants.getCollectionName())
                .withFields(*fields.toTypedArray())
                .withNearVector(
                    NearVectorArgument.builder()
                        .vector(queryVector.toTypedArray())
                        .targetVectors(arrayOf(this.name))
                        .build()
                )
                .withLimit(query.k.toInt())
                .run()

            result.toWeaviateObject() ?: emptySequence()
        } else -> {
            LOGGER.error { "Unsupported query type: ${query::class.simpleName}" }
            emptySequence()
        }
    }

}