package io.openaev.service.tenants;

import static io.openaev.utils.pagination.PaginationUtils.buildPaginationJPA;

import io.openaev.database.model.Tenant;
import io.openaev.database.repository.TenantRepository;
import io.openaev.multitenancy.DependenciesManager;
import io.openaev.multitenancy.DependenciesManagerException;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Log
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class TenantService {

  public static final int SOFT_DELETE_RETENTION_DAYS = 30;

  private final TenantRepository tenantRepository;
  private final List<DependenciesManager> dependencies;

  // -- CREATE --

  /** Creates a new tenant and initializes all required dependencies. */
  public Tenant create(Tenant tenant) throws DependenciesManagerException {
    Objects.requireNonNull(tenant, "tenant must not be null");
    Objects.requireNonNull(tenant.getName(), "tenant name must not be null");

    Tenant createdTenant = tenantRepository.save(tenant);
    for (DependenciesManager dependency : dependencies) {
      dependency.createDependencyForTenant(createdTenant);
    }
    return createdTenant;
  }

  // -- READ --

  /** Finds a tenant by ID. Returns the tenant regardless of soft-delete status. */
  @Transactional(readOnly = true)
  public Tenant findById(String tenantId) {
    return tenantRepository
        .findById(tenantId)
        .orElseThrow(() -> new EntityNotFoundException("Tenant not found: " + tenantId));
  }

  /** Searches tenants with pagination and filtering. */
  @Transactional(readOnly = true)
  public Page<Tenant> search(SearchPaginationInput searchPaginationInput) {
    return buildPaginationJPA(this.tenantRepository::findAll, searchPaginationInput, Tenant.class);
  }

  /** Returns all tenants accessible by a given user. */
  @Transactional(readOnly = true)
  public List<Tenant> findTenantsByUserId(@NotNull String userId) {
    return tenantRepository.findTenantsByUserId(userId);
  }

  // -- UPDATE --

  /** Updates an existing tenant's attributes. */
  public Tenant update(String tenantId, Tenant updated) {
    Tenant existing = findById(tenantId);
    existing.setUpdateAttributes(updated);
    return tenantRepository.save(existing);
  }

  /** Reactivates a soft-deleted tenant within the threshold grace period. */
  public Tenant reactivate(String tenantId) {
    Tenant tenant = findById(tenantId);

    if (tenant.getDeletedAt() == null) {
      throw new IllegalStateException("Tenant is already enabled: " + tenantId);
    }

    Instant cutoff = tenant.getDeletedAt().plus(SOFT_DELETE_RETENTION_DAYS, ChronoUnit.DAYS);
    if (Instant.now().isAfter(cutoff)) {
      throw new IllegalStateException(
          "Reactivation of "
              + SOFT_DELETE_RETENTION_DAYS
              + " days period expired: "
              + tenantId
              + ". Deleted at: "
              + tenant.getDeletedAt());
    }

    tenant.setDeletedAt(null);
    return tenantRepository.save(tenant);
  }

  // -- DELETE --

  /**
   * Soft-deletes a tenant by setting the deletedAt timestamp instead of removing the row. The admin
   * has a grace period to reactivate the tenant before permanent deletion.
   */
  public Tenant softDelete(String tenantId) {
    Tenant tenant = findById(tenantId);
    if (tenant.getDeletedAt() != null) {
      throw new IllegalStateException("Tenant is already deleted: " + tenantId);
    }
    tenant.setDeletedAt(Instant.now());
    return tenantRepository.save(tenant);
  }

  /**
   * Permanently deletes all tenants whose a grace period has expired. Dependencies are cleaned
   * individually per tenant, then all expired tenants are deleted in a single batch query.
   */
  public int purgeExpiredTenants() {
    Instant cutoffDate = Instant.now().minus(SOFT_DELETE_RETENTION_DAYS, ChronoUnit.DAYS);
    List<Tenant> expired = tenantRepository.findAllExpiredSoftDeleted(cutoffDate);
    if (expired.isEmpty()) {
      return 0;
    }

    List<String> purgedIds = new java.util.ArrayList<>();
    for (Tenant tenant : expired) {
      try {
        for (DependenciesManager dependency : dependencies) {
          dependency.deleteDependencyForTenant(tenant.getId());
        }
        purgedIds.add(tenant.getId());
      } catch (DependenciesManagerException e) {
        log.severe(
            "Failed to clean dependencies for tenant "
                + tenant.getId()
                + " ("
                + tenant.getName()
                + "): "
                + e.getMessage());
      }
    }

    if (!purgedIds.isEmpty()) {
      tenantRepository.deleteAllByIdsNative(purgedIds);
    }
    return purgedIds.size();
  }
}
