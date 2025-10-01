package io.openaev.database.raw;

import java.util.Set;

public interface RawUserAuth {

  String getUser_id();

  boolean getUser_admin();

  Set<RawGrant> getUser_grants();
}
