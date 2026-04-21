package io.openaev.service.tenants;

import static io.openaev.utils.pagination.CriteriaBuilderPagination.paginate;
import static io.openaev.utils.pagination.PaginationUtils.buildPaginationCriteriaBuilder;

import io.openaev.api.tenants.TenantInput;
import io.openaev.api.tenants.TenantOutput;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.TenantRepository;
import io.openaev.multitenancy.DependenciesManager;
import io.openaev.multitenancy.DependenciesManagerException;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.PersistenceContext;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ClassUtils;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class TenantService {

  public static final int SOFT_DELETE_RETENTION_DAYS = 30;

  private final TenantRepository tenantRepository;
  private final List<DependenciesManager> dependencies;
  @PersistenceContext private EntityManager entityManager;

  // -- CREATE --

  /** Creates a new tenant and initializes all required dependencies (ordered by prerequisites). */
  public Tenant create(Tenant tenant) throws DependenciesManagerException {
    Objects.requireNonNull(tenant, "tenant must not be null");
    Objects.requireNonNull(tenant.getName(), "tenant name must not be null");

    Tenant createdTenant = tenantRepository.save(tenant);
    for (DependenciesManager dependency : sortByPrerequisites(dependencies)) {
      dependency.createDependencyForTenant(createdTenant);
    }
    return createdTenant;
  }

  /**
   * Sorts managers so that each one appears after its prerequisites. Simple insertion approach —
   * fine for the small number of DependenciesManager beans we have.
   */
  private List<DependenciesManager> sortByPrerequisites(List<DependenciesManager> managers) {
    List<DependenciesManager> sorted = new ArrayList<>();
    Set<Class<?>> resolved = new HashSet<>();

    List<DependenciesManager> remaining = new ArrayList<>(managers);
    while (!remaining.isEmpty()) {
      int before = remaining.size();
      Iterator<DependenciesManager> it = remaining.iterator();
      while (it.hasNext()) {
        DependenciesManager m = it.next();
        if (resolved.containsAll(m.getPrerequisite())) {
          sorted.add(m);
          resolved.add(ClassUtils.getUserClass(m));
          it.remove();
        }
      }
      if (remaining.size() == before) {
        log.warn(
            "Circular prerequisite detected among DependenciesManagers, appending remaining in original order: {}",
            remaining);
        sorted.addAll(remaining);
        break;
      }
    }
    return sorted;
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
  public Page<TenantOutput> search(@NotNull SearchPaginationInput searchPaginationInput) {
    return buildPaginationCriteriaBuilder(
        (spec, specCount, pageable) ->
            paginate(
                entityManager,
                Tenant.class,
                spec,
                specCount,
                pageable,
                TenantQueryHelper::select,
                TenantQueryHelper::execution),
        searchPaginationInput,
        Tenant.class);
  }

  /** Returns all tenants accessible by a given user. */
  @Transactional(readOnly = true)
  public List<Tenant> findTenantsByUserId(@NotNull String userId) {
    return tenantRepository.findTenantsByUserId(userId);
  }

  /** Counts the number of active (non-soft-deleted) tenants. */
  @Transactional(readOnly = true)
  public long countActiveTenants() {
    return tenantRepository.countByDeletedAtIsNull();
  }

  // -- UPDATE --

  /** Updates an existing tenant's attributes. */
  public Tenant update(String tenantId, TenantInput input) {
    Tenant existing = findById(tenantId);
    existing.setUpdateAttributes(input);
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
        log.error(
            "Failed to clean dependencies for tenant {} ({}): {}",
            tenant.getId(),
            tenant.getName(),
            e.getMessage());
      }
    }

    if (!purgedIds.isEmpty()) {
      tenantRepository.deleteAllByIdsNative(purgedIds);
    }
    return purgedIds.size();
  }
}
