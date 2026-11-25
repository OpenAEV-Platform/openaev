package io.openaev.architecture.fixture;

import io.openaev.context.TxCtx;
import io.openaev.database.model.User;
import io.openaev.service.PermissionService;
import io.openaev.service.account.CallerRightsInTenant;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;

/**
 * Violates both caller allowlists: loads a user's rights in the ambient tenant, and asks for the
 * caller in the ambient tenant, without being listed.
 */
public class CallerRightsOutsideAllowlistFixture {

  private final CallerRightsInTenant callerRightsInTenant;
  private final PermissionService permissionService;

  public CallerRightsOutsideAllowlistFixture(
      CallerRightsInTenant callerRightsInTenant, PermissionService permissionService) {
    this.callerRightsInTenant = callerRightsInTenant;
    this.permissionService = permissionService;
  }

  public User rightsOf(User user) {
    return callerRightsInTenant.loadInAmbientTenant(user);
  }

  public UnaryOperator<User> rightsAsReference() {
    return callerRightsInTenant::loadInAmbientTenant;
  }

  public User ambientCaller(User user, TxCtx ctx) {
    return permissionService.callerInAmbientTenant(user, ctx);
  }

  public BiFunction<User, TxCtx, User> ambientCallerAsReference() {
    return permissionService::callerInAmbientTenant;
  }
}
