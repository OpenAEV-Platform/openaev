package io.openaev.annotation;

import java.lang.annotation.*;

/**
 * Marks a LAZY collection of {@link io.openaev.database.model.Base} entities, in an {@link
 * AuditDiffTracked} entity, that the load snapshot must not load.
 *
 * <p>That snapshot runs on every load of the entity: serializing such a collection would load it
 * each time, which defeats the LAZY fetch. The load snapshot keeps the collection unread; the
 * update snapshot compares it only if the update loaded or replaced it, against the member ids it
 * had at load (its Hibernate stored snapshot). An untouched collection stays unloaded and out of
 * the diff. The create and delete snapshots still load it, so a deleted entity keeps its full
 * content in the audit log.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditDiffUnloaded {}
