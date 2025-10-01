package io.openaev.database.raw;

import java.time.Instant;
import java.util.Set;

public interface RawAsset {

  String getAsset_id();

  String getAsset_type();

  String getAsset_name();

  String getAsset_description();

  String getAsset_external_reference();

  String getEndpoint_platform();

  Instant getAsset_created_at();

  Instant getAsset_updated_at();

  // relations
  Set<String> getAsset_findings();

  Set<String> getAsset_tags();
}
