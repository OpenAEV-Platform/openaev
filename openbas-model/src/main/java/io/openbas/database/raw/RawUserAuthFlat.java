package io.openbas.database.raw;

import io.openbas.database.model.Grant;

public interface RawUserAuthFlat {

  // From users table
  String getUser_id();

  boolean getUser_admin();

  // From grants table (via join)
  String getGrant_id();

  String getGrant_name();

  String getGrant_resource();

  Grant.GRANT_RESOURCE_TYPE getGrant_resource_type();
}
