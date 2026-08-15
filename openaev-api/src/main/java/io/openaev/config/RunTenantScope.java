package io.openaev.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@code TxCtx} controller parameter as a SERVICE-IDENTITY scope derived from the parent
 * autonomous run named by the {@code {runId}} path variable, instead of from the caller's tenant
 * memberships.
 *
 * <p>The orchestrator callback endpoints (events / status / directive consumption / attack-path
 * authoring, evaluation and state / scope get-set / target-teams / promote-finding-to-asset) are
 * reached by XTM One with a per-user cross-platform JWT that carries NO tenant claim and NO {@code
 * X-Tenant-Ids} header on the legacy non-prefixed path. Resolving that empty selector to the
 * caller's memberships (the default {@link TxCtxArgumentResolver} behaviour) couples the callback
 * to whichever tenants the caller happens to belong to: a member of the run's tenant works, but a
 * caller whose scope does not pin the run's tenant reads nothing and the callback dies on the run
 * lookup (404). That is wrong for a service callback whose authority is the run itself.
 *
 * <p>With this annotation the scope is taken from the run's own immutable {@code tenant_id} (looked
 * up by primary key, scope-free, in {@link AutonomousRunTenantLocator}), so the callback always
 * acts on the run's tenant regardless of the caller's selector or memberships, and every write it
 * makes is stamped with that tenant. An unknown run resolves to {@link
 * io.openaev.context.TxCtx#missing()} (fail-closed), which the service then reports as a 404
 * through its own run lookup.
 *
 * <p>The derivation applies ONLY on the legacy non-prefixed route (the one the orchestrator
 * actually calls). On the tenant-prefixed operator route the handler keeps the standard
 * caller-authorized resolution - the {@code {tenantId}} the URL names stays the boundary - so the
 * annotation never turns the prefixed API into a second, caller-independent door to another
 * tenant's run.
 *
 * <p>Deliberately NOT applied to the operator-facing endpoints (create / list / get / timeline /
 * start-pause-resume-cancel-restart / queue-directive / configuration): those stay tenant-isolated
 * against the caller's scope exactly as multi-tenancy v2 made them. This annotation only restores
 * the run-authoritative posture the callbacks had before the {@code autonomous_*} tables became
 * tenant-active, without weakening operator isolation. The EE-license gate and the run's existence
 * remain the callback's guardrails; restricting the callbacks to the actual service identity is a
 * separate hardening tracked apart from tenant correctness.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface RunTenantScope {}
