package org.vitrivr.engine.core.math.correspondence

import org.vitrivr.engine.core.model.retrievable.attributes.DistanceAttribute
import org.vitrivr.engine.core.model.retrievable.attributes.ScoreAttribute

/**
 * A linear [CorrespondenceFunction] that is based on a maximum distance.
 *
 * @author Ralph Gasser
 * @version 1.0.0
 */
class LinearCorrespondence(private val max: Float) : CorrespondenceFunction {
    override fun invoke(distance: DistanceAttribute): ScoreAttribute.Similarity = when(distance) {
        is DistanceAttribute.Global ->  ScoreAttribute.Similarity(1.0f - (distance.distance / this.max))
        is DistanceAttribute.Local -> ScoreAttribute.Similarity(1.0f - (distance.distance / this.max), distance.descriptorId)
    }
}