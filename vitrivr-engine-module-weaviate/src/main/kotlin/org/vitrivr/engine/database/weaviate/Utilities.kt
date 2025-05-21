package org.vitrivr.engine.database.weaviate

import io.weaviate.client.WeaviateClient
import io.weaviate.client.v1.data.model.WeaviateObject
import io.weaviate.client.v1.graphql.model.GraphQLResponse
import io.weaviate.client.base.Result


/**
 * Extension function that converts a [Result] of type [GraphQLResponse] to a sequence of [WeaviateObject].
 * 
 * The [GraphQLResponse] is in JSON format which this function parses.
 */
internal fun <T>  Result<GraphQLResponse<T>?>.toWeaviateObject(): Sequence<WeaviateObject>? {
    
    /* First check if the response has errors */
    if (this.hasErrors()) {
        LOGGER.error { "Error parsing GraphQL Response: ${this.error}" }
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
    val retrievables = getSection[RETRIEVABLE_ENTITY_NAME]
    if (retrievables !is List<*>) {
        LOGGER.error { "No objects found in '$RETRIEVABLE_ENTITY_NAME'... : $retrievables" }
        return null
    }

    val result = mutableListOf<WeaviateObject>()

    /* Parse the retrievables */
    for (item in retrievables) {
        val contents = item as? Map<*, *>
        if (contents == null) {
            warn(item.toString())
            continue
        }
        /* the item id as well as the named vectors are stored in '_additional' */
        val additional = contents["_additional"] as? Map<*,*>
        if (additional == null) {
            warn(item.toString())
            continue
        }

        val id = additional["id"] as? String
        if (id == null) {
            warn(item.toString())
            continue
        }

        /* Is type safe parsing always this ugly? */
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

        result.add(WeaviateObject.builder()
            .className(RETRIEVABLE_ENTITY_NAME)
            .id(id)
            .properties(properties)
            .vectors(vectors)
            .build()
        )
    }
    return result.asSequence()
}

/**
 * Small helper function that logs a warning message.
 *
 * If this function is called, the format of the GraphQLResponse probably changed.
 *
 * @param msg The message to log.
 */
private fun warn(msg: String) {
    LOGGER.warn { "GraphQLResponse Parser found an unexpected input: $msg"}
}

internal fun WeaviateClient.findPredicateProperties(): List<String> {
    this.schema().classGetter().withClassName(RETRIEVABLE_ENTITY_NAME).run().let { result ->
        if (result.hasErrors()) {
            LOGGER.error { "Error retrieving schema: ${result.error}" }
            return emptyList()
        }

        return result.result.properties
            .filter { it.dataType.contains(RETRIEVABLE_ENTITY_NAME) }
            .map { it.name }

    }
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

