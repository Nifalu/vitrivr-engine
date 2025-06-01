package org.vitrivr.engine.database.weaviate

/** The prefix for descriptor entities */
const val DESCRIPTOR_ENTITY_PREFIX = "descriptor"

/** A description for the weaviate collection */
const val RETRIEVABLE_ENTITY_DESCRIPTION = "Weaviate Collection of retrievables"

/** The property name storing the retrievable type */
const val RETRIEVABLE_TYPE_PROPERTY_NAME = "type"

/** A description for the retrievable type property */
const val RETRIEVABLE_TYPE_PROPERTY_DESCRIPTION = "The type of the retrievable entity."

/** The distance metric used for vector similarity search in Weaviate */
const val DISTANCE = "cosine"

/**
 *  The Collection is not defined by the connection, so we need to store it somewhere accessible.
 *
 *  It's not really a constant but rather a final variable that can be set at runtime.
 */
object Constants {
    /** The name of the collection */
    private var COLLECTION_NAME = "NotSet"

    /** Get the name of the collection */
    fun getCollectionName(): String {
        return COLLECTION_NAME
    }

    /** Sets the name of the collection */
    fun setCollectionName(name: String) {
        COLLECTION_NAME = name
    }
}