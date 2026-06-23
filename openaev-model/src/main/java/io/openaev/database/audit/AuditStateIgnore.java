package io.openaev.database.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field as non-significant for audit diff comparison.
 *
 * <p>Fields annotated with {@code @AuditDiffIgnore} are excluded from the map returned by {@link
 * AuditStateCapturable#significantState}. Typical candidates are timestamps, computed values, and
 * back-references that change on every update but carry no business significance.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditStateIgnore {}
