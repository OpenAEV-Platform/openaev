package io.openaev.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that an API controller endpoint (or a whole controller) deliberately needs no tenant
 * scope: it is public/anonymous or otherwise not tenant-data. It is one of the two escape hatches
 * the default-secure compile-time rule ({@code EndpointTxScopeRule}, #7726) accepts in place of a
 * {@code TxCtx} parameter, so a new endpoint cannot ship without an explicit choice. Use {@link
 * PlatformScoped} instead when the endpoint operates on platform / dual-scope data through a
 * different mechanism. Retention is SOURCE: the only consumer is the compile-time processor.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.SOURCE)
public @interface NoTenantScope {}
