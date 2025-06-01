package org.vitrivr.engine.database.weaviate.properties

import io.weaviate.client.v1.data.model.WeaviateObject
import io.weaviate.client.v1.filters.Operator
import io.weaviate.client.v1.filters.WhereFilter
import io.weaviate.client.v1.graphql.query.argument.WhereArgument
import io.weaviate.client.v1.graphql.query.fields.Field
import io.weaviate.client.v1.schema.model.DataType
import io.weaviate.client.v1.schema.model.Property
import io.weaviate.client.v1.schema.model.Property.NestedProperty
import org.vitrivr.engine.core.model.descriptor.struct.StructDescriptor
import org.vitrivr.engine.core.model.metamodel.Schema
import org.vitrivr.engine.core.model.query.bool.SimpleBooleanQuery
import org.vitrivr.engine.core.model.query.fulltext.SimpleFulltextQuery
import org.vitrivr.engine.core.model.retrievable.Retrieved
import org.vitrivr.engine.core.model.retrievable.RetrievableId
import org.vitrivr.engine.core.model.retrievable.Retrievable
import org.vitrivr.engine.core.model.descriptor.DescriptorId
import org.vitrivr.engine.core.model.types.Type
import org.vitrivr.engine.core.model.types.Value
import org.vitrivr.engine.database.weaviate.*
import org.vitrivr.engine.database.weaviate.LOGGER
import org.vitrivr.engine.database.weaviate.toWeaviateObject
import java.util.*
import kotlin.reflect.full.primaryConstructor

/**
 * A [StructDescriptorProperty] represents a property in Weaviate that holds a [StructDescriptor].
 *
 * @author Nico Bachmann
 */
class StructDescriptorProperty<D: StructDescriptor<*>>(val field: Schema.Field<*, D>, val connection: WeaviateConnection) : AbstractDescriptorProperty<D>() {

    /** The name of the [StructDescriptorProperty] */
    override val name: String = field.fieldName

    /** Nested properties used in this [StructDescriptorProperty] */
    private val nestedProperties = mutableListOf<NestedProperty>()

    init {
        this.field.getPrototype().layout().forEach { attribute ->
            val nestedProperty = when (val type = attribute.type) {
                Type.Boolean -> NestedProperty.builder().name(attribute.name).dataType(listOf(DataType.BOOLEAN)).build()
                Type.Datetime -> NestedProperty.builder().name(attribute.name).dataType(listOf(DataType.DATE)).build()
                Type.Text -> NestedProperty.builder().name(attribute.name).dataType(listOf(DataType.TEXT)).build()
                Type.String -> NestedProperty.builder().name(attribute.name).dataType(listOf(DataType.TEXT)).build()
                Type.UUID -> NestedProperty.builder().name(attribute.name).dataType(listOf(DataType.UUID)).build()
                Type.Int -> NestedProperty.builder().name(attribute.name).dataType(listOf(DataType.INT)).build()
                else -> {
                    LOGGER.error { "Unsupported type $type for attribute ${attribute.name} in ${Constants.getCollectionName()}" }
                    throw IllegalArgumentException("Unsupported type $type for attribute ${attribute.name} in ${Constants.getCollectionName()}")
                }
            }
            nestedProperties.add(nestedProperty)
        }
    }

    /** Nested Fields to be included in queries */
    private val nestedFields = this.nestedProperties.map { Field.builder().name(it.name).build() }.toTypedArray()

    /** The [Property] that represents this [StructDescriptorProperty] in Weaviate */
    val property: Property = Property.builder()
        .name(field.fieldName)
        .description("This ${field.fieldName} describes a struct feature of a retrievable")
        .dataType(listOf(DataType.OBJECT))
        .nestedProperties(nestedProperties)
        .build()

    /**
     * Initializes the [StructDescriptorProperty] in Weaviate.
     *
     * Add the property to the collection defined in [Constants.getCollectionName()].
     */
    override fun initialize() {
        val result = this.connection.client.schema().propertyCreator()
            .withClassName(Constants.getCollectionName())
            .withProperty(this.property)
            .run()

        if (!isValid(result)) {
            LOGGER.error { "Failed to initialize property '${this.property.name}' in Weaviate due to error: ${result.error}" }
        }
    }

    /**
     * Checks if the [StructDescriptorProperty] exists in Weaviate.
     */
    override fun isInitialized(): Boolean {
        this.connection.client.schema().classGetter().withClassName(Constants.getCollectionName()).run().let { result ->
            if (isValid(result)) {
                return result.result.properties.any { it.name == this.property.name && it.dataType == this.property.dataType }
            }
            return false
        }
    }

