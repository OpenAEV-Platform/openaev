package io.openaev.database.repository;

import io.openaev.database.model.Folder;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FolderRepository
    extends CrudRepository<Folder, String>, JpaSpecificationExecutor<Folder> {

  @NotNull
  Optional<Folder> findById(@NotNull String id);

  List<Folder> findByParentId(String parentId);

  List<Folder> findByParentIsNull();
}
