package io.openaev.aop;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a Quartz {@link org.quartz.Job} class as cross-tenant. When a job class is annotated with
 * {@code @CrossTenantJob}, the Hibernate tenant filter will NOT be enabled for any
 * {@code @Transactional} methods executed within the job's thread.
 *
 * <p>This removes the need to manually call {@code
 * entityManager.unwrap(Session.class).disableFilter("tenantFilter")} in each method.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface CrossTenantJob {}
