package io.openaev.database.repository;

import io.openaev.database.model.NotificationTrigger;
import io.openaev.database.model.NotificationTriggerType;
import io.openaev.database.model.ResourceType;
import jakarta.validation.constraints.NotNull;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationTriggerRepository
    extends CrudRepository<NotificationTrigger, String>,
        JpaSpecificationExecutor<NotificationTrigger> {

  Optional<NotificationTrigger> findByIdAndTenantId(@NotNull String id, @NotNull String tenantId);

  List<NotificationTrigger> findAllByIdInAndTenantId(
      @NotNull Collection<String> ids, @NotNull String tenantId);

  boolean existsByIdAndTenantId(@NotNull String id, @NotNull String tenantId);

  @Query(
      "select t from NotificationTrigger t "
          + "where t.type = io.openaev.database.model.NotificationTriggerType.LIVE "
          + "and t.enabled = true and t.watchedResourceType = :resourceType")
  List<NotificationTrigger> findEnabledLiveTriggersByResourceType(
      @NotNull ResourceType resourceType);

  List<NotificationTrigger> findAllByTypeAndEnabledTrue(@NotNull NotificationTriggerType type);

  List<NotificationTrigger> findAllByOwnerId(@NotNull String ownerId);

  @Query(
      "select distinct t.watchedResourceType from NotificationTrigger t "
          + "where t.type = io.openaev.database.model.NotificationTriggerType.LIVE "
          + "and t.enabled = true")
  List<ResourceType> findWatchedResourceTypes();
}
