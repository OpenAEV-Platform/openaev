package io.openaev.annotation;

import io.openaev.database.audit.AuditLogContext;
import java.lang.annotation.*;

/**
 * Marks a JPA entity for before/after diff tracking through the audit log pipeline.
 *
 * <p>When present on an entity class, {@link io.openaev.database.audit.ModelBaseListener} will:
 *
 * <ul>
 *   <li>Capture a JSON snapshot of the entity at {@code @PostLoad} time (the "before" state).
 *   <li>Compute a field-level diff at {@code @PreUpdate} time and record it in {@link
 *       AuditLogContext}.
 * </ul>
 *
 * <p>The diff context is consumed by the audit aspect ({@code AccessControlAuditLogAspect})
 * <em>before</em> the async logging boundary, so the data is always serialized on the servlet
 * thread and never crosses thread-local boundaries.
 *
 * <p>Collections are compared as sorted string representations to avoid order-dependent false
 * positives. Not inherited — subclasses must re-annotate explicitly.
 *
 * <p>Example:
 *
 * <pre>{@code
 * @Entity
 * @EntityListeners(ModelBaseListener.class)
 * @AuditDiffTracked
 * public class Role implements DualScopeBase { ... }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditDiffTracked {}
