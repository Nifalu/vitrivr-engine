package org.vitrivr.engine.database.weaviate
import io.weaviate.client.WeaviateClient
import io.weaviate.client.Config
import org.vitrivr.engine.core.database.AbstractConnectionProvider
import org.vitrivr.engine.core.database.Connection
import org.vitrivr.engine.core.database.ConnectionProvider
import org.vitrivr.engine.core.database.descriptor.DescriptorProvider
import org.vitrivr.engine.core.model.descriptor.scalar.*
import org.vitrivr.engine.core.model.descriptor.struct.AnyMapStructDescriptor
import org.vitrivr.engine.core.model.descriptor.struct.LabelDescriptor
import org.vitrivr.engine.core.model.descriptor.struct.metadata.MediaDimensionsDescriptor
import org.vitrivr.engine.core.model.descriptor.struct.metadata.Rectangle2DMetadataDescriptor
import org.vitrivr.engine.core.model.descriptor.struct.metadata.ShotBoundaryDescriptor
import org.vitrivr.engine.core.model.descriptor.struct.metadata.TemporalMetadataDescriptor
import org.vitrivr.engine.core.model.descriptor.struct.metadata.source.FileSourceMetadataDescriptor
import org.vitrivr.engine.core.model.descriptor.struct.metadata.source.VideoSourceMetadataDescriptor
import org.vitrivr.engine.core.model.descriptor.vector.*
import org.vitrivr.engine.database.weaviate.descriptor.providers.ScalarDescriptorProvider
import org.vitrivr.engine.database.weaviate.descriptor.providers.StructDescriptorProvider
import org.vitrivr.engine.database.weaviate.descriptor.providers.VectorDescriptorProvider

/**
 * Implementation of the [ConnectionProvider] interface for Weaviate.
 *
 * @author Nico Bachmann
 * @version 1.0.0
 */

class WeaviateConnectionProvider: AbstractConnectionProvider() {

    companion object {

        const val PARAMETER_NAME_SCHEME = "scheme"

        const val PARAMETER_DEFAULT_SCHEME = "http"
        /** Name of the host parameter. */
        const val PARAMETER_NAME_HOST = "host"

        /** Name of the host parameter. */
        const val PARAMETER_DEFAULT_HOST = "127.0.0.1"

        /** Name of the port parameter. */
        const val PARAMETER_NAME_PORT = "port"

        /** Name of the host parameter. */
        const val PARAMETER_DEFAULT_PORT = "5432"

        const val PARAMETERS_NAME_NAMED_VECTORS = "vectors"

    }

    /** The name of this [WeaviateConnectionProvider]. */
    override val databaseName: String = "Weaviate"

    /** The version of this [WeaviateConnectionProvider]. */
    override val version: String = "1.0.0"

    /**
     * This method is called during initialization of the [WeaviateConnectionProvider] and can be used to register [DescriptorProvider]s.
     */
    override fun initialize() {
        /* Scalar descriptors. */
        this.register(BooleanDescriptor::class, ScalarDescriptorProvider)
        this.register(IntDescriptor::class, ScalarDescriptorProvider)
        this.register(LongDescriptor::class, ScalarDescriptorProvider)
        this.register(FloatDescriptor::class, ScalarDescriptorProvider)
        this.register(DoubleDescriptor::class, ScalarDescriptorProvider)
        this.register(StringDescriptor::class, ScalarDescriptorProvider)
        this.register(TextDescriptor::class, ScalarDescriptorProvider)

        /* Vector descriptors. */
        this.register(BooleanVectorDescriptor::class, VectorDescriptorProvider)
        this.register(IntVectorDescriptor::class, VectorDescriptorProvider)
        this.register(LongVectorDescriptor::class, VectorDescriptorProvider)
        this.register(FloatVectorDescriptor::class, VectorDescriptorProvider)
        this.register(DoubleVectorDescriptor::class, VectorDescriptorProvider)

        /* Struct descriptor. */
        this.register(LabelDescriptor::class, StructDescriptorProvider)
        this.register(FileSourceMetadataDescriptor::class, StructDescriptorProvider)
        this.register(VideoSourceMetadataDescriptor::class, StructDescriptorProvider)
        this.register(TemporalMetadataDescriptor::class, StructDescriptorProvider)
        this.register(ShotBoundaryDescriptor::class, StructDescriptorProvider)
        this.register(Rectangle2DMetadataDescriptor::class, StructDescriptorProvider)
        this.register(MediaDimensionsDescriptor::class, StructDescriptorProvider)
        this.register(AnyMapStructDescriptor::class, StructDescriptorProvider)
    }

    override fun openConnection(schemaName: String, parameters: Map<String, String>): Connection {
        val scheme = parameters.getOrDefault(PARAMETER_NAME_SCHEME, PARAMETER_DEFAULT_SCHEME)
        val host = parameters.getOrDefault(PARAMETER_NAME_HOST, PARAMETER_DEFAULT_HOST)
        val port = parameters.getOrDefault(PARAMETER_NAME_PORT, PARAMETER_DEFAULT_PORT)
        val url = "$host:$port"

        val namedVectors = (parameters[PARAMETERS_NAME_NAMED_VECTORS]?: "").split(",").map { it.trim() }

        val config = Config(scheme, url)
        val db = WeaviateClient(config)

        return WeaviateConnection(this, schemaName.firstUpperCase(), db, namedVectors)
    }
}