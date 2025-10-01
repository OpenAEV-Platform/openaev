package io.openaev.database.repository;

import io.openaev.database.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/** Repository used to run database health check */
@Repository
public interface HealthCheckRepository extends JpaRepository<User, String> {
  @Query("select 1")
  void healthCheck();
}
