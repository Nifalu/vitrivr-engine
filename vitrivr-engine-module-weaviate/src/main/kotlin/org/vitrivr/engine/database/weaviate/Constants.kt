package org.vitrivr.engine.database.weaviate

const val DESCRIPTOR_ENTITY_PREFIX = "descriptor"

const val RETRIEVABLE_ENTITY_DESCRIPTION = "Weaviate Collection of retrievables"

const val RETRIEVABLE_TYPE_PROPERTY_NAME = "type"

const val RETRIEVABLE_TYPE_PROPERTY_DESCRIPTION = "The type of the retrievable entity."

object Constants {
    private var COLLECTION_NAME = "NotSet"
    fun getCollectionName(): String {
        return COLLECTION_NAME
    }
    fun setCollectionName(name: String) {
        COLLECTION_NAME = name
    }
}