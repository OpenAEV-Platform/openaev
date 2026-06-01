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
 *   <li>Inject the resolved {@code userId} into the method parameter referenced by {@link
 *       #userId()}.
 *   <li>Return {@code 401 Unauthorized} on any validation failure.
 * </ol>
 *
 * <p>Both {@link #exerciseId()} and {@link #userId()} accept SpEL expressions referencing method
 * parameters (e.g. {@code "#exerciseId"}). Leaving an attribute empty disables the corresponding
 * feature (scope check or userId injection).
 *
 * <p>This annotation is a no-op when the {@code URL_ACCESS_TOKEN} preview feature flag is disabled.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface UrlAccessControl {

  /**
   * SpEL expression resolving the exercise ID used to scope the token validation (e.g. {@code
   * "#exerciseId"}). Empty string means no exercise scope check.
   */
  String exerciseId() default "";

  /**
   * SpEL expression pointing to the {@code Optional<String>} method parameter that receives the
   * injected user ID (e.g. {@code "#userId"}). Must be a simple {@code #paramName} reference. Empty
   * string means no injection.
   */
  String userId() default "";
}
