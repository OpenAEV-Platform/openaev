package io.openaev.health;

/**
 * Used size, in bytes, of each storage dependency. A {@code null} value means the metric could not
 * be retrieved by the last probe.
 *
 * @param pgUsedSize size of the PostgreSQL database
 * @param esUsedSize size of the engine (Elasticsearch/OpenSearch) indexes, replicas excluded
 * @param s3UsedSize size of the objects stored in the bucket
 */
public record StorageUsage(Long pgUsedSize, Long esUsedSize, Long s3UsedSize) {

  public static StorageUsage unknown() {
    return new StorageUsage(null, null, null);
  }
}
