package io.openaev.database.raw;

import java.time.Instant;

public interface RawAssetGroupIndexing {

  String getAsset_group_id();

  String getAsset_group_name();

  Instant getAsset_group_updated_at();

  Instant getAsset_group_created_at();
}
