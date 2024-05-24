package org.vitrivr.engine.index.exporters

import com.sksamuel.scrimage.ImmutableImage
import com.sksamuel.scrimage.nio.JpegWriter
import com.sksamuel.scrimage.nio.PngWriter
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import org.vitrivr.engine.core.context.IndexContext
import org.vitrivr.engine.core.model.content.element.ImageContent
import org.vitrivr.engine.core.model.retrievable.Retrievable
import org.vitrivr.engine.core.operators.Operator
import org.vitrivr.engine.core.operators.general.Exporter
import org.vitrivr.engine.core.operators.general.ExporterFactory
import org.vitrivr.engine.core.source.file.MimeType

private val logger: KLogger = KotlinLogging.logger {}

/**
 * An [Exporter] that generates thumbnails from videos and images.
 *
 * @author Finn Faber
 * @version 1.0.1
 */
class ThumbnailExporter : ExporterFactory {
    companion object {
        val SUPPORTED = setOf(MimeType.JPEG, MimeType.JPG, MimeType.PNG)
    }

    /**
     * Creates a new [Exporter] instance from this [ThumbnailExporter].
     *
     * @param name The name of the [Exporter]
     * @param input The [Operator] to acting as an input.
     * @param context The [IndexContext] to use.
     */
    override fun newExporter(name: String, input: Operator<Retrievable>, context: IndexContext): Exporter {
        val maxSideResolution = context[name, "maxSideResolution"]?.toIntOrNull() ?: 400
        val mimeType = context[name, "mimeType"]?.let {
            try {
                MimeType.valueOf(it.uppercase())
            } catch (e: java.lang.IllegalArgumentException) {
                null
            }
        } ?: MimeType.JPG
        logger.debug { "Creating new ThumbnailExporter with maxSideResolution=$maxSideResolution and mimeType=$mimeType." }
        return Instance(input, context, maxSideResolution, mimeType)
    }

    /**
     * The [Exporter] generated by this [ThumbnailExporter].
     */
    private class Instance(override val input: Operator<Retrievable>, private val context: IndexContext, private val maxResolution: Int, private val mimeType: MimeType) : Exporter {
        init {
            require(mimeType in SUPPORTED) { "ThumbnailExporter only support image formats JPEG and PNG." }
        }

        override fun toFlow(scope: CoroutineScope): Flow<Retrievable> = this.input.toFlow(scope).onEach { retrievable ->
            val resolvable = this.context.resolver.resolve(retrievable.id)
            val content = retrievable.content.filterIsInstance<ImageContent>()
            if (resolvable != null && content.isNotEmpty()) {
                val writer = when (mimeType) {
                    MimeType.JPEG,
                    MimeType.JPG -> JpegWriter()
                    MimeType.PNG -> PngWriter()
                    else -> throw IllegalArgumentException("Unsupported mime type $mimeType")
                }

                logger.debug { "Generating thumbnail(s) for ${retrievable.id} with ${retrievable.type} and resolution $maxResolution. Storing it with ${resolvable::class.simpleName}." }

                content.forEach { cnt ->
                    val imgBytes = ImmutableImage.fromAwt(cnt.content).let {
                        if (it.width > it.height) {
                            it.scaleToWidth(maxResolution)
                        } else {
                            it.scaleToHeight(maxResolution)
                        }
                    }.bytes(writer)

                    resolvable.openOutputStream().use {
                        it.write(imgBytes)
                    }
                }
            }
        }
    }
}

