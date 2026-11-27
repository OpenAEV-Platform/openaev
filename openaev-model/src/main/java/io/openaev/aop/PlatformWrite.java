package io.openaev.aop;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@code @Transactional} method that takes a {@code TxCtx} as a platform-write transaction:
 * {@link TenantScopeTransactionAspect} sets the {@code app.platform_write} transaction-local
 * setting for its duration, which is what {@code can_write_tenant} and {@code tenant_write_value}
 * check before letting a write touch, or create, a dual-scope table's platform row ({@code
 * tenant_id IS NULL}). The aspect does nothing for any other method, so the annotation is refused
 * anywhere else ({@code TenantActiveTableAccessArchTest}).
 *
 * <p>The annotation is used on:
 *
 * <ul>
 *   <li>the platform settings endpoints ({@code PlatformSettingsApi}, {@code /api/settings}), which
 *       exist on the platform route only and are gated by platform capabilities;
 *   <li>{@code XtmComposerApi#register} and {@code #refreshConnectivity}, which write the XTM
 *       Composer registration, a platform row. They are mounted on the platform route and on the
 *       tenant route (the composer's own identity keeps its access, {@code TODO v2: #7248}), and
 *       are gated by the platform setting write right: the tenant-scoped catalog write that {@code
 *       MANAGE_TENANT_SETTINGS} grants does not reach them on either route;
 *   <li>{@code PlatformRoleApi} (create, update and delete), the platform roles being rows with no
 *       tenant;
 *   <li>{@code AdminPrivilegeService#ensurePlatformAdminGroup}, which writes the platform {@code
 *       Admin} role and group at every boot.
 * </ul>
 *
 * <p>Off the request thread the marker is set by the {@code executeAsPlatformWrite}, {@code
 * executeNewAsPlatformWrite} and {@code setPlatformWriteOnCurrentTransaction} methods of {@code
 * TenantScopedTransaction}, which have more callers than the ones above. The list of writers and
 * the check that each one sits under one of these markers is the {@code
 * parameters_platform_row_writers_run_under_platform_write} architecture rule ({@code
 * TenantActiveTableAccessArchTest}); the code does not otherwise stop a new caller from setting the
 * marker, so review it there.
 *
 * <p>Adding this to a method does not by itself authorize anything: {@code @AccessControl} still
 * decides who may call it. This annotation only lifts the SQL-level refusal of a platform-row write
 * for a transaction the server has already decided is allowed to make one. A migration needs
 * neither: it runs outside Hibernate, so the statement inspector never sees it.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface PlatformWrite {}
