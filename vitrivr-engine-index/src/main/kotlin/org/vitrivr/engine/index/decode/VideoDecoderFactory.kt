package org.vitrivr.engine.index.decode

import org.vitrivr.engine.core.context.IndexContext
import org.vitrivr.engine.core.model.metamodel.Schema
import org.vitrivr.engine.core.operators.Operator
import org.vitrivr.engine.core.operators.ingest.DecoderFactory
import org.vitrivr.engine.core.source.Source

/**
 * A [DecoderFactory] for the [VideoDecoder].
 *
 * @author Ralph Gasser
 * @version 1.0.0
 */
class VideoDecoderFactory : DecoderFactory {
    override fun newOperator(
        input: Operator<Source>,
        parameters: Map<String, Any>,
        schema: Schema,
        context: IndexContext
    ): VideoDecoder {
        val contentFactory = context.contentFactory
        val video = true
        val audio = true
        return VideoDecoder(input, contentFactory, video, audio)
    }
}