    /**
     * Returns the [StructDescriptor] of type [D] that has the provided [retrievableId].
     *
     * Note: As descriptors are stored as properties of retrievables, the [DescriptorId] equals the [RetrievableId].
     *
     * @param retrievableId The [RetrievableId] (or [DescriptorId]) of the [StructDescriptor] to return.
     * @return The [StructDescriptor] with the given [retrievableId], or null if not found.
     */
    override fun get(retrievableId: UUID): D? {
        val result = this.connection.client.data().objectsGetter()
            .withClassName(Constants.getCollectionName())
            .withID(retrievableId.toString())
            .run()

        if (isValid(result)) {
            result.result.first().properties[this.property.name]?.let {
                val id = UUID.fromString(result.result.first().id)
                return this.resultToDescriptor(it, id)
            } ?: return null
        }
        return null
    }

    /**
     * Returns all [StructDescriptor]s that are associated with the provided [retrievableIds].
     *
     * Note: As descriptors are stored as properties of retrievables, the [DescriptorId] equals the [RetrievableId].
     *
     * @param retrievableIds An [Iterable] of [UUID]s representing the [RetrievableId]s for which to fetch the descriptors.
     * @return A [Sequence] of [StructDescriptor]s associated with the provided [retrievableIds].
     */
    override fun getAll(retrievableIds: Iterable<UUID>): Sequence<D> {
        val retrievableIdsArray = retrievableIds.map { it.toString() }.toTypedArray()
        val idFilter = WhereFilter.builder()
            .path("id")
            .operator(Operator.ContainsAny)
            .valueText(*retrievableIdsArray)
            .build()

        val result = this.connection.client.graphQL().get()
            .withClassName(Constants.getCollectionName())
            .withFields(
                Field.builder().name("_additional").fields(
                    Field.builder().name("id").build()).build(),
                Field.builder().name(this.property.name).fields(*nestedFields).build())
            .withWhere(WhereArgument.builder().filter(idFilter).build())
            .run()

        return if (isValid(result)) {
            wObjectsToDescriptors(result.toWeaviateObject() ?: emptySequence())
        } else {
            emptySequence()
        }
    }

    /**
     * Returns all [StructDescriptor]s of type [D] that are associated with the retrievable collection.
     *
     * @return A [Sequence] of all [StructDescriptor]s in the retrievable collection.
     */
    override fun getAll() : Sequence<D> {
        val result = this.connection.client.graphQL().get()
            .withClassName(Constants.getCollectionName())
            .withFields(
                Field.builder().name("_additional").fields(
                    Field.builder().name("id").build()).build(),
                Field.builder().name(this.property.name).fields(*nestedFields).build())
            .run()

        return if (isValid(result)) {
            wObjectsToDescriptors(result.toWeaviateObject() ?: emptySequence())
        } else {
            emptySequence()
        }
    }

    /**
     * Updates the given [StructDescriptor] in Weaviate.
     *
     * @param descriptor The [StructDescriptor] to update.
     * @return True if the update was successful, false otherwise.
     */
    override fun set(descriptor: D): Boolean {
        val result = this.connection.client.data().updater()
            .withClassName(Constants.getCollectionName())
            .withID(descriptor.retrievableId.toString())
            .withMerge()
            .withProperties(
                mapOf(
                    this.property.name to descriptor.values()
                )
            )
            .run()

        if (result.hasErrors()) {
            LOGGER.error {
                "Failed to update retrievable '${descriptor.retrievableId}' with descriptor ${this.property.name} due to exception: ${result.error}"
            }
        }

        return result.result
    }

    /**
     * Checks if the [StructDescriptorProperty] is set for the given [retrievableId].
     *
     * @param retrievableId The [UUID] of the retrievable to check.
     * @return True if the property is set, false otherwise.
     */
    override fun isSet(retrievableId: UUID): Boolean {
        /* get the retrievable */
        val result = this.connection.client.data().objectsGetter()
            .withClassName(Constants.getCollectionName())
            .withID(retrievableId.toString())
            .run()

        if (result.hasErrors()) {
            LOGGER.error { "Failed to fetch descriptor $retrievableId due to error." }
            return false
        }

        return result.result.first().properties.containsKey(this.property.name)
    }


    /**
     * Unsets (deletes) the [StructDescriptor] from its [Retrievable]
     */
    override fun unset(descriptor: D): Boolean {
        val result = this.connection.client.data().objectsGetter()
            .withClassName(Constants.getCollectionName())
            .withID(descriptor.retrievableId.toString())
            .withVector()
            .run()

        if (result.hasErrors()) {
            println("failed to delete descriptor ${this.property.name} from retrievable ${descriptor.retrievableId}: ${result.error}")
            return false
        }

        val wObject = result.result.firstOrNull() ?: return true // nothing to delete
        wObject.properties.remove(this.property.name) // remove the property
        val update = this.connection.client.data().updater() // replace the entire object.
            .withClassName(Constants.getCollectionName())
            .withID(descriptor.retrievableId.toString())
            .withProperties(wObject.properties)
            .withVectors(wObject.vectors)
            .run()

        if (update.hasErrors()) {
            println("failed to delete descriptor ${this.property.name} from retrievable ${descriptor.retrievableId}: ${update.error}")
            return false
        }

        return true
    }

