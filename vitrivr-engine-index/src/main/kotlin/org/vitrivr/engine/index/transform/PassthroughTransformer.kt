package org.vitrivr.engine.index.transform

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.vitrivr.engine.core.model.content.element.ContentElement
import org.vitrivr.engine.core.operators.Operator
import org.vitrivr.engine.core.operators.ingest.Transformer

private val logger: KLogger = KotlinLogging.logger {}

/***
 * A Template for a [Transformer].
 *
 * @author Raphael Waltenspül
 * @version 1.0
 */
class PassthroughTransformer(
    override val input: Operator<ContentElement<*>>,
    val parameters: Map<String, Any>
) : Transformer {
    override fun toFlow(scope: CoroutineScope): Flow<ContentElement<*>> {
        return this.input.toFlow(scope).map { value: ContentElement<*> ->
            logger.info { "Performed Dummy Transformer with options ${parameters} on ${value}" }
            value
        }
    }
}