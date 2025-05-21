package org.vitrivr.engine.database.weaviate.retrievable

import io.weaviate.client.v1.filters.Operator
import io.weaviate.client.v1.filters.WhereFilter
import io.weaviate.client.v1.graphql.query.argument.WhereArgument
import org.vitrivr.engine.core.database.retrievable.RetrievableReader
import org.vitrivr.engine.core.model.relationship.Relationship
import org.vitrivr.engine.core.model.retrievable.RetrievableId
import org.vitrivr.engine.core.model.retrievable.Retrieved

import io.weaviate.client.v1.graphql.query.fields.Field
import org.vitrivr.engine.database.weaviate.*
import org.vitrivr.engine.database.weaviate.LOGGER
import java.util.*

class WeaviateRetrievableReader(override val connection: WeaviateConnection): RetrievableReader {

    /**
     * Returns the [Retrieved] that matches the provided [RetrievableId]
     *
     * NOTE: Technically this query receives all properties (descriptors) of this retrievable.
     * We just ignore them all and only use id and type. This is probably not efficient at all
     * but would probably require a major refactoring of the vitrivr-engine-core to change this.
     *
     * @param id The [RetrievableId]
     */
    override fun get(id: RetrievableId): Retrieved? {
        val result = connection.client.data().objectsGetter()
            .withClassName(RETRIEVABLE_ENTITY_NAME)
            .withID(id.toString())
            .withLimit(1)
            .run()

        if (result.hasErrors()) {
            LOGGER.error { "Error retrieving retrievable with ID $id: ${result.error}" }
            return null
        }

        if (result.result != null && result.result.isNotEmpty()) {
            result.result.first().let { o ->
                return Retrieved(
                    UUID.fromString(o.id),
                    o.properties["type"] as? String ?: "unknown")
            }
        } else {
            LOGGER.warn { "No retrievable found with ID $id." }
            return null
        }
    }


    /**
     * Returns all [Retrieved]s that match any of the provided [RetrievableId]
     *
     * NOTE: Technically this query receives all properties (descriptors) of this retrievable.
     * We just ignore them all and only use id and type. This is probably not efficient at all
     * but would probably require a major refactoring of the vitrivr-engine-core to change this.
     */
    override fun getAll(ids: Iterable<RetrievableId>): Sequence<Retrieved> {
        val idArray = ids.map(UUID::toString).toTypedArray()
        val whereArgument = WhereArgument.builder()
            .filter(
                WhereFilter.builder()
                    .path("id")
                    .operator(Operator.ContainsAny)
                    .valueText(*idArray)
                    .build())
            .build()

        val result = connection.client.graphQL().get()
            .withClassName(RETRIEVABLE_ENTITY_NAME)
            .withFields(
                Field.builder().name("id").build(),
                Field.builder().name(RETRIEVABLE_TYPE_PROPERTY_NAME).build()
            )
            .withWhere(whereArgument)
            .run().toWeaviateObject()

        result ?: return emptySequence()
        return result.map { o ->
            Retrieved(
                UUID.fromString(o.id),
                o.properties[RETRIEVABLE_TYPE_PROPERTY_NAME] as? String ?: "unknown"
            )
        }
    }

    /**
     * Returns all [Retrieved]s in the retrievable collection.
     *
     * NOTE: Technically this query receives all properties (descriptors) of this retrievable.
     * We just ignore them all and only use id and type. This is probably not efficient at all
     * but would probably require a major refactoring of the vitrivr-engine-core to change this.
     */
    override fun getAll(): Sequence<Retrieved> {
        val result = connection.client.graphQL().get()
            .withClassName(RETRIEVABLE_ENTITY_NAME)
            .withFields(
                Field.builder().name("_additional").fields(
                    Field.builder().name("id").build()
                ).build(),
                Field.builder().name(RETRIEVABLE_TYPE_PROPERTY_NAME).build()
            )
            .run().toWeaviateObject()

        result ?: return emptySequence()
        return result.map { o ->
            Retrieved(
                UUID.fromString(o.id),
                o.properties[RETRIEVABLE_TYPE_PROPERTY_NAME] as? String ?: "unknown"
            )
        }
    }

