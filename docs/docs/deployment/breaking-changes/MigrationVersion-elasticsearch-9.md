# Elasticsearch 9 and search engine auto-detection

!!! info ""

    * **Introduced in**: `OpenAEV [MigrationVersion]`

## Description of changes

The platform now supports Elasticsearch 9 next to Elasticsearch 8 and OpenSearch, and picks the right
client by itself. At startup it calls `GET /` on `engine.url`, reads the version the server reports and
instantiates the matching client:

| Server                  | Client used          |
|:------------------------|:---------------------|
| Elasticsearch 8.19+     | Elasticsearch 8.x    |
| Elasticsearch 9         | Elasticsearch 9.x    |
| Elasticsearch Serverless | Elasticsearch 9.x (most recent client), recognised by its `serverless` build flavor |
| OpenSearch              | OpenSearch           |

Each Elasticsearch major needs its own client: the 9.x client stamps every request with an
`application/vnd.elasticsearch+json; compatible-with=9` content type, which an 8.x server rejects
outright.

The `engine.engine-selector` parameter (`ENGINE_ENGINE_SELECTOR`) is **removed**. A value still set is
ignored, and the platform logs a warning at startup. When the detection cannot decide, for example behind a
proxy that blocks `GET /`, set `engine.client` (`ENGINE_CLIENT`) to `elasticsearch8`, `elasticsearch9` or
`opensearch`: the platform then uses that client without probing. Its default, `auto`, keeps the detection. With AWS SigV4 authentication
(`engine.engine-aws-mode` set to `es` or `aoss`), the platform uses the OpenSearch client without
probing, as before.

The platform refuses to start, with an explicit message, when the server cannot be reached, rejects the
credentials, or is older than Elasticsearch 8.

## Migration

Nothing to do: remove `engine.engine-selector` from your configuration to get rid of the startup warning.
Do not copy its value to `engine.client`: leave `engine.client` unset unless the detection fails, otherwise
the client stays pinned when the cluster is upgraded.

To move from Elasticsearch 8 to Elasticsearch 9, upgrade the cluster following
[Elastic's upgrade documentation](https://www.elastic.co/docs/deploy-manage/upgrade), then restart
OpenAEV: it detects the new version and switches client. The indices are kept. Elasticsearch 9 only reads
indices created by 8.x or later, so any index still carrying a 7.x creation version has to be reindexed
or removed first — Elastic's Upgrade Assistant reports them.

The platform owns every index it queries and can rebuild them from PostgreSQL, so an alternative to
migrating the data is to point OpenAEV at an empty Elasticsearch 9 cluster:

1. Stop OpenAEV and set `engine.url` to the new cluster.
2. Delete the content of the `indexing_status` table in PostgreSQL. It records, per model, how far the
   data was already indexed: left in place, it makes the platform skip everything indexed into the old
   cluster, and the new indices only receive later changes.
3. Start OpenAEV: the indices, templates and lifecycle policy are recreated and reindexed from scratch.

Expect the initial reindex to take a while on a large dataset, and dashboards to be incomplete until it
finishes.

## Clusters served over HTTPS

With Elasticsearch 9, a cluster whose certificate is signed by an internal CA is trusted as soon as that
CA is dropped in `openaev.extra-trusted-certs-dir`, like every other outgoing connection of the platform
(see [certificate validation](../platform/certificate-validation.md)). The Elasticsearch 8 client reads
the JVM default trust store only: import the CA into that trust store to reach such a cluster on
Elasticsearch 8. `engine.reject-unauthorized=false` also works, but it disables certificate and hostname
verification entirely, so keep it for tests.
