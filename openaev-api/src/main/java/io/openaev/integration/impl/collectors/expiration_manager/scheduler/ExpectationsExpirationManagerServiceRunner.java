package io.openaev.integration.impl.collectors.expiration_manager.scheduler;

import io.openaev.collectors.expectations_expiration_manager.service.ExpectationsExpirationManagerService;

public class ExpectationsExpirationManagerServiceRunner implements Runnable {
  private final ExpectationsExpirationManagerService expectationsExpirationManagerService;
  private final String tenantId;

  private ExpectationsExpirationManagerServiceRunner(
      String tenantId, ExpectationsExpirationManagerService expectationsExpirationManagerService) {
    this.expectationsExpirationManagerService = expectationsExpirationManagerService;
    this.tenantId = tenantId;
  }

  @Override
  public void run() {
    expectationsExpirationManagerService.computeExpectations(tenantId);
  }

  public static ExpectationsExpirationManagerServiceRunner runnerForTenant(
      String tenantId, ExpectationsExpirationManagerService service) {
    return new ExpectationsExpirationManagerServiceRunner(tenantId, service);
  }
}
