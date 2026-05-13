package io.openaev.utils;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Disables Row-Level Security for the annotated test method or class.
 *
 * <p>Use this to:
 *
 * <ul>
 *   <li>Insert test data in a non-default tenant without RLS blocking it
 *   <li>Run isolation tests WITHOUT RLS to detect missing application-level filters (if the test
 *       still returns 403/404 without RLS → the app code correctly scopes by tenant)
 * </ul>
 *
 * <p>Example:
 *
 * <pre>{@code
 * @Test
 * @WithoutRls // RLS disabled → only app-level filtering protects isolation
 * void given_exerciseInTenantX_should_notBeReadableFromTenantY() { ... }
 * }</pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(RlsToggleExtension.class)
public @interface WithoutRls {}
