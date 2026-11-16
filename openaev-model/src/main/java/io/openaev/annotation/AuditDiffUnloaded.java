package io.openaev.annotation;

import io.openaev.database.audit.AuditLogContext;
import java.lang.annotation.*;

/**
 * Marks a LAZY collection of an {@link AuditDiffTracked} entity that the load and update snapshots
 * must not load.
 *
 * <p>Those snapshots run on every load of the entity: serializing such a collection would load it
 * each time, which defeats the LAZY fetch. While the collection is not loaded, they write {@link
 * AuditLogContext#NOT_LOADED} instead, and the diff skips the field. The create and delete
 * snapshots still load it, so a deleted entity keeps its full content in the audit log.
 *
 * <p>Use it only for collections that change through their own endpoint (e.g. group members), not
 * for fields whose change must show in an update diff.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditDiffUnloaded {}
