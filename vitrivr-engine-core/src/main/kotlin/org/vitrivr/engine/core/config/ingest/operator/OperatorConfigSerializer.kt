package org.vitrivr.engine.core.config.ingest.operator

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive


/**
 * A [JsonContentPolymorphicSerializer] for [OperatorConfig]s.
 * Deserialisation builds on the [OperatorConfig.type] property.
 */
object OperatorConfigSerializer: JsonContentPolymorphicSerializer<OperatorConfig>(OperatorConfig::class) {

    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<OperatorConfig> {
        val typeName = element.jsonObject["type"]?.jsonPrimitive?.content?.uppercase()
            ?: throw IllegalArgumentException("An OperatorConfig requries a type, but none found.")

        return when(OperatorType.valueOf(typeName)){
            OperatorType.DECODER -> DecoderConfig.serializer()
            OperatorType.TRANSFORMER -> TransformerConfig.serializer()
            OperatorType.EXTRACTOR -> ExtractorConfig.serializer()
            OperatorType.EXPORTER -> ExporterConfig.serializer()
            OperatorType.AGGREGATOR -> AggregatorConfig.serializer()
        }
    }


}
