package io.openaev.service.account;

import io.openaev.database.model.Capability;
import java.util.HashSet;
import java.util.Set;

public class Constants {
  public static final String SERVICE_ROLE_NAME = "Service integration";
  public static final String SERVICE_ROLE_DESCRIPTION =
      "Allows running an agent/implant with the minimal required API endpoints.";
  public static final Set<Capability> SERVICE_ROLE_CAPABILITIES =
      new HashSet<>(Set.of(Capability.AGENT_RUNTIME_ACCESS, Capability.ACCESS_DOCUMENTS));

  public static final String SERVICE_GROUP_NAME = "Service integration";
  public static final String SERVICE_GROUP_DESCRIPTION =
      "Group for granting access rights to the Agent/Implant API";
}
