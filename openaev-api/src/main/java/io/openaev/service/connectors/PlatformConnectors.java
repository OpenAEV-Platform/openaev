package io.openaev.service.connectors;

import io.openaev.injectors.openaev.OpenAEVImplantContract;
import io.openaev.integration.impl.executors.openaev.OpenAEVExecutorIntegration;

/**
 * The two connectors that ARE the platform.
 *
 * <p>The implant injector executes every payload and the agent executor drives every agent, so
 * removing either one breaks execution itself. Every other connector is removable, including the
 * ones running in-process: they are shipped with the platform, not vital to it, and a legacy row
 * whose implementation was dropped from the code in an earlier version has to be cleanable.
 * In-process connectors that are still implemented register again on the next startup.
 *
 * <p>Matched on the connector type rather than the seeded id: ids are per tenant, types are stable.
 */
public final class PlatformConnectors {

  public static boolean isPlatformInjector(String injectorType) {
    return OpenAEVImplantContract.TYPE.equals(injectorType);
  }

  public static boolean isPlatformExecutor(String executorType) {
    return OpenAEVExecutorIntegration.OPENAEV_EXECUTOR_TYPE.equals(executorType);
  }

  private PlatformConnectors() {}
}