    /**
     * Queries the [StructDescriptorProperty] and returns a sequence of [Retrieved] objects.
     *
     * @param query The [org.vitrivr.engine.core.model.query.Query] to execute.
     * @return A [Sequence] of [Retrieved] objects that match the query.
     */
    override fun queryRetrievable(query: org.vitrivr.engine.core.model.query.Query): Sequence<Retrieved> = wObjectsToRetrieved(query(query))

    /**
     * Queries the [StructDescriptorProperty] and returns a sequence of [StructDescriptor]s.
     *
     * @param query The [org.vitrivr.engine.core.model.query.Query] to execute.
     * @return A [Sequence] of [StructDescriptor]s that match the query.
     */
    override fun queryProperty(query: org.vitrivr.engine.core.model.query.Query): Sequence<D> = wObjectsToDescriptors(query(query))

    /**
     * Executes a query against the Weaviate collection and returns a sequence of [WeaviateObject]s.
     *
     * @param query The [org.vitrivr.engine.core.model.query.Query] to execute.
     * @return A [Sequence] of [WeaviateObject]s that match the query.
     */
    private fun query(query: org.vitrivr.engine.core.model.query.Query): Sequence<WeaviateObject>  {
        val whereFilter = this.buildWhereFilter(query)

        val result = this.connection.client.graphQL().get()
            .withClassName(Constants.getCollectionName())
            .withFields(
                Field.builder().name("_additional").fields(
                    Field.builder().name("id").build()).build(),
                Field.builder().name(this.property.name).fields(*nestedFields).build())
            .withWhere(WhereArgument.builder().filter(whereFilter).build())
            .run()

        if (!isValid(result)) {
            emptySequence<WeaviateObject>()
        }

        return result.toWeaviateObject() ?: emptySequence()
    }

    /**
     * Builds a [WhereFilter] for the given [org.vitrivr.engine.core.model.query.Query].
     *
     * @param query The [org.vitrivr.engine.core.model.query.Query] to build the filter for.
     * @return A [WhereFilter] that can be used in Weaviate queries.
     */
    fun buildWhereFilter(query: org.vitrivr.engine.core.model.query.Query): WhereFilter = when (query) {
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
        require(query.attributeName != null) { "Attribute name must not be null." }
        val value = query.value.value as? Boolean ?: run {
            LOGGER.error { "Invalid value for text query: ${query.value.value}. Expected a String." }
            return WhereFilter.builder().build() // Return an empty filter if the value is invalid
        }
        return WhereFilter.builder()
            .path(this.property.name, query.attributeName)
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
        require(query.attributeName != null) { "Attribute name must not be null." }
        val value = query.value.value as? String ?: run {
            LOGGER.error { "Invalid value for text query: ${query.value.value}. Expected a String." }
            return WhereFilter.builder().build() // Return an empty filter if the value is invalid
        }
        return WhereFilter.builder()
            .path(this.property.name, query.attributeName)
            .operator(Operator.Like)
            .valueText(value)
            .build()
    }

    /**
     * Converts a result from Weaviate into a [StructDescriptor].
     *
     * @param value The value from Weaviate that represents the descriptor.
     * @param id The [UUID] of the descriptor.
     * @return A [StructDescriptor] created from the provided value.
     */
    override fun resultToDescriptor(value: Any, id: UUID): D {

        val constructor = this.field.analyser.descriptorClass.primaryConstructor ?: throw IllegalStateException("Provided type ${this.field.analyser.descriptorClass} does not have a primary constructor.")

        val valueMap = mutableMapOf<String, Value<*>>()
        (value as? Map<*,*>)?.forEach { (name, attribute) ->
            if (name == null || attribute == null) {
                LOGGER.warn { "Invalid attribute in struct descriptor: name=$name, attribute=$attribute" }
            } else
                valueMap[name as String] = Value.of(attribute)
        }
        val parameters: MutableList<Any?> = mutableListOf(
            id,
            id,
            valueMap,
            this.field
        )

        return constructor.call(*parameters.toTypedArray())

    }

    /**
     * Converts a sequence of [WeaviateObject]s to a sequence of [StructDescriptor]s.
     *
     * @param weaviateObjects The sequence of [WeaviateObject]s to convert.
     * @return A sequence of [StructDescriptor]s.
     */
    override fun wObjectsToDescriptors(weaviateObjects: Sequence<WeaviateObject>): Sequence<D> = weaviateObjects.mapNotNull { wObj ->
        val value = wObj.properties[this.property.name] ?: return@mapNotNull null
        val id = UUID.fromString(wObj.id)
        resultToDescriptor(value, id)
    }

}