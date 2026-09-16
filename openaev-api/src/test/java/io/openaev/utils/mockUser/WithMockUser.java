package io.openaev.utils.mockUser;

import io.openaev.database.model.Capability;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface WithMockUser {
  String userFirstName() default "";

  String userLastName() default "";

  String userMail() default "";

  boolean isAdmin() default false;

  Capability[] withCapabilities() default {};

  /**
   * Whether the listener grants this mock user membership in {@link
   * io.openaev.database.model.Tenant#DEFAULT_TENANT_UUID} (mirroring production, where every real
   * user belongs to at least one tenant). Defaults to {@code false}: most tests either don't care
   * about tenant membership or already manage their own precise membership set explicitly via
   * {@code tenantRepository.addUserToTenant(...)} (idempotent, ON CONFLICT DO NOTHING). Adding a
   * membership unconditionally for every mock user is NOT safe as a global default: it silently
   * turns every "single authorized tenant" caller into a "two authorized tenants" caller for any
   * test that also grants its own tenant, which trips the "tenant scope already set for this
   * transaction" guard on nested @Transactional calls, and defeats every test that asserts a 400 on
   * an ambiguous/missing tenant selector (fallbackSelector no longer sees an unambiguous single
   * tenant). Set to {@code true} only for tests that need an endpoint to attribute a tenant for
   * writes (via {@code TxCtxArgumentResolver} / {@code TenantWriteScopeResolver}) and don't
   * otherwise grant the mock user any other tenant membership.
   */
  boolean autoJoinDefaultTenant() default false;
}
