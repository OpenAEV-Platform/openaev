package io.openaev.database.repository;

import io.openaev.database.model.UserEvent;
import io.openaev.database.model.UserEventType;
import java.time.Instant;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserEventRepository extends CrudRepository<UserEvent, String> {

  @Query(
      """
              select count(e)
              from UserEvent e
              where e.type = :type
                and e.createdAt >= :from
            """)
  long countEvents(@Param("type") UserEventType type, @Param("from") Instant from);

  @Modifying
  @Query(
      """
              delete from UserEvent e
              where e.type = :type
                and e.createdAt < :before
            """)
  int deleteOlderThan(@Param("type") UserEventType type, @Param("before") Instant before);
}
