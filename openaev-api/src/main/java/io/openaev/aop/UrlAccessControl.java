package io.openaev.aop;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an endpoint as protected by URL access token authentication.
 *
 * <p>The associated {@link UrlAccessControlAspect} will:
 *
 * <ol>
 *   <li>Extract the {@code url_access_token} cookie from the current request.
 *   <li>Validate the token (expiry, revocation, optional exercise scope).
 *   <li>Inject the resolved {@code userId} into the method's {@code Optional<String> userId}
 *       parameter when present. Be carrefull, using userId parameter name is important !
 *   <li>Return {@code 401 Unauthorized} on any validation failure.
 * </ol>
 *
 * <p>The {@code exerciseId} and {@code userId} method parameters are resolved dynamically from the
 * annotated method's signature by parameter name. Both are optional — if absent the scope check is
 * skipped for the missing dimension.
 *
 * <p>This annotation is a no-op when the {@code URL_ACCESS_TOKEN} preview feature flag is disabled.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface UrlAccessControl {}
