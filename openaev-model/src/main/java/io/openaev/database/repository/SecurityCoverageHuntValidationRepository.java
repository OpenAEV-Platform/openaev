package io.openaev.database.repository;

import io.openaev.database.model.SecurityCoverageHuntValidation;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SecurityCoverageHuntValidationRepository
    extends JpaRepository<SecurityCoverageHuntValidation, String> {

  List<SecurityCoverageHuntValidation> findAllByInjectIdIn(@NotNull Collection<String> injectIds);

  List<SecurityCoverageHuntValidation>
      findByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
          @NotNull SecurityCoverageHuntValidation.Status status,
          @NotNull Instant now,
          @NotNull Pageable pageable);
}
