package org.vitrivr.engine.database.weaviate.properties

import io.weaviate.client.base.Result
import io.weaviate.client.v1.data.model.WeaviateObject
import io.weaviate.client.v1.schema.model.Property
import org.vitrivr.engine.core.model.descriptor.Descriptor
import org.vitrivr.engine.core.model.retrievable.Retrieved
import org.vitrivr.engine.core.model.retrievable.Retrievable
import org.vitrivr.engine.core.model.retrievable.RetrievableId
import org.vitrivr.engine.core.model.retrievable.attributes.DistanceAttribute
import org.vitrivr.engine.core.model.retrievable.attributes.RetrievableAttribute
import org.vitrivr.engine.database.weaviate.LOGGER
import org.vitrivr.engine.database.weaviate.RETRIEVABLE_TYPE_PROPERTY_NAME
import java.util.*

/**
 * An abstract base class for properties of [Descriptor]s in Weaviate.
 *
 * This class defines the basic operations that can be performed on a [Property] of a [Retrievable],
 * such as initialization, checking if it is set, getting and setting descriptors, and querying.
 *
 * @param D The type of [Descriptor] this property handles.
 * @author Nico Bachmann
 */
abstract class AbstractDescriptorProperty<D : Descriptor<*>> {

    /** The name of a [Descriptor] [Property]*/
    abstract val name: String

    /** Initializes the [Property] for this [Descriptor] */
    abstract fun initialize()

    /** Check if the [Property] is initialized */
    abstract fun isInitialized() : Boolean

    /** Check if the [Property] is set for the given [RetrievableId] */
    abstract fun isSet(retrievableId: UUID) : Boolean

    /** Set the [Property] for the given [Descriptor] */
    abstract fun set(descriptor: D) : Boolean

    /** Get the [Descriptor] of type [D] for the given [RetrievableId] */
    abstract fun get(retrievableId: UUID): D?

    /** Get all the [Descriptor]s of type [D] for the given [RetrievableId]s */
    abstract fun getAll(retrievableIds: Iterable<UUID>): Sequence<D>

    /** Get all [Descriptor]s of type [D]*/
    abstract fun getAll(): Sequence<D>

    /** Delete the [Property] for the given [Descriptor] */
    abstract fun unset(descriptor: D): Boolean

    /** Convert a result value to a [Descriptor] of type [D] */
    abstract fun resultToDescriptor(value: Any, id: UUID): D?

    /** Convert a sequence of [WeaviateObject]s to a sequence of [Descriptor]s of type [D] */
    abstract fun wObjectsToDescriptors(weaviateObjects: Sequence<WeaviateObject>): Sequence<D>

    /** Query for [Retrieved]s */
    abstract fun queryRetrievable(query: org.vitrivr.engine.core.model.query.Query): Sequence<Retrieved>

    /** Query for [Descriptor]s of type [D] */
    abstract fun queryProperty(query: org.vitrivr.engine.core.model.query.Query): Sequence<D>

    /** Convert a sequence of [WeaviateObject]s to a sequence of [Retrieved]s */
    fun wObjectsToRetrieved(weaviateObjects: Sequence<WeaviateObject>): Sequence<Retrieved> {
        return weaviateObjects.map { retrievable ->
            val type = retrievable.properties[RETRIEVABLE_TYPE_PROPERTY_NAME] as? String ?: run {
                LOGGER.error { "Retrievable object ${retrievable.id} does not have a type property set." }
                "unknown"
            }
            val attributes = mutableSetOf<RetrievableAttribute>()
            val id = UUID.fromString(retrievable.id)
            val distance = retrievable.additional?.get("distance") as? Double
            if (distance != null) {
                attributes.add(DistanceAttribute.Local(distance, id))
            }

            Retrieved(
                id = id,
                type = type,
                descriptors = this.wObjectsToDescriptors(sequenceOf(retrievable)).toSet(),
                attributes = attributes
            )
        }
    }

    /**
     * Checks if the given [Result] is valid.
     *
     * A valid result is one that has no errors and contains a non-null result.
     *
     * @param result The [Result] to check.
     * @return True if the result is valid, false otherwise.
     */
    fun isValid(result: Result<*>) : Boolean {
        if (result.hasErrors()) {
            LOGGER.error { "Failed to fetch descriptor due to error: ${result.error}" }
            return false
        }
        if (result.result == null) {
            LOGGER.warn { "Got null result from Weaviate $result" }
            return false
        }
        return true
    }
}