package org.vitrivr.engine.database.jsonl.scalar

import org.vitrivr.engine.core.model.descriptor.scalar.*
import org.vitrivr.engine.core.model.metamodel.Schema
import org.vitrivr.engine.core.model.types.Value
import org.vitrivr.engine.database.jsonl.AbstractJsonlReader
import org.vitrivr.engine.database.jsonl.model.AttributeContainerList
import org.vitrivr.engine.database.jsonl.JsonlConnection
import org.vitrivr.engine.database.jsonl.JsonlConnection.Companion.DESCRIPTOR_ID_COLUMN_NAME
import org.vitrivr.engine.database.jsonl.JsonlConnection.Companion.RETRIEVABLE_ID_COLUMN_NAME

class ScalarJsonlReader(
    field: Schema.Field<*, ScalarDescriptor<*>>,
    connection: JsonlConnection
) : AbstractJsonlReader<ScalarDescriptor<*>>(field, connection) {

    override fun toDescriptor(list: AttributeContainerList): ScalarDescriptor<*> {

        val map = list.list.associateBy { it.attribute.name }
        val retrievableId = (map[DESCRIPTOR_ID_COLUMN_NAME]?.value!!.toValue() as Value.UUIDValue).value
        val descriptorId = (map[RETRIEVABLE_ID_COLUMN_NAME]?.value!!.toValue() as Value.UUIDValue).value
        val value = map["value"]?.value!!.toValue()

        return when(prototype) {
            is BooleanDescriptor -> BooleanDescriptor(retrievableId, descriptorId, value as Value.Boolean)
            is DoubleDescriptor -> DoubleDescriptor(retrievableId, descriptorId, value as Value.Double)
            is FloatDescriptor -> FloatDescriptor(retrievableId, descriptorId, value as Value.Float)
            is IntDescriptor -> IntDescriptor(retrievableId, descriptorId, value as Value.Int)
            is LongDescriptor -> LongDescriptor(retrievableId, descriptorId, value as Value.Long)
            is StringDescriptor -> StringDescriptor(retrievableId, descriptorId, value as Value.String)
            else -> {
                error("Unsupported type $prototype")
            }
        }


    }

}