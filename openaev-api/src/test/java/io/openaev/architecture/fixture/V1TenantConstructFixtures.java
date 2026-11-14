package io.openaev.architecture.fixture;

import io.openaev.context.AmbientTenantBridge;
import io.openaev.context.TenantContext;
import jakarta.persistence.EntityListeners;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Every shape of v1 construct the v1 construct guard must find, and the code it must leave alone.
 * Nothing here runs: the scan reads the class files only. The entity shapes carry no
 * {@code @Entity}, so no Spring context picks them up. The query shapes are compiled by {@code
 * V1TenantConstructRulesTest} itself, since this module refuses Spring Data query annotations.
 */
public final class V1TenantConstructFixtures {

  private V1TenantConstructFixtures() {}

  // -- CALLS --

  public static class ReadsTheAmbientTenant {
    public String read() {
      return TenantContext.getCurrentTenant();
    }
  }

  public static class ReadsTheAmbientTenantTwice {
    public boolean readTwice() {
      String first = TenantContext.getCurrentTenant();
      String second = TenantContext.getCurrentTenant();
      return first.equals(second);
    }
  }

  public static class SetsTheAmbientTenantThroughAMethodReference {
    public Consumer<String> setter() {
      return TenantContext::setCurrentTenant;
    }
  }

  public static class ClearsTheAmbientTenantInsideALambda {
    public Runnable later() {
      return () -> TenantContext.clearCurrentTenant();
    }
  }

  public static class ChecksTheAmbientTenantInsideANestedLambda {
    public Supplier<Supplier<Boolean>> nested() {
      return () -> () -> TenantContext.hasCurrentTenant();
    }
  }

  /** One call in a {@code finally}: one source line, several calls in the class file. */
  public static class ClearsTheAmbientTenantInAFinally {
    public String readThenClear() {
      try {
        return TenantContext.getCurrentTenant();
      } finally {
        TenantContext.clearCurrentTenant();
      }
    }
  }

  public static class CallsTheBridge {
    public String callIn(AmbientTenantBridge bridge, String tenantId) {
      return bridge.callInTenant(tenantId, () -> "work");
    }
  }

  public static class ReadsNoAmbientTenant {
    public String read(String tenantId) {
      return tenantId;
    }
  }

  // -- LISTENERS --

  public static class StampsFromTheAmbientTenant {
    public void stamp(Object entity) {
      TenantContext.getCurrentTenant();
    }
  }

  /** Inherits the ambient read: a listener whose superclass reads it is a tenant listener too. */
  public static class InheritsTheAmbientStamp extends StampsFromTheAmbientTenant {}

  public static class StampsNothing {
    public void stamp(Object entity) {
      entity.hashCode();
    }
  }

  @EntityListeners({StampsNothing.class, StampsFromTheAmbientTenant.class})
  public static class StampedEntity {}

  @EntityListeners(InheritsTheAmbientStamp.class)
  public static class StampedByASubclassEntity {}

  @EntityListeners(StampsNothing.class)
  public static class UnstampedEntity {}
}
