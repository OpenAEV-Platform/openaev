package io.openaev.service;

/**
 * Scope a user is created from. It decides which auto-assign groups the new user inherits: the
 * creator can only hand out groups it has authority over.
 */
public enum UserCreationScope {
  /**
   * Creation from the platform: the platform administrator has authority over every tenant, so the
   * platform auto-assign groups are granted, plus those of each tenant carried by the input.
   */
  PLATFORM,

  /**
   * Creation from within a tenant: the creator has no authority over the platform, so no
   * platform-wide group is ever granted and the tenants carried by the input are ignored. The
   * caller attaches the user to its own tenant, then grants that tenant's auto-assign groups.
   */
  TENANT
}
