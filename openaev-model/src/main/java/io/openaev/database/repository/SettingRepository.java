package io.openaev.database.repository;

import io.openaev.database.model.Setting;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface SettingRepository
    extends CrudRepository<Setting, String>, JpaSpecificationExecutor<Setting> {

  @NotNull
  Optional<Setting> findById(@NotNull final String id);

  Optional<Setting> findByKey(@NotNull final String key);

  @Query(value = "SHOW server_version", nativeQuery = true)
  String getServerVersion();

  @Modifying
  @Query(value = "delete from parameters where parameter_id in :ids", nativeQuery = true)
  @Transactional
  void deleteByIdsNative(@Param("ids") List<String> ids);

  @Transactional
  void deleteByKeyIn(@NotNull final Collection<String> keys);
}
