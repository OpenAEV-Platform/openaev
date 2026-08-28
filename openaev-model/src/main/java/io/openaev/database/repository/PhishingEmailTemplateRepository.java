package io.openaev.database.repository;

import io.openaev.database.model.PhishingEmailTemplate;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PhishingEmailTemplateRepository
    extends CrudRepository<PhishingEmailTemplate, String>,
        JpaSpecificationExecutor<PhishingEmailTemplate> {

  /**
   * Tenant-filtered lookup by id (JPQL so {@code tenantFilter} applies; see
   * ChannelRepository#findById).
   */
  @NotNull
  @Query("SELECT t FROM PhishingEmailTemplate t WHERE t.id = :id")
  Optional<PhishingEmailTemplate> findById(@NotNull @Param("id") String id);

  List<PhishingEmailTemplate> findByNameIgnoreCase(String name);
}
