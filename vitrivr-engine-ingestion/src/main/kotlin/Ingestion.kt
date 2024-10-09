package org.vitrivr.engine.ingestion
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import org.vitrivr.cottontail.client.SimpleClient
import org.vitrivr.cottontail.client.language.basics.Direction
import org.vitrivr.cottontail.client.language.basics.Distances
import org.vitrivr.cottontail.client.language.basics.expression.Column
import org.vitrivr.cottontail.client.language.basics.expression.Literal
import org.vitrivr.cottontail.client.language.basics.predicate.Compare
import org.vitrivr.cottontail.client.language.basics.predicate.Predicate

import org.vitrivr.cottontail.client.language.ddl.CreateEntity
import org.vitrivr.cottontail.client.language.ddl.CreateSchema
import org.vitrivr.cottontail.client.language.ddl.DropSchema
import org.vitrivr.cottontail.client.language.dml.Insert
import org.vitrivr.cottontail.client.language.dql.Query
import org.vitrivr.cottontail.client.language.dml.Update


import org.vitrivr.cottontail.core.database.Name
import org.vitrivr.cottontail.core.database.TupleId
import org.vitrivr.cottontail.core.types.Types
import org.vitrivr.cottontail.core.values.UuidValue
//import org.vitrivr.cottontail.utilities.VectorUtility
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Example code for the use of Cottontail DB [SimpleClient] library in Kotlin.
 *
 * @author Ralph Gasser
 * @version 1.0.1
 */
object ExamplesSimple {

    /** Cottontail DB gRPC channel; adjust Cottontail DB host and port according to your needs. */
    private val channel = ManagedChannelBuilder.forAddress("10.34.58.81", 1865).usePlaintext().build()

    /** Cottontail DB [SimpleClient] for all database operations. */
    private val client = SimpleClient(channel)


/*
    /**
     * Imports the example data contained in the resource bundle of the project.
     */
    fun importData() = entities.forEach {
        /* Start a transaction per INSERT. */
        val txId = this.client.begin()

        /* Load data from file (in resources folder). */
        val classloader = Thread.currentThread().contextClassLoader
        try {
            BufferedReader(InputStreamReader(classloader.getResourceAsStream(it.first))).useLines { lines ->
                lines.forEach { l ->
                    val split = l.split('\t')

                    /* Prepare data for second (feature) column. */
                    val feature = split[3].split(' ').map { v -> v.toFloat() }.toFloatArray()
                    this.client.insert(Insert("${this.schemaName}.${it.first}").value("id", split[0]).value("feature", feature));
                }
            }
            this.client.commit(txId)
        } catch (e: Throwable) {
            println("Exception during data import.")
            this.client.rollback(txId)
            e.printStackTrace()
        }
    }
    */

    //TODO - Add a way to read from a file and update the metadata accordingly
    fun updateMetaData(updates: List<Triple<String, String, Pair<String, String>>>) {
        updates.forEach { u ->
            updateField(u.first, u.second, u.third)
        }
    }

    fun updateMetaData(entity: String, column: String, value: String, limit: Long = 3) {

        /* Prepare query. */
        val query = Query(entity).select("*").limit(limit)

        /* Execute query. */
        val results = this.client.query(query)

        results.forEach { r ->
            val id = r.values().first().toString()
            updateField(entity, column, id to value)
        }
    }

    private fun updateField(entity: String, column: String, value: Pair<String, String>) {

        /* prepare compare */
        val compare = Compare(
            Column("descriptorid"),
            Compare.Operator.EQUAL,
            Literal(
                UuidValue(value.first)
            )
        )

        /* Prepare update. */
        val update = Update(entity)
            .where(compare)
            .any(column to value.second)

        /* Execute query. */
        this.client.update(update)
    }
}


/**
 * Entry point for example program.
 */
fun main() {

    println("Let's go!!")
    ExamplesSimple.updateMetaData("xreco.descriptor_description", "user", "HARAMBE" ,100) /* Execute simple SELECT statement with LIMIT. */
    println("Done!!")

}