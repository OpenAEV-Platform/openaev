package io.openaev.aop;

import io.openaev.rest.settings.PreviewFeature;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Puts every endpoint of a controller behind a preview feature: while the feature is off, each
 * route answers a 404 as if it did not exist, before any transaction, tenant scope or permission
 * check runs. Declared once on the controller class; enforced by {@link PreviewFeatureAspect}.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePreviewFeature {

  /** The preview feature the controller belongs to. */
  PreviewFeature value();
}
