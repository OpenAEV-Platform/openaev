package io.openbas.rest.rbac;

import io.openbas.aop.RBAC;

public enum EndpointTestScenarios {
  ADMIN {
    public boolean shouldBeAllowed(RBAC rbac) {
      return true;
    }
  },
  GROUP_WITH_BYPASS {
    public boolean shouldBeAllowed(RBAC rbac) {
      return true;
    }
  },
  GROUP_NO_ROLE {
    public boolean shouldBeAllowed(RBAC rbac) {
      return false;
    }
  },
  GROUP_ROLE_NO_CAPABILITY {
    public boolean shouldBeAllowed(RBAC rbac) {
      return false;
    }
  },
  RESOURCE_GRANT_ONLY {
    public boolean shouldBeAllowed(RBAC rbac) {
      return true;
    } // ✅ FIXED
  },
  RESOURCE_ROLE_MATCH {
    public boolean shouldBeAllowed(RBAC rbac) {
      return true;
    }
  },
  NO_RESOURCE_ROLE_MATCH {
    public boolean shouldBeAllowed(RBAC rbac) {
      return true;
    }
  };

  public abstract boolean shouldBeAllowed(RBAC rbac);
}
