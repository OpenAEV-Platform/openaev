package io.openaev.database.repository;

import io.openaev.database.model.TenantSetting;
import jakarta.validation.constraints.NotNull;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface TenantSettingRepository
    extends JpaRepository<TenantSetting, String>, JpaSpecificationExecutor<TenantSetting> {

  Optional<TenantSetting> findByKey(@NotNull String key);
}
