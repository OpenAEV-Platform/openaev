# Elasticsearch 9

!!! info ""

    * **Introduced in**: `OpenAEV 3.260921.0`

## Description of changes

The platform can now use an Elasticsearch 9 cluster. Set `engine.engine-selector` to `elk9` to use the 9.x
Elasticsearch Java client. The default `elk` selector keeps the 8.x client, so Elasticsearch 8 and OpenSearch
deployments are unaffected.

!!! note "Future requirement"

    A future release will require Elasticsearch 9. No version is planned yet; this page will say when.

The 9.x client stamps every request with an `application/vnd.elasticsearch+json; compatible-with=9` content type,
which an 8.x server rejects outright:

```json
{"error":{"type":"media_type_header_exception","reason":"Invalid media-type value on headers [Accept, Content-Type]"},"status":400}
```

Do not use the `elk9` selector with an Elasticsearch 8 cluster: every engine call fails.

## Migration

1. Upgrade the Elasticsearch cluster to 9.x, following
   [Elastic's upgrade documentation](https://www.elastic.co/docs/deploy-manage/upgrade). Elasticsearch 9
   only reads indices created by 8.x or later, so any index still carrying a 7.x creation version has
   to be reindexed or removed first — Elastic's Upgrade Assistant reports them.
2. Upgrade OpenAEV to 3.260921.0 or later.
3. Set `engine.engine-selector=elk9`.

The platform owns every index it queries and can rebuild them from PostgreSQL, so an alternative to
migrating the data is to point OpenAEV at an empty Elasticsearch 9 cluster: the indices, templates and
lifecycle policy are recreated at startup and reindexed from scratch. Expect the initial reindex to
take a while on a large dataset, and dashboards to be incomplete until it finishes.

## Clusters served over HTTPS

With the `elk9` selector, a cluster whose certificate is signed by an internal CA is trusted as soon as that CA is dropped
in `openaev.extra-trusted-certs-dir`, like every other outgoing connection of the platform (see
[certificate validation](../certificate-validation.md)). The `elk` client reads the JVM default trust store
only, which leaves `engine.reject-unauthorized=false` — no verification at all — as the only way to reach such a
cluster. That parameter still works with `elk9`, and is no longer needed there.
