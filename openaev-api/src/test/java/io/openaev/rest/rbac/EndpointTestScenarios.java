package io.openaev.rest.rbac;

import io.openaev.aop.AccessControl;

public enum EndpointTestScenarios {
  ADMIN {
    public boolean shouldBeAllowed(AccessControl accessControl) {
      return true;
    }
  },
  GROUP_WITH_BYPASS {
    public boolean shouldBeAllowed(AccessControl accessControl) {
      return true;
    }
  },
  GROUP_NO_ROLE {
    public boolean shouldBeAllowed(AccessControl accessControl) {
      return false;
    }
  },
  GROUP_ROLE_NO_CAPABILITY {
    public boolean shouldBeAllowed(AccessControl accessControl) {
      return false;
    }
  },
  RESOURCE_GRANT_ONLY {
    public boolean shouldBeAllowed(AccessControl accessControl) {
      return true;
    } // ✅ FIXED
  },
  RESOURCE_ROLE_MATCH {
    public boolean shouldBeAllowed(AccessControl accessControl) {
      return true;
    }
  },
  NO_RESOURCE_ROLE_MATCH {
    public boolean shouldBeAllowed(AccessControl accessControl) {
      return true;
    }
  };

  public abstract boolean shouldBeAllowed(AccessControl accessControl);
}
