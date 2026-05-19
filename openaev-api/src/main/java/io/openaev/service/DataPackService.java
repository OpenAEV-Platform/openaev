package io.openaev.service;

import io.openaev.database.model.DataPack;
import io.openaev.database.model.DatapackTenantId;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.DataPackRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DataPackService {
  private final DataPackRepository dataPackRepository;

  public Optional<DataPack> findByIdAndTenant(String id, Tenant tenant) {
    return dataPackRepository.findByCompositeId(new DatapackTenantId(id, tenant));
  }

  public DataPack registerDataPack(String id, Tenant tenant) {
    DataPack dp = new DataPack();
    dp.setId(id);
    dp.setTenant(tenant);
    return dataPackRepository.save(dp);
  }
}
