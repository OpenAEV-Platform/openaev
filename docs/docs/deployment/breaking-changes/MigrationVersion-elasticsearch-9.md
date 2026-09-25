# Elasticsearch 9

!!! info ""

    * **Introduced in**: `OpenAEV [MigrationVersion]`

## Description of changes

The platform now uses the 9.x Elasticsearch Java client. That client stamps every request with an
`application/vnd.elasticsearch+json; compatible-with=9` content type, which an 8.x server rejects
outright:

```json
{"error":{"type":"media_type_header_exception","reason":"Invalid media-type value on headers [Accept, Content-Type]"},"status":400}
```

Elasticsearch 8 is therefore no longer usable: every engine call fails, and the platform refuses to
start. **Upgrade your cluster to Elasticsearch 9 before upgrading OpenAEV.** OpenSearch deployments
are unaffected.

## Migration

1. Upgrade the Elasticsearch cluster to 9.x, following
   [Elastic's upgrade documentation](https://www.elastic.co/docs/deploy-manage/upgrade). Elasticsearch 9
   only reads indices created by 8.x or later, so any index still carrying a 7.x creation version has
   to be reindexed or removed first — Elastic's Upgrade Assistant reports them.
2. Upgrade OpenAEV.

The platform owns every index it queries and can rebuild them from PostgreSQL, so an alternative to
migrating the data is to point OpenAEV at an empty Elasticsearch 9 cluster: the indices, templates and
lifecycle policy are recreated at startup and reindexed from scratch. Expect the initial reindex to
take a while on a large dataset, and dashboards to be incomplete until it finishes.

## Clusters served over HTTPS

A cluster whose certificate is signed by an internal CA is now trusted as soon as that CA is dropped
in `openaev.extra-trusted-certs-dir`, like every other outgoing connection of the platform (see
[certificate validation](../certificate-validation.md)). The engine client used to read the JVM
default trust store only, which left `engine.reject-unauthorized=false` — no verification at all — as
the only way to reach such a cluster. That parameter still works, and is no longer needed here.
