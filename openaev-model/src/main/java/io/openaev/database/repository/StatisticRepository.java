package io.openaev.database.repository;

import java.time.Instant;
import org.springframework.data.repository.query.Param;

@SuppressWarnings("EmptyMethod")
public interface StatisticRepository {

  long globalCount(@Param("creationDate") Instant creationDate);

  long userCount(@Param("userId") String userId, @Param("creationDate") Instant creationDate);
}
