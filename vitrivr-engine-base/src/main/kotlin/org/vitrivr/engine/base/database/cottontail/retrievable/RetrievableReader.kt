package org.vitrivr.engine.base.database.cottontail.retrievable

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import io.grpc.StatusRuntimeException
import org.vitrivr.cottontail.client.language.basics.expression.Column
import org.vitrivr.cottontail.client.language.basics.expression.List
import org.vitrivr.cottontail.client.language.basics.expression.Literal
import org.vitrivr.cottontail.client.language.basics.predicate.And
import org.vitrivr.cottontail.client.language.basics.predicate.Compare
import org.vitrivr.cottontail.client.language.dql.Query
import org.vitrivr.cottontail.core.database.Name
import org.vitrivr.cottontail.core.values.StringValue
import org.vitrivr.cottontail.core.values.UuidValue
import org.vitrivr.engine.base.database.cottontail.CottontailConnection
import org.vitrivr.engine.base.database.cottontail.CottontailConnection.Companion.OBJECT_ID_COLUMN_NAME
import org.vitrivr.engine.base.database.cottontail.CottontailConnection.Companion.PREDICATE_COLUMN_NAME
import org.vitrivr.engine.base.database.cottontail.CottontailConnection.Companion.RETRIEVABLE_ID_COLUMN_NAME
import org.vitrivr.engine.base.database.cottontail.CottontailConnection.Companion.RETRIEVABLE_TYPE_COLUMN_NAME
import org.vitrivr.engine.base.database.cottontail.CottontailConnection.Companion.SUBJECT_ID_COLUMN_NAME
import org.vitrivr.engine.core.database.retrievable.RetrievableInitializer
import org.vitrivr.engine.core.database.retrievable.RetrievableReader
import org.vitrivr.engine.core.model.retrievable.Retrievable
import org.vitrivr.engine.core.model.retrievable.RetrievableId
import org.vitrivr.engine.core.model.retrievable.Retrieved
import java.util.*

private val logger: KLogger = KotlinLogging.logger {}

/**
 * A [RetrievableReader] implementation for Cottontail DB.
 *
 * @author Ralph Gasser
 * @version 1.0.0
 */
internal class RetrievableReader(private val connection: CottontailConnection) : RetrievableReader {
    /** The [Name.EntityName] for this [RetrievableInitializer]. */
    private val entityName: Name.EntityName =
        Name.EntityName.create(this.connection.schemaName, CottontailConnection.RETRIEVABLE_ENTITY_NAME)

    /** The [Name.EntityName] of the relationship entity. */
    private val relationshipEntityName: Name.EntityName =
        Name.EntityName.create(this.connection.schemaName, CottontailConnection.RELATIONSHIP_ENTITY_NAME)

    /**
     * Returns the [Retrievable]s that matches the provided [RetrievableId]
     *
     * @param id [RetrievableId]s to return.
     * @return A [Sequence] of all [Retrievable].
     */
    override fun get(id: RetrievableId): Retrievable? = getBy(id, RETRIEVABLE_ID_COLUMN_NAME)

    override fun getBy(id: UUID, columnName: String): Retrievable? {
        val query = Query(this.entityName).where(
            Compare(
                Column(this.entityName.column(columnName)),
                Compare.Operator.EQUAL,
                Literal(UuidValue(id))
            )
        )
        return try {
            this.connection.client.query(query).use {
                if (it.hasNext()) {
                    val tuple = it.next()
                    val retrievableId = tuple.asUuidValue(columnName)?.value
                        ?: throw IllegalArgumentException("The provided tuple is missing the required field '${columnName}'.")
                    val type = tuple.asString(RETRIEVABLE_TYPE_COLUMN_NAME)
                    Retrieved.Default(retrievableId, type, false)
                } else {
                    null
                }
            }
        } catch (e: StatusRuntimeException) {
            logger.error(e) { "Failed to retrieve descriptor $id due to exception." }
            null
        }
    }

    /**
     * Checks whether a [Retrievable] with the provided [UUID] exists.
     *
     * @param id The [RetrievableId], i.e., the [UUID] of the [Retrievable] to check for.
     * @return True if descriptor exsits, false otherwise
     */
    override fun exists(id: RetrievableId): Boolean {
        val query = Query(this.entityName).exists()
            .where(
                Compare(
                    Column(this.entityName.column(RETRIEVABLE_ID_COLUMN_NAME)),
                    Compare.Operator.EQUAL,
                    Literal(UuidValue(id))
                )
            )
        return try {
            val result = this.connection.client.query(query)
            result.next().asBoolean(0) ?: false
        } catch (e: StatusRuntimeException) {
            logger.error(e) { "Failed to check for existence of retrievable $id due to exception." }
            false
        }
    }

