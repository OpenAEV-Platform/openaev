package io.openaev.database.repository;

import io.openaev.database.model.Notification;
import jakarta.validation.constraints.NotNull;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationRepository
    extends CrudRepository<Notification, String>, JpaSpecificationExecutor<Notification> {

  Optional<Notification> findByIdAndUserId(@NotNull String id, @NotNull String userId);

  long countByUserIdAndReadFalse(@NotNull String userId);

  @Modifying
  @Query("update Notification n set n.read = true where n.user.id = :userId and n.read = false")
  int markAllAsRead(@NotNull String userId);
}
