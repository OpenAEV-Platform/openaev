package io.openaev.service.account;

import io.openaev.database.model.Capability;
import java.util.Set;

public final class Constants {
  private Constants() {}

  public static final String SERVICE_ROLE_ID = "b2668023-1a92-4d7d-9f98-5e7bef6eae7c";
  public static final String SERVICE_ROLE_NAME = "Service integration";
  public static final String SERVICE_ROLE_DESCRIPTION =
      "Allows running an agent/implant with the minimal required API endpoints.";
  public static final Set<Capability> SERVICE_ROLE_CAPABILITIES =
      Set.of(Capability.AGENT_RUNTIME_ACCESS, Capability.ACCESS_DOCUMENTS);

  public static final String SERVICE_GROUP_ID = "3768d85b-dc56-4bd4-a844-2851dc41e3a8";
  public static final String SERVICE_GROUP_NAME = "Service integration";
  public static final String SERVICE_GROUP_DESCRIPTION =
      "Group for granting access rights to the Agent/Implant API";
}