    /**
     * Returns all [Retrievable]s  that match any of the provided [RetrievableId]
     *
     * @param ids A [Iterable] of [RetrievableId]s to return.
     * @return A [Sequence] of all [Retrievable].
     */
    override fun getAll(ids: Iterable<RetrievableId>): Sequence<Retrievable> = getAllBy(ids, RETRIEVABLE_ID_COLUMN_NAME)

    override fun getAllBy(ids: Iterable<UUID>, columnName: String): Sequence<Retrievable> {
        val query = Query(this.entityName).select("*").where(
            Compare(
                Column(Name.ColumnName(columnName)),
                Compare.Operator.IN,
                List(ids.map { UuidValue(it) }.toTypedArray())
            )
        )
        return this.connection.client.query(query).asSequence().map { tuple ->
            val retrievableId = tuple.asUuidValue(RETRIEVABLE_ID_COLUMN_NAME)?.value
                ?: throw IllegalArgumentException("The provided tuple is missing the required field '${RETRIEVABLE_ID_COLUMN_NAME}'.")
            val type = tuple.asString(RETRIEVABLE_TYPE_COLUMN_NAME)
            Retrieved.Default(retrievableId, type, false)
        }
    }

    /**
     * Returns all [Retrievable]s stored by the database.
     *
     * @return A [Sequence] of all [Retrievable]s in the database.
     */
    override fun getAll(): Sequence<Retrievable> {
        val query = Query(this.entityName).select("*")
        return this.connection.client.query(query).asSequence().map { tuple ->
            val retrievableId = tuple.asUuidValue(RETRIEVABLE_ID_COLUMN_NAME)?.value
                ?: throw IllegalArgumentException("The provided tuple is missing the required field '${RETRIEVABLE_ID_COLUMN_NAME}'.")
            val type = tuple.asString(RETRIEVABLE_TYPE_COLUMN_NAME)
            Retrieved.Default(retrievableId, type, false)
        }
    }

    /**
     * Counts the number of retrievables stored by the database.
     *
     * @return The number of [Retrievable]s.
     */
    override fun count(): Long {
        var count = 0L
        this.connection.client.query(Query(this.entityName).count()).forEach {
            count = it.asLong(0)!!
        }
        return count
    }

    /**
     * Returns connections between [Retrievable]s. Empty collections do not filter.
     */
    override fun getConnections(
        subjectIds: Collection<RetrievableId>,
        predicates: Collection<String>,
        objectIds: Collection<RetrievableId>
    ): Sequence<Triple<RetrievableId, String, RetrievableId>> {

        val filters = listOfNotNull(
            if (subjectIds.isNotEmpty()) {
                Compare(
                    Column(Name.ColumnName(SUBJECT_ID_COLUMN_NAME)),
                    Compare.Operator.IN,
                    List(subjectIds.map { UuidValue(it) }.toTypedArray())
                )
            } else {
                null
            },
            if (predicates.isNotEmpty()) {
                Compare(
                    Column(Name.ColumnName(PREDICATE_COLUMN_NAME)),
                    Compare.Operator.IN,
                    List(predicates.map { StringValue(it) }.toTypedArray())
                )
            } else {
                null
            },
            if (objectIds.isNotEmpty()) {
                Compare(
                    Column(Name.ColumnName(OBJECT_ID_COLUMN_NAME)),
                    Compare.Operator.IN,
                    List(objectIds.map { UuidValue(it) }.toTypedArray())
                )
            } else {
                null
            }
        )

        val query = Query(this.relationshipEntityName).select("*")

        if (filters.isNotEmpty()) {
            query.where(
                when (filters.size) {
                    3 -> And(filters[0], And(filters[1], filters[2]))
                    2 -> And(filters[0], filters[1])
                    else -> filters[0]
                }
            )
        }

        return this.connection.client.query(query).asSequence().map { tuple ->
            val s = tuple.asUuidValue(SUBJECT_ID_COLUMN_NAME)?.value
                ?: throw IllegalArgumentException("The provided tuple is missing the required field '${SUBJECT_ID_COLUMN_NAME}'.")
            val p = tuple.asStringValue(PREDICATE_COLUMN_NAME)?.value
                ?: throw IllegalArgumentException("The provided tuple is missing the required field '${PREDICATE_COLUMN_NAME}'.")
            val o = tuple.asUuidValue(OBJECT_ID_COLUMN_NAME)?.value
                ?: throw IllegalArgumentException("The provided tuple is missing the required field '${OBJECT_ID_COLUMN_NAME}'.")
            Triple(s, p, o)
        }

    }
}