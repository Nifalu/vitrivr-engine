# vitrivr engine

[vitrivr](https://vitrivr.org)'s next-generation retrieval engine.
Based on the experiences with its predecessor, [Cineast](https://github.com/vitrivr/cineast),
vitrivr engine's data model, ingestion pipeline and retrieval logic have been reworked.

## Project Structure

The project is set up as a multi-module Kotlin project:

* [`vitrivr-engine-core`](/vitrivr-engine-core) - The core library of the project (also published on maven as a library)
* [`vitrivr-engine-index`](/vitrivr-engine-index) - Indexing / Ingestion related core library
* [`vitrivr-engine-plugin-cottontaildb`](/vitrivr-engine-plugin-cottontaildb) - The java module for the column-store and kNN faciliating databse, [CottontailDB](https://github.com/vitrivr/cottontaildb)
* [`vitrivr-engine-plugin-features`](/vitrivr-engine-plugin-features) - The java module which provides specific indexing and retrieval implementations such as fulltext, colour, etc.
* [`vitrivr-engine-plugin-m3d`](/vitrivr-engine-plugin-m3d) - The in-project plugin related to 3d model indexing and retrieval
* [`vtirivr-engine-query`](/vitrivr-engine-query) - Query / Retrieval related core library
* [`vitrivr-engine-server`](/vitrivr-engine-server) - A [Javalin](https://javalin.io) powered server providing an [OpenApi](https://openapis.org) [documented REST API](vitrivr-engine-server/doc/oas.json) for both, ingestion and querying and a CLI, essentially the runtime of the project

## Getting Started: Usage

vitrivr engine is a Kotlin project and hence requires a JDK (e.g. [OpenJDK](https://openjdk.org/)) to properly run.
Furthermore, we use [Gradle](https://gradle.org) in order to facilitate the building and deployment
through the Gradle wrapper.

In the context of retrieval, often times a distinction of _indexing_ / _ingestion_ (also known as _offline phase_)
and _querying_ / _retrieval_ (also known as _online phase_) is made.
While the former addresses (multimedia) content to be analysed and indexed, i.e. made ready for search, is the latter's purpose to
search within the previously built index.

### Indexing / Ingestion

The most essential prerequisite for the ingestion is the existence of multimedia content.
For the sake of this example, let's assume the existence of such multimedia content in the form of image and video files.

Also, since vitrivr engine is highly configurable, the first few steps involve the creation of a suitable
configuration.

#### Schema

vitrivr engine operates on the notion of _schema_, similarly to a database or a collection, 
essentially providing, among other things, a namespace.
For this guide, we will have a single schema `sandbox`.

Create a config file `sandbox-config.json` with one named schema in it:

```json
{
  "schemas": [{
    "name": "sandbox"
  }]
}
```

#### Schema Database

The database is also a rather important component of the system. 
This guide assumes a running [CottontailDB](https://github.com/vitrivr/cottontaildb)
instance on the same machine on the default port `1865`.

---
**NOTE this requires [Cottontail 0.16.5](https://github.com/vitrivr/cottontaildb/releases/tag/0.16.5) or newer**

---

We address the database with the [`ConnectionConfig`](/vitrivr-engine-core/src/main/kotlin/org/vitrivr/engine/core/config/ConnectionConfig.kt):

```json
{
  "database": "CottontailConnectionProvider",
  "parameters": {
    "Host": "127.0.0.1",
    "port": "1865"
  }
}
```

We add the cottontail connection to the schema's connection property:

```json
{
  "schemas": [{
    "name": "sandbox",
    "connection": {
      "database": "CottontailConnectionProvider",
      "parameters": {
        "Host": "127.0.0.1",
        "port": "1865"
      }
    }
  }]
}
```

#### Schema Analyser

The [`Analyser`](/vitrivr-engine-core/src/main/kotlin/org/vitrivr/engine/core/model/metamodel/Analyser.kt)
performs analysis to derive a [`Descriptor`](/vitrivr-engine-core/src/main/kotlin/org/vitrivr/engine/core/model/descriptor/Descriptor.kt).
In other words, the _analyser_ produces a _descriptor_ which represents the media content analysed.
However, this is only for _indexing_ / _ingestion_. During _querying_ / _retrieval_ time, 
the _analyser_ queries the underlying storage layer to perform a query on said _descriptors_.

#### Schema Field Configuration

A schema consists of unique-named _fields_, that have to be backed by an [`Analyser`](/vitrivr-engine-core/src/main/kotlin/org/vitrivr/engine/core/model/metamodel/Analyser.kt),
essentially representing a specific [`Descriptor`](/vitrivr-engine-core/src/main/kotlin/org/vitrivr/engine/core/model/descriptor/Descriptor.kt).
This is configured using the [`FieldConfig`](/vitrivr-engine-core/src/main/kotlin/org/vitrivr/engine/core/config/FieldConfig.kt):

```json
{
  "name": "uniqueName",
  "factory": "FactoryClass",
  "parameters":{
    "key": "value"
  }
}
```

For images (and video frames), it might be worthwhile to use the average colour for representation purposes.
The built-in [`AverageColor`](/vitrivr-engine-plugin-features/src/main/kotlin/org/vitrivr/engine/base/features/averagecolor/AverageColor.kt)
analyser can be facilitated for this endeavour.
To use it, we specifically craft a corresponding _field config_:

```json
{
  "name": "averagecolor",
  "factory": "AverageColor"
}
```

There are no additional parameters, unlike, for instance, an [`ExternalAnalyser`](/vitrivr-engine-plugin-features/src/main/kotlin/org/vitrivr/engine/base/features/external/ExternalAnalyser.kt),
which requires the parameter `host` with an endpoint as value.

Other fields are for (technical) metadata such as the [`FileSourceMetadata`](/vitrivr-engine-core/src/main/kotlin/org/vitrivr/engine/core/features/metadata/source/file/FileSourceMetadata.kt),
which additionally stores the file's path and size.

Currently, there is no list of available fields and analysers, therefore a quick look into the code
reveals those existent. For basic (metadata), see in [the core module](/vitrivr-engine-core/src/main/kotlin/org/vitrivr/engine/core/features/),
for content-based features, see in [the features' module](/vitrivr-engine-plugin-features/src/main/kotlin/org/vitrivr/engine/base/features/).

In this guide, we use the _average colour_ and _file source_ fields, which results in the (currently) following
configuration file:

```json
{
  "schemas": [{
    "name": "sandbox",
    "connection": {
      "database": "CottontailConnectionProvider",
      "parameters": {
        "Host": "127.0.0.1",
        "port": "1865"
      }
    },
    "fields": [
      {
        "name": "averagecolor",
        "factory": "AverageColor"
      },
      {
        "name": "file",
        "factory": "FileSourceMetadata"
      }
    ]
  }]
}
```

#### Schema Resolver Configuration

Some data is stored e.g. on disk during extraction, which later will also be required during query time,
therefore the [`Resolver`](vitrivr-engine-core/src/main/kotlin/org/vitrivr/engine/core/resolver/Resolver.kt)
is configured as the [`ResolverConfig`](vitrivr-engine-core/src/main/kotlin/org/vitrivr/engine/core/config/schema/ResolverConfig.kt)
on the schema with a unique name.

The [`ResolverConfig`](vitrivr-engine-core/src/main/kotlin/org/vitrivr/engine/core/config/schema/ResolverConfig.kt) describes such a configuration:

```json
{
  "factory": "FactoryClass",
  "parameters": {
    "key": "value"
  }
}
```

Specifically, the [`DiskResolver`](/vitrivr-engine-core/src/main/kotlin/org/vitrivr/engine/core/resolver/impl/DiskResolver.kt) is implemented and configured as such:

```json
{
  "factory": "DiskResolver",
  "parameters": {
    "location": "./thumbnails/vitrivr"
  }
}
```

Therefore, the _schema_ config is expanded with the _disk resolver_, named `disk`:

```json
{
  "schemas": [{
    "name": "sandbox",
    "connection": {
      "database": "CottontailConnectionProvider",
      "parameters": {
        "Host": "127.0.0.1",
        "port": "1865"
      }
    },
    "fields": [
      {
        "name": "averagecolor",
        "factory": "AverageColor"
      },
      {
        "name": "file",
        "factory": "FileSourceMetadata"
      }
    ]
  }],
  "resolvers": {
    "disk": {
      "factory": "DiskResolver",
      "parameters": {
        "location": "./thumbnails/vitrivr"
      }
    }
  }
}
```

#### Schema Exporter Configuration

In the context of images and videos, having thumbnails is desirable, which can be generated
during ingestion with the configuration of an _exporter_.
Generally speaking, an _exporter_ exports an artifact based on the media content.

The [`ExporterConfig`](/vitrivr-engine-core/src/main/kotlin/org/vitrivr/engine/core/config/ExporterConfig.kt)
describes such a configuration:

```json
{
  "name": "uniqueName",
  "factory": "FactoryClass",
  "resolverName": "disk",
  "parameters": {
    "key": "value"
  }
}
```

Specifically, the [`ThumbnailExporter`](/vitrivr-engine-index/src/main/kotlin/org/vitrivr/engine/index/exporters/ThumbnailExporter.kt),
can be configured as follows, which references a [`DiskResolver`](/vitrivr-engine-core/src/main/kotlin/org/vitrivr/engine/core/resolver/impl/DiskResolver.kt) named `disk`, see the previous section.

```json
{
    "name": "thumbnail",
    "factory": "ThumbnailExporter",
    "resolverName": "disk",
    "parameters": {
      "maxSideResolution": "400",
      "mimeType": "JPG"
    }
}
```

Resulting in the following schema config:

```json
{
  "schemas": [{
    "name": "sandbox",
    "connection": {
      "database": "CottontailConnectionProvider",
      "parameters": {
        "Host": "127.0.0.1",
        "port": "1865"
      }
    },
    "fields": [
      {
        "name": "averagecolor",
        "factory": "AverageColor"
      },
      {
        "name": "file",
        "factory": "FileSourceMetadata"
      }
    ],
    "resolvers": {
      "disk": {
        "factory": "DiskResolver",
        "parameters": {
          "location": "./thumbnails/vitrivr"
        }
      }
    },
    "exporters": [
      {
        "name": "thumbnail",
        "factory": "ThumbnailExporter",
        "resolverName": "disk",
        "parameters": {
          "maxSideResolution": "400",
          "mimeType": "JPG"
        }
      }
    ]
  }]
}
```

#### Extraction Pipeline Configuration

In order to effectively support a specific _ingestion_ / _indexing_, we have to provide
a reference to the [`IndexConfig`](/vitrivr-engine-core/src/main/kotlin/org/vitrivr/engine/core/config/IndexConfig.kt),
which is configured as a [`PipelineConfig`](/vitrivr-engine-core/src/main/kotlin/org/vitrivr/engine/core/config/PipelineConfig.kt) within the schema config:

```json
{
  "name": "pipelineName",
  "path": "path/to/vbs-config-pipeline.json"
}
```

We will create said _index config_ later as `sandbox-pipeline.json`, hence, our schema config
is as follows:

```json
{
  "schemas": [{
    "name": "sandbox",
    "connection": {
      "database": "CottontailConnectionProvider",
      "parameters": {
        "Host": "127.0.0.1",
        "port": "1865"
      }
    },
    "fields": [
      {
        "name": "averagecolor",
        "factory": "AverageColor"
      },
      {
        "name": "file",
        "factory": "FileSourceMetadata"
      }
    ],
    "exporters": [
      {
        "name": "thumbnail",
        "factory": "ThumbnailExporter",
        "resolver": {
          "factory": "DiskResolver",
          "parameters": {
            "location": "./thumbnails/sandbox"
          }
        },
        "parameters": {
          "maxSideResolution": "400",
          "mimeType": "JPG"
        }
      }
    ],
    "extractionPipelines": [
      {
        "name": "sandboxpipeline",
        "path": "./sandbox-pipeline.json"
      }
    ]
  }]
}
```

#### Index Pipeline Configuration

Let's create a new file `sandbox-pipeline.json` right next to the `sandbox-config.json` in the root of the project.
This file will contain the [`IngestionConfig`](/vitrivr-engine-core/src/main/kotlin/org/vitrivr/engine/core/config/ingest/IngestionConfig.kt).

In order to address (reference) our schema, we reference it in our index config and provide a _context_ as well as an _enumerator_:

```json
{
  "schema": "sandbox",
  "context": {
    
  },
  "enumerator": {
    
  }
}
```

#### Index Context Configuration

_NOTE: THIS SECTION REQUIRES REVIEW_

An [`IngestionContextConfig`](/vitrivr-engine-core/src/main/kotlin/org/vitrivr/engine/core/config/ingest/IngestionContextConfig.kt)
is used to specify the _context_, additional information to the media data.
Specifically, a [`Resolver`](vitrivr-engine-core/src/main/kotlin/org/vitrivr/engine/core/resolver/Resolver.kt), `disk`, is referenced by its name from the _schema_ configuration.

```json
{
    "contentFactory": "InMemoryContentFactory",
    "resolverName": "disk"
}
```

The example index pipeline configuration then, with the path adjusted to the one we used in our configuration,
looks as follows:

```json
{
  "schema": "sandbox",
  "context": {
    "contentFactory": "InMemoryContentFactory",
    "resolverName": "disk"
  },
  "enumerator": {

  }
}
```

#### Index Enumerator Configuration

The _enumerator_ enumerates the content to index and provides it to the indexing pipeline.
It is described with a [`EnumeratorConfig](/vitrivr-engine-core/src/main/kotlin/org/vitrivr/engine/core/config/ingest/operator/OperatorConfig.kt).

```json
{
  "type": "ENUMERATOR",
  "factory": "FactoryClass",
  "api": true,
  "parameters": {
    "key": "value"
  }
}
```

Currently implemented enumerators are found [in the index module](/vitrivr-engine-index/src/main/kotlin/org/vitrivr/engine/index/enumerate),
of which we will use the [`FileSystemEnumerator`](/vitrivr-engine-index/src/main/kotlin/org/vitrivr/engine/index/enumerate/FileSystemEnumerator.kt).
The configuration **requires** the parameter `path`, the path to the folder containing multimedia content
and the parameter `mediaTypes`, which is a semicolon (`;`) separated list of [`MediaType`](/vitrivr-engine-core/src/main/kotlin/org/vitrivr/engine/core/source/MediaType.kt)s.
Essentially, for still images, use `IMAGE` and for videos `VIDEO`.
Additional parameters are `skip` (how many files should be skipped), `limit` (how many files should at max be enumerated over)
and `depth` (the depth to traverse the file system, `1` stands for current folder only, `2` for sub-folders, `3` for sub-sub-folders, ...).
Let's assume we do have in the root project a folder `sandbox`, with two sub-folders `imgs` and `vids`:

```
/sandbox
  |
  - /imgs
    |
    - img1.png
    |
    - img2.png
  |
  - /vids
    |
    - vid1.mp4
```

For an image only ingestion, we could set-up the configuration as follows (`skip` and `limit` have sensible default values of `0` and `Integer.MAX_VALUE`, respectively):

```json
{
    "type": "ENUMERATOR",
    "factory": "FileSystemEnumerator",
    "api": true,
    "parameters": {
      "path": "./sandbox/imgs",
      "mediaTypes": "IMAGE",
      "depth": "1"
    }
}
```

This results in the following index pipeline config:

```json
{
  "schema": "sandbox",
  "context": {
    "contentFactory": "InMemoryContentFactory",
    "resolverName": "disk"
  },
  "enumerator": {
    "type": "ENUMERATOR",
    "factory": "FileSystemEnumerator",
    "api": true,
    "parameters": {
      "path": "./sandbox/imgs",
      "mediaTypes": "IMAGE",
      "depth": "1"
    }
  }
}
```

#### Index Decoder Configuration

The [`DecoderConfig`](/vitrivr-engine-core/src/main/kotlin/org/vitrivr/engine/core/config/ingest/operator/OperatorConfig.kt)
describes how the media content is decoded.

```json
{
  "type": "",
  "factory": "DecoderClass",
  "parameters": {
    "key": "value"
  }
}
```
Note that either a _transformer_ **or** a _segmenter_ can be configured as next.

Available decodes can be found [in the index module](/vitrivr-engine-index/src/main/kotlin/org/vitrivr/engine/index/decode).
Since we work with images in this tutorial, we require the [`ImageDecoder`](/vitrivr-engine-index/src/main/kotlin/org/vitrivr/engine/index/decode/ImageDecoder.kt):

```json
{
  "name": "ImageDecoder"
}
```

Resulting in the following index pipeline configuration:

```json
{
  "schema": "sandbox",
  "context": {
    "contentFactory": "InMemoryContentFactory",
    "resolverName": "disk"
  },
  "enumerator": {
    "type": "ENUMERATOR",
    "factory": "FileSystemEnumerator",
    "api": true,
    "parameters": {
      "path": "./sandbox/imgs",
      "mediaTypes": "IMAGE",
      "depth": "1"
    }
  },
  "decoder": {
    "type": "DECODER",
    "factory": "ImageDecoder"
  }
}
```

#### Index Operators Configuration

Next up, we declare a list of [operators](vitrivr-engine-core/src/main/kotlin/org/vitrivr/engine/core/operators/Operator.kt)
in the form of [`OperatorConfig`](vitrivr-engine-core/src/main/kotlin/org/vitrivr/engine/core/config/ingest/operator/OperatorConfig.kt)s.
These _operators_ must have a unique name in the `operators` property of the [`IngestionConfig`](vitrivr-engine-core/src/main/kotlin/org/vitrivr/engine/core/config/ingest/IngestionConfig.kt):

```json
{
  "schema": "sandbox",
  "context": {
    "contentFactory": "InMemoryContentFactory",
    "resolverName": "disk"
  },
  "enumerator": {
    "type": "ENUMERATOR",
    "factory": "FileSystemEnumerator",
    "api": true,
    "parameters": {
      "path": "./sandbox/imgs",
      "mediaTypes": "IMAGE",
      "depth": "1"
    }
  },
  "decoder": {
    "type": "DECODER",
    "factory": "ImageDecoder"
  },
  "operators": {
    "myoperator1": {},
    "myoperator2": {}
  }
}
```

There are different _types_ of operators:

* [`Segmenter`](vitrivr-engine-core/src/main/kotlin/org/vitrivr/engine/core/operators/ingest/Segmenter.kt) which segment incoming content and emit _n_ [`Retrievable`](vitrivr-engine-core/src/main/kotlin/org/vitrivr/engine/core/model/retrievable/Retrievable.kt)s, resulting in a 1:n mapping.
* [`Transformer`](vitrivr-engine-core/src/main/kotlin/org/vitrivr/engine/core/operators/ingest/Segmenter.kt), [`Extractor`](vitrivr-engine-core/src/main/kotlin/org/vitrivr/engine/core/operators/ingest/Extractor.kt), and [`Exporter`](vitrivr-engine-core/src/main/kotlin/org/vitrivr/engine/core/operators/ingest/Exporter.kt), which all process one retrievable and emit _one_ [`Retrievable`](vitrivr-engine-core/src/main/kotlin/org/vitrivr/engine/core/model/retrievable/Retrievable.kt)s, resulting in a 1:1 mapping.
* [`Aggregator`](vitrivr-engine-core/src/main/kotlin/org/vitrivr/engine/core/operators/ingest/Aggregator.kt) which aggregate _n_ incoming retrievables and emit one [`Retrievable`](vitrivr-engine-core/src/main/kotlin/org/vitrivr/engine/core/model/retrievable/Retrievable.kt)s, resulting in a n:1 mapping.

Notably, `Extractor`s are backed by a schema's field and `Exporter`s are also referenced by name from the _schema_.

In the following, we briefly introduce these configurations:

##### Index Operators Configuration: Segmenter

A [`Segmenter`](vitrivr-engine-core/src/main/kotlin/org/vitrivr/engine/core/operators/ingest/Segmenter.kt) is a 1:n operator,
its [`OperatorConfig`](vitrivr-engine-core/src/main/kotlin/org/vitrivr/engine/core/config/ingest/operator/OperatorConfig.kt) looks as follows:

```json
{
  "type": "SEGMENTER",
  "factory": "FactoryClass",
  "parameters": {
    "key": "value"
  }
}
```

The `type` property is mandatory, equally so the `factory`, which has to point to a [`SegmenterFactory`](vitrivr-engine-core/src/main/kotlin/org/vitrivr/engine/core/operators/ingest/SegmenterFactory.kt) implementation.
The `parameters` property is optional and implementation dependent.

See [implementations](vitrivr-engine-index/src/main/kotlin/org/vitrivr/engine/index/segment/) 

##### Index Operators Configuration: Transformer

A [`Transformer`](vitrivr-engine-core/src/main/kotlin/org/vitrivr/engine/core/operators/ingest/Transformer.kt) is a 1:1 operator,
its [`OperatorConfig`](vitrivr-engine-core/src/main/kotlin/org/vitrivr/engine/core/config/ingest/operator/OperatorConfig.kt) looks as follows:

```json
{
  "type": "TRANSFORMER",
  "factory": "FactoryClass",
  "parameters": {
    "key": "value"
  }
}
```

The `type` property is mandatory, equally so the `factory`, which has to point to a [`TransformerFactory`](vitrivr-engine-core/src/main/kotlin/org/vitrivr/engine/core/operators/ingest/TransformerFactory.kt) implementation.
The `parameters` property is optional and implementation dependent.

See [implementations](vitrivr-engine-index/src/main/kotlin/org/vitrivr/engine/index/transform)

##### Index Operators Configuration: Exporter

A [`Exporter`](vitrivr-engine-core/src/main/kotlin/org/vitrivr/engine/core/operators/ingest/Exporter.kt) is a 1:1 operator,
its [`OperatorConfig`](vitrivr-engine-core/src/main/kotlin/org/vitrivr/engine/core/config/ingest/operator/OperatorConfig.kt) looks as follows:

```json
{
  "type": "EXPORTER",
  "exporterName": "name-from-schema",
  "parameters": {
    "key": "value"
  }
}
```

The `type` property is mandatory, equally so the `exporterName`, which has to point to an `Exporter` defined on the _schema_.
The `parameters` property is optional and implementation dependent.

See [implementations](vitrivr-engine-index/src/main/kotlin/org/vitrivr/engine/index/exporters)

##### Index Operators Configuration: Extractor

A [`Extractor`](vitrivr-engine-core/src/main/kotlin/org/vitrivr/engine/core/operators/ingest/Extractor.kt) is a 1:1 operator,
its [`OperatorConfig`](vitrivr-engine-core/src/main/kotlin/org/vitrivr/engine/core/config/ingest/operator/OperatorConfig.kt) looks as follows:

```json
{
  "type": "EXTRACTOR",
  "fieldName": "name-from-schema"
}
```

The `type` property is mandatory, equally so the `fieldName`, which has to point to a _field_ as defined on the _schema_.


See [implementations](vitrivr-engine-module-features/src/main/kotlin/org/vitrivr/engine/base/features/)

##### Index Operators Configuration: Aggregator

A [`Aggregator`](vitrivr-engine-core/src/main/kotlin/org/vitrivr/engine/core/operators/ingest/Aggregator.kt) is a 1:n operator,
its [`OperatorConfig`](vitrivr-engine-core/src/main/kotlin/org/vitrivr/engine/core/config/ingest/operator/OperatorConfig.kt) looks as follows:

```json
{
  "type": "AGGREGATOR",
  "factory": "FactoryClass",
  "parameters": {
    "key": "value"
  }
}
```

The `type` property is mandatory, equally so the `factory`, which has to point to a [`AggregatorFactory`](vitrivr-engine-core/src/main/kotlin/org/vitrivr/engine/core/operators/ingest/AggregatorFactory.kt) implementation.
The `parameters` property is optional and implementation dependent.

See [implementations](vitrivr-engine-index/src/main/kotlin/org/vitrivr/engine/index/aggregators)

#### Index Operations Configuration: The Pipeline

So far, we only have _declared_ the operators, with the `operations` property, we define the ingestion pipeline as a tree in the form of
[`OperationsConfig`](vitrivr-engine-core/src/main/kotlin/org/vitrivr/engine/core/config/ingest/operation/OperationsConfig.kt):

```json
{
  "schema": "sandbox",
  "context": {
    "contentFactory": "InMemoryContentFactory",
    "resolverName": "disk"
  },
  "enumerator": {
    "type": "ENUMERATOR",
    "factory": "FileSystemEnumerator",
    "api": true,
    "parameters": {
      "path": "./sandbox/imgs",
      "mediaTypes": "IMAGE",
      "depth": "1"
    }
  },
  "decoder": {
    "type": "DECODER",
    "factory": "ImageDecoder"
  },
  "operators": {
    "myoperator": {},
    "myoperator1": {},
    "myoperator2": {}
  },
  "operations": {
    "myOperation": {
      "operator": "myoperator",
      "next": [
        "nextOperation1",
        "nextOperation2"
      ]
    },
    "myOperation1": {
      "operator": "myoperator1"
    },
    "myOperation2": {
      "operator": "myoperator2"
    }
  }
}
```

Specifically, the `operator` property must point to a previously declared _operator_ and the entries in the `next` property must point to an _operation_ with that name.


Currently, there are the following rules to build such a pipeline:

**Pipeline Rules:**

1. The first _operation_ must either be a `TRANSFORMER` or `SEGMENTER`
2. `TRANSFORMER`s and `SEGMENTER`s can be daisy-chained 
3. A `SEGMENTER` must be followed by one or more `AGGREGATOR`s, multiple `AGGREGATORS` results in branching.
4. An `AGGREGATOR` must be followed by either a `EXTRACTOR` or `EXPORTER`
5. `EXPORTER`s and `EXTRACTOR`s can be daisy-chained
6. The end or the ends, in case of branching, must be of type `EXPORTER` or `EXTRACTOR`.

One example, based on the _schema_ further above (without branching), might look as follows:

```json
{
  "schema": "sandbox",
  "context": {
    "contentFactory": "InMemoryContentFactory",
    "resolverName": "disk"
  },
  "enumerator": {
    "type": "ENUMERATOR",
    "factory": "FileSystemEnumerator",
    "api": true,
    "parameters": {
      "path": "./sandbox/imgs",
      "mediaTypes": "IMAGE",
      "depth": "1"
    }
  },
  "decoder": {
    "type": "DECODER",
    "factory": "ImageDecoder"
  },
  "operators": {
    "pass": {
      "type": "SEGMENTER",
      "factory": "PassThroughSegmenter"
    },
    "allContent": {
      "type": "AGGREGATOR",
      "factory": "AllContentAggregator"
    },
    "avgColor": {
      "type": "EXTRACTOR",
      "fieldName": "averagecolor"
    },
    "thumbs": {
      "type": "EXPORTER",
      "exporterName": "thumbnail",
      "parameters": {
        "maxSideResolution": "350",
        "mimeType": "JPG"
      }
    },
    "fileMeta": {
      "type": "EXTRACTOR",
      "fieldName": "file"
    }
  },
  "operations": {
    "stage1": {"operator": "pass", "next": ["stage2"]},
    "stage2": {"operator": "allContent", "next": ["stage3"]},
    "stage3": {"operator": "avgColor", "next": ["stage4"]},
    "stage4": {"operator": "thumbs", "next": ["stage5"]},
    "stage5": {"operator": "fileMeta"}
  }
}
```

Here, the linear pipeline is: `pass` -> `allContent` -> `avgColor` -> `thumbs` -> `fileMeta`.

#### Complete Sandbox Configuration

After following above's guide on how to build your _schema_ config and your _index pipeline_ config,
the files should be similar as follows.

The schema config:

```json
{
  "schemas": [{
    "name": "sandbox",
    "connection": {
      "database": "CottontailConnectionProvider",
      "parameters": {
        "Host": "127.0.0.1",
        "port": "1865"
      }
    },
    "fields": [
      {
        "name": "averagecolor",
        "factory": "AverageColor"
      },
      {
        "name": "file",
        "factory": "FileSourceMetadata"
      }
    ],
    "exporters": [
      {
        "name": "thumbnail",
        "factory": "ThumbnailExporter",
        "resolver": {
          "factory": "DiskResolver",
          "parameters": {
            "location": "./thumbnails/sandbox"
          }
        },
        "parameters": {
          "maxSideResolution": "400",
          "mimeType": "JPG"
        }
      }
    ],
    "extractionPipelines": [
      {
        "name": "sandboxpipeline",
        "path": "./sandbox-pipeline.json"
      }
    ]
  }]
}

```

The pipeline config:

```json
{
  "schema": "sandbox",
  "context": {
    "contentFactory": "InMemoryContentFactory",
    "resolverName": "disk"
  },
  "enumerator": {
    "type": "ENUMERATOR",
    "factory": "FileSystemEnumerator",
    "api": true,
    "parameters": {
      "path": "./sandbox/imgs",
      "mediaTypes": "IMAGE",
      "depth": "1"
    }
  },
  "decoder": {
    "type": "DECODER",
    "factory": "ImageDecoder"
  },
  "operators": {
    "pass": {
      "type": "SEGMENTER",
      "factory": "PassThroughSegmenter"
    },
    "allContent": {
      "type": "AGGREGATOR",
      "factory": "AllContentAggregator"
    },
    "avgColor": {
      "type": "EXTRACTOR",
      "fieldName": "averagecolor"
    },
    "thumbs": {
      "type": "EXPORTER",
      "exporterName": "thumbnail",
      "parameters": {
        "maxSideResolution": "350",
        "mimeType": "JPG"
      }
    },
    "fileMeta": {
      "type": "EXTRACTOR",
      "fieldName": "file"
    }
  },
  "operations": {
    "stage1": {"operator": "pass", "next": ["stage2"]},
    "stage2": {"operator": "allContent", "next": ["stage3"]},
    "stage3": {"operator": "avgColor", "next": ["stage4"]},
    "stage4": {"operator": "thumbs", "next": ["stage5"]},
    "stage5": {"operator": "fileMeta"}
  }
}
```

---

#### Starting the indexing pipeline

To start the actual pipeline, we start [the server module](/vitrivr-engine-server)'s [`Main`](vitrivr-engine-server/src/main/kotlin/org/vitrivr/engine/server/Main.kt)
with the path to the schema configuration as argument.

For this to work you either build the stack or you use an IDE.

1. Then, when the server is running, we have to first initialise the database (since our schema is named `sandbox`):

```
sandbox init
```

2. The extraction is started via the CLI by calling:
```
sandbox extract -c sandbox-pipeline.json
```

Which should result in logging messages that confirm the usage of our ThumbnailExporter (including its parameters) and the message:
```
Started extraction job with UUID <uuid>
```

3. The server (by default) provides an [OpenAPI swagger ui](http://localhost:7070/swagger-ui) with which the job status can be queried.
The same can be achieved by this cURL command, where `<uuid>` is the UUID printed above (and again, we have named our schema `sandbox`, hence the sandbox path:

```bash
curl -X 'GET' \
  'http://localhost:7070/api/sandbox/index/<uuid>' \
  -H 'accept: application/json'
```

### Querying / Retrieval

## Getting Started: Development

This is a Gradle-powered Kotlin project, we assume prerequisites are handled accordingly.

1. Generate the OpenApi client code by executing the (top-level) `generateOpenApi` gradle task
2. Start developing

If you develop another module (plugin), please keep in mind that the providers and factories are
exposed in `<your-module>/resources/META-INF/services/`

```
./gradlew openApiGenerate
```


