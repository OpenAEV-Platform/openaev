package io.openaev.opencti.connectors;

import io.openaev.database.model.Capability;
import java.util.HashSet;
import java.util.Set;

public class Constants {
  public static final String PROCESS_STIX_ROLE_ID = "2c24790b-fa69-4565-8dc8-b00f85ca47d5";
  public static final String PROCESS_STIX_ROLE_NAME = "STIX bundle processors";
  public static final String PROCESS_STIX_ROLE_DESCRIPTION = "Can process STIX bundles via API";
  public static final Set<Capability> PROCESS_STIX_ROLE_CAPABILITIES =
      new HashSet<>(Set.of(Capability.MANAGE_STIX_BUNDLE));

  public static final String PROCESS_STIX_GROUP_ID = "0b4db570-fdf4-44e9-8daa-39130189fec8";
  public static final String PROCESS_STIX_GROUP_NAME = "STIX bundle processors";
  public static final String PROCESS_STIX_GROUP_DESCRIPTION =
      "Group for granting access rights to the STIX bundle API";
}
