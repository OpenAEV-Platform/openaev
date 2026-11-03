package io.openaev.config;

import io.openaev.database.model.Capability;
import io.openaev.database.model.User;
import java.util.Set;

/**
 * Who reads past markings: shared by the HTTP scope ({@link HttpMarkingScopeSupplier}) and the
 * stream, so the two can never disagree on who sees a marked asset.
 */
public final class MarkingBypass {

  private MarkingBypass() {}

  /**
   * Whether the user reads every row, whatever its markings.
   *
   * <p>AGENT_RUNTIME_ACCESS is the well-known capability of the per-tenant agent/implant service
   * account (AgentServiceAccountPrivilegeService). That account has no group-marking grants of its
   * own — it exists only to run the agent/implant API surface — so without this it would lose sight
   * of its own host asset the moment that asset is marked. An agent must always see the asset it is
   * installed on, independent of what markings are later applied to it.
   *
   * <p>MANAGE_STIX_BUNDLE is the same kind of capability (hidden, held by the per-tenant OpenCTI
   * connector user, which has no group-marking grants either): the security coverage it processes
   * must build its scenario from every asset of the tenant, not from the unmarked subset a
   * marking-less user would see. This is the READ clearance only - the dispatch-time check
   * (InjectService.resolveLaunchedByClearance) deliberately does not use it.
   */
  public static boolean bypassesMarkings(User user) {
    Set<Capability> capabilities = user.getCapabilities();
    return user.isAdminOrBypass()
        || capabilities.contains(Capability.AGENT_RUNTIME_ACCESS)
        || capabilities.contains(Capability.MANAGE_STIX_BUNDLE);
  }
}
