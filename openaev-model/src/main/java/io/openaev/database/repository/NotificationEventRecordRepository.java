package io.openaev.database.repository;

import io.openaev.database.model.NotificationEventRecord;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationEventRecordRepository
    extends CrudRepository<NotificationEventRecord, String>,
        JpaSpecificationExecutor<NotificationEventRecord> {

  @Query(
      "select e from NotificationEventRecord e "
          + "where e.trigger.id in :triggerIds and e.createdAt >= :from and e.createdAt < :to "
          + "order by e.createdAt asc")
  List<NotificationEventRecord> findAllByTriggerIdsAndWindow(
      @NotNull Collection<String> triggerIds, @NotNull Instant from, @NotNull Instant to);

  @Modifying
  @Query("delete from NotificationEventRecord e where e.createdAt < :threshold")
  int deleteAllByCreatedAtBefore(@NotNull Instant threshold);
}
