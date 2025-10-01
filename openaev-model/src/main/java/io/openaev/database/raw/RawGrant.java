package io.openaev.database.raw;

import io.openaev.database.model.Grant;

public interface RawGrant {
  String getGrant_id();

  String getGrant_name();

  String getUser_id();

  String getGrant_resource();

  Grant.GRANT_RESOURCE_TYPE getGrant_resource_type();
}
