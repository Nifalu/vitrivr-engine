package org.vitrivr.engine.core.resolver.impl

import org.vitrivr.engine.core.model.metamodel.Schema
import org.vitrivr.engine.core.model.retrievable.RetrievableId
import org.vitrivr.engine.core.resolver.Resolvable
import org.vitrivr.engine.core.resolver.Resolver
import org.vitrivr.engine.core.resolver.ResolverFactory
import org.vitrivr.engine.core.source.file.MimeType
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

/**
 * A [Resolver] resolves a physical file from disk.
 *
 * @author Fynn Faber
 * @version 1.0.0
 */
class DiskResolver : ResolverFactory {
    /**
     * Generates a new [DiskResolver] instance using the provided [parameters].
     *
     * @param parameters The parameters used to configure [Resolver]
     * @return [DiskResolver]
     */
    override fun newResolver(schema: Schema, parameters: Map<String, String>): Resolver {
        val location = Paths.get(parameters["location"] ?: "./thumbnails/${schema.name}")
        val mimeType = MimeType.valueOf(parameters["mimeType"] ?: "JPG")
        return Instance(location, mimeType)
    }

    /**
     * The [Resolver] generated by this [DiskResolver].
     */
    private class Instance(private val location: Path, private val mimeType: MimeType) : Resolver {
        init {
            /* Make sure, directory exists. */
            if (!Files.exists(this.location)) {
                Files.createDirectories(this.location)
            }
        }

        /**
         * Resolves the provided [RetrievableId] to a [Resolvable] using this [Resolver].
         *
         * @param id The [RetrievableId] to resolve.
         * @return [Resolvable] or null, if [RetrievableId] could not be resolved.
         */
        override fun resolve(id: RetrievableId): Resolvable = DiskResolvable(id)


        /**
         * A [Resolvable] generated by this [DiskResolver].
         */
        inner class DiskResolvable(override val retrievableId: RetrievableId) : Resolvable {
            val path: Path
                get() = this@Instance.location.resolve("$retrievableId.${this@Instance.mimeType.fileExtension}")
            override val mimeType: MimeType
                get() = this@Instance.mimeType

            override fun exists(): Boolean = Files.exists(this.path)
            override fun openInputStream(): InputStream = Files.newInputStream(this.path, StandardOpenOption.READ)
            override fun openOutputStream(): OutputStream = Files.newOutputStream(this.path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)
        }
    }
}
