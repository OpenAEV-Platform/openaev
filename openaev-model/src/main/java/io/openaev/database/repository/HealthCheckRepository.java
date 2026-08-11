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

  /**
   * Total disk space used by the current PostgreSQL database, in bytes.
   *
   * <p>Platform-wide metric exposed by the health check endpoint: it is intentionally not
   * tenant-scoped (there is no per-tenant physical storage in PostgreSQL, data of all tenants share
   * the same database).
   *
   * @return the database size in bytes
   */
  @Query(value = "select pg_database_size(current_database())", nativeQuery = true)
  Long databaseUsedSize();
}
