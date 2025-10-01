package io.openaev.database.raw;

import java.time.Instant;

public interface RawTag {

  String getTag_id();

  String getTag_name();

  String getTag_color();

  Instant getTag_created_at();

  Instant getTag_updated_at();
}
