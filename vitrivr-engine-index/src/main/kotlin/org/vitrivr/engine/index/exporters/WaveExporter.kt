package org.vitrivr.engine.index.exporters

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import org.vitrivr.engine.core.context.IndexContext
import org.vitrivr.engine.core.model.content.element.AudioContent
import org.vitrivr.engine.core.model.retrievable.Retrievable
import org.vitrivr.engine.core.operators.Operator
import org.vitrivr.engine.core.operators.general.Exporter
import org.vitrivr.engine.core.operators.general.ExporterFactory
import org.vitrivr.engine.core.resolver.Resolver
import org.vitrivr.engine.index.util.WaveUtilities
import java.io.IOException

private val logger: KLogger = KotlinLogging.logger {}

/**
 * An [Exporter] that generates wave files from audio samples.
 *
 * @author Ralph Gasser
 * @version 1.0.0
 */
class WaveExporter : ExporterFactory {
    /**
     * Creates a new [Exporter] instance from this [ThumbnailExporter].
     *
     * @param name The name of the [Exporter]
     * @param input The [Operator] to acting as an input.
     * @param context The [IndexContext] to use.
     */
    override fun newExporter(name: String, input: Operator<Retrievable>, context: IndexContext): Exporter {
        val resolverName = context[name, "resolver"]?: "default"
        return Instance(input, context, resolverName, name)
    }

    /**
     * The [Exporter] generated by this [WaveExporter].
     */
    private class Instance(
        override val input: Operator<Retrievable>,
        private val context: IndexContext,
        resolverName: String,
        override val name: String
    ) : Exporter {

        /** [Resolver] instance. */
        private val resolver: Resolver = this.context.resolver[resolverName] ?: throw IllegalStateException("Unknown resolver with name $resolverName.")

        override fun toFlow(scope: CoroutineScope): Flow<Retrievable> = this.input.toFlow(scope).onEach { retrievable ->
            try {
                val resolvable = this.resolver.resolve(retrievable.id, ".wav")
                val content = retrievable.content.filterIsInstance<AudioContent>()
                if (resolvable != null && content.isNotEmpty()) {
                    resolvable.openOutputStream().use {
                        WaveUtilities.export(content, it)
                    }
                }
            } catch (e: IOException) {
                logger.error(e) { "IO exception during wave creation." }
            }
        }
    }
}