package io.openaev.database.repository;

import io.openaev.database.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface HealthCheckRepository extends JpaRepository<User, String> {
  @Query("select 1")
  void healthCheck();

  @Query(value = "select pg_database_size(current_database())", nativeQuery = true)
  Long databaseUsedSize();
}
