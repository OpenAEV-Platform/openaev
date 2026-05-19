package io.openaev.database.audit;

import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.time.Instant;

/**
 * JPA entity listener that manages audit timestamps for {@link Auditable} entities.
 *
 * <p>Replaces Hibernate-specific {@code @CreationTimestamp}/{@code @UpdateTimestamp} with a pure
 * JPA approach to reduce coupling to Hibernate internals.
 */
public class AuditableListener {

  @PrePersist
  void prePersist(Object entity) {
    if (entity instanceof Auditable auditable) {
      Instant now = Instant.now();
      if (auditable.getCreatedAt() == null) {
        auditable.setCreatedAt(now);
      }
      if (auditable.getUpdatedAt() == null) {
        auditable.setUpdatedAt(now);
      }
    }
  }

  @PreUpdate
  void preUpdate(Object entity) {
    if (entity instanceof Auditable auditable) {
      auditable.setUpdatedAt(Instant.now());
    }
  }
}
