package io.openaev.database.repository;

import io.openaev.database.model.FindingComment;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface FindingCommentRepository
    extends JpaRepository<FindingComment, String>, JpaSpecificationExecutor<FindingComment> {

  List<FindingComment> findByFinding_IdOrderByCreationDateDesc(String findingId);

  Optional<FindingComment> findByIdAndTenantId(@NotNull String id, @NotNull String tenantId);
}