    /**
     * Checks whether a [RetrievableId] exists.
     */
    override fun exists(id: RetrievableId): Boolean {
        val result = connection.client.data().checker()
            .withClassName(RETRIEVABLE_ENTITY_NAME)
            .withID(id.toString())
            .run()
        if (result.hasErrors()) {
            LOGGER.error { "Error checking existence of retrievable with ID $id: ${result.error}" }
            return false
        }
        return result.result
    }


    /**
     * Returns all [Relationship]s that match the provided [RetrievableId]s.
     *
     * NOTE: This method was originally designed for a relational table-like structure
     * where we can easily put filters on columns and return the remaining rows.
     *
     * In this Weaviate model the relationships are stored in the retrievable itself.
     * Filtering for [objectIds] i.E looking for retrievables that are being referenced by
     * something else or just looking for different relationship types is not supported very
     * well by Weaviate and requires this awfully complex query down here.
     *
     * @param subjectIds The [RetrievableId]s of the subjects to consider. If empty, all subjects are considered.
     * @param predicates The [predicates] to consider. If empty, all predicates are considered
     * @param objectIds The [RetrievableId]s of the objects to consider.  If empty, all subjects are considered.
     */
    override fun getConnections(
        subjectIds: Collection<RetrievableId>,
        predicates: Collection<String>,
        objectIds: Collection<RetrievableId>
    ): Sequence<Relationship.ById> {

        /**
         * Helper function that builds the fields for the individual predicates.
         */
        fun buildPredicateFields(predicates: Array<String>): MutableList<Field> {
            val fields = mutableListOf(
                Field.builder().name("_additional").fields(
                    Field.builder().name("id").build()
                ).build(),
                Field.builder().name(RETRIEVABLE_TYPE_PROPERTY_NAME).build()
            )

            for (predicate in predicates) {
                val refField = Field.builder()
                    .name(predicate)
                    .fields(Field.builder().name("... on Retrievable") // "inline fragment" or something like that lol
                            .fields(Field.builder().name("_additional")
                                    .fields(Field.builder().name("id").build())
                                    .build())
                            .build())
                    .build()
                fields.add(refField)
            }
            return fields
        }

        /**
         * Helper function that builds the filter for the predicates.
         * We cannot search for the predicate only, we need to search for some value which is
         * why we check if the value is not empty, i.E taking all retrievables that have a predicate
         */
        fun buildPredicateWhereFilter(predicates: Array<String>): WhereFilter {
            val hasPredicateFilter = predicates.map { p ->
                WhereFilter.builder()
                    .path(p, RETRIEVABLE_ENTITY_NAME, "id")
                    .operator(Operator.NotEqual)
                    .valueText("")
                    .build()
            }
            val atLeastOneExistingPredicateFilter = WhereFilter.builder()
                .operator(Operator.Or)
                .operands(*hasPredicateFilter.toTypedArray())
                .build()

            return atLeastOneExistingPredicateFilter
        }

        val existingPredicates = connection.client.findPredicateProperties().toTypedArray()
        val subjectsArray = subjectIds.map(UUID::toString).toTypedArray()
        val predicatesArray = predicates.toTypedArray()
        val objectsArray = objectIds.map(UUID::toString).toTypedArray()
        val filters = mutableListOf<WhereFilter>()

        /**
         *  Add filters for the subjectIds and only take retrievables that do have at least
         *  one predicate property. (i.e. they are in a relationship with something)
         */
        if (subjectsArray.isNotEmpty()) {
            /* Only take the requested id's into account */
            val idFilter = WhereFilter.builder()
                .path("id")
                .operator(Operator.ContainsAny)
                .valueText(*subjectsArray) // must have a specific id
                .build()

            filters.add(idFilter)
            filters.add(buildPredicateWhereFilter(existingPredicates))
        }

        val fields: MutableList<Field> = mutableListOf(
            Field.builder().name("_additional").fields(
                Field.builder().name("id").build()
            ).build(),
            Field.builder().name(RETRIEVABLE_TYPE_PROPERTY_NAME).build()
        )

        /**
         *  Filter for specific predicates only. We're also interested in the [RetrievableId]s
         *  of the referenced objects. So we need to add fields for each predicate we want to
         *  retrieve. Either for our selection or for all existing predicates.
         */
        if (predicatesArray.isNotEmpty()) {
            filters.add(buildPredicateWhereFilter(predicatesArray))
            /* we want to retrieve the predicates per retrievable */
            fields.addAll(buildPredicateFields(predicatesArray))
        } else {
            /* if no predicates are given we just take all existing predicates */
            fields.addAll(buildPredicateFields(existingPredicates))
        }

        /**
         *  Filter for specific objectIds.
         *  To find references of our object id we search for known predicates
         *  and look if they reference any of our ids.
         */
        if (objectsArray.isNotEmpty()) {
            val predicateFilters = existingPredicates.map { p ->
                WhereFilter.builder()
                    .path(p, RETRIEVABLE_ENTITY_NAME, "id")
                    .operator(Operator.ContainsAny)
                    .valueText(*objectsArray) // objects is a list of id's, however weaviate stores a reference beacon here...?
                    .build()
            }.toTypedArray()

            filters.add(
                WhereFilter.builder()
                    .operator(Operator.Or)
                    .operands(*predicateFilters)
                    .build()
            )
        }

        /* AND the individual filters together */
        val whereArgument = WhereArgument.builder()
            .filter(
                WhereFilter.builder()
                    .operator(Operator.And)
                    .operands(*filters.toTypedArray())
                    .build()
            )
            .build()

        /**
         * Execute the query
         */
        val result = connection.client.graphQL().get()
            .withClassName(RETRIEVABLE_ENTITY_NAME)
            .withFields(
                *fields.toTypedArray()
            )
            .withWhere(whereArgument)
            .run()

        if (result.hasErrors()) {
            LOGGER.error { "Error retrieving retrievables: ${result.error}" }
            return mutableListOf<Relationship.ById>().asSequence()
        }
        if (result.result == null) {
            LOGGER.warn { "No retrievables found." }
            return mutableListOf<Relationship.ById>().asSequence()
        }

        /**
         * Parse the result to a [Sequence] of [Relationship.ById]
         */
        val relationshipSequence = mutableListOf<Relationship.ById>()
        result.toWeaviateObject()?.forEach { res ->
            /* Find the Reference Properties for each Retrievable */
            val predicatesForResult = res.properties.filter {prop ->
                if (predicatesArray.isNotEmpty()) {
                    prop.key in predicatesArray
                } else {
                    prop.key in existingPredicates
                }
            }
            /* Separate entry per predicate */
            predicatesForResult.forEach { (k, v) ->
                /* Separate entry for each target id within a predicate */
                (v as List<*>).forEach { targetRetrievable ->
                    /* Extract the id from the target map... */
                    val targetRetrievableMap = targetRetrievable as? Map<*, *>
                    if (targetRetrievableMap != null) {
                        val targetId = targetRetrievableMap["_additional"] as? Map<*, *>
                        if (targetId?.get("id") != null) {
                            relationshipSequence.add(Relationship.ById(
                                UUID.fromString(res.id),
                                k,
                                UUID.fromString(targetId["id"].toString()),
                                false)
                            )
                        }
                    }
                }
            }
        }
        return relationshipSequence.asSequence()
    }


    /**
     * Returns the number of retrievables in the database.
     */
    override fun count(): Long {

        val metaCountField = Field.builder()
            .name("meta")
            .fields(Field.builder().name("count").build())
            .build()

        val result = connection.client.graphQL().aggregate()
            .withClassName(RETRIEVABLE_ENTITY_NAME)
            .withFields(metaCountField)
            .run()

        if (result.hasErrors()) {
            LOGGER.error { "Error counting retrievables: ${result.error}" }
            return -1
        }

        return (result.result.data as? Map<*,*>)
            ?.let { it["Aggregate"] as? Map<*,*> }
            ?.let { it[RETRIEVABLE_ENTITY_NAME] as? List<*> }
            ?.let { it.first() as? Map<*,*> }
            ?.let { it["meta"] as? Map<*,*> }
            ?.let { it["count"] as? Double }
            ?.toLong() ?: -1L

    }
}