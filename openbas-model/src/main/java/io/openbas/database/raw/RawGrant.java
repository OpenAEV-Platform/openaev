package io.openbas.database.raw;

import io.openbas.database.model.Grant;

public interface RawGrant {
  String getGrant_id();

  String getGrant_name();

  String getUser_id();

  String getGrant_resource();

  Grant.GRANT_RESOURCE_TYPE getGrant_resource_type();
}
