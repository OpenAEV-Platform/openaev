package io.minio.custom;

import com.google.common.collect.Multimap;
import io.minio.ObjectConditionalReadArgs;

/**
 * Cloned class to work around a MinIO limitation with handling copy
 * of paths with leading slash when interacting with actual AWS S3 buckets.
 * It overrides a single method to restore a non escaped path as copy source.
 */
public class CopySource extends io.minio.CopySource {
  public CopySource() {
    super();
  }

  public CopySource(ObjectConditionalReadArgs args) {
    super(args);
  }

  public static Builder customBuilder() {
    return new Builder();
  }

  /** Argument builder of {@link CopySource}. */
  public static final class Builder
      extends ObjectConditionalReadArgs.Builder<Builder, CopySource> {}

  /**
   * Force restoring the full AWS-compatible source path
   *
   * @return AWS S3 copy headers
   */
  @Override
  public Multimap<String, String> genCopyHeaders() {
    Multimap<String, String> minioHeaders = super.genCopyHeaders();

    // restore the original copy path with bucket name prefix
    minioHeaders.put("x-amz-copy-source", "/" + bucketName + "/" + objectName);
    return minioHeaders;
  }
}
