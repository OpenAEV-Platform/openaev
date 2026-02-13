package io.openaev.service.tenants;

import static io.openaev.utils.pagination.PaginationUtils.buildPaginationJPA;

import io.openaev.database.model.Tenant;
import io.openaev.database.repository.TenantRepository;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.persistence.EntityNotFoundException;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class TenantService {

  private final TenantRepository tenantRepository;

  // -- CREATE --

  public Tenant create(Tenant tenant) {
    Objects.requireNonNull(tenant, "tenant must not be null");
    Objects.requireNonNull(tenant.getName(), "tenant name must not be null");

    return tenantRepository.save(tenant);
  }

  // -- READ --

  @Transactional(readOnly = true)
  public Tenant findById(String tenantId) {
    return tenantRepository
        .findById(tenantId)
        .orElseThrow(() -> new EntityNotFoundException("Tenant not found: " + tenantId));
  }

  @Transactional(readOnly = true)
  public Page<Tenant> search(SearchPaginationInput searchPaginationInput) {
    return buildPaginationJPA(this.tenantRepository::findAll, searchPaginationInput, Tenant.class);
  }

  // -- UPDATE --

  public Tenant update(String tenantId, Tenant updated) {
    Tenant existing = findById(tenantId);
    existing.setUpdateAttributes(updated);
    return tenantRepository.save(existing);
  }

  // -- DELETE --

  public void delete(String tenantId) {
    if (!tenantRepository.existsById(tenantId)) {
      throw new EntityNotFoundException("Tenant not found: " + tenantId);
    }
    tenantRepository.deleteByIdNative(tenantId);
  }
}
