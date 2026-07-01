package io.openaev.database.model;

/**
 * Cloud platform hosting a {@link AssetCategory#CLOUD_RESOURCE} asset. Combined with {@code
 * asset_cloud_native_type} (free-form, e.g. {@code ec2}, {@code s3_bucket}, {@code
 * azure_virtual_machine}) and the cloud service {@link AssetSubCategory}, this models the full
 * breadth of cloud resources (mirrors the Wiz cloud-resource schema).
 */
public enum CloudProvider {
  AWS,
  AZURE,
  GCP,
  OCI,
  ALIBABA,
  KUBERNETES,
  OTHER;
}
