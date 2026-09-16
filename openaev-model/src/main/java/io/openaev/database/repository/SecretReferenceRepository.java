package io.openaev.database.repository;

import io.openaev.database.model.CredentialSecretReference;
import io.openaev.database.model.CredentialSecretReference.CREDENTIAL_AUTH_METHOD;
import io.openaev.database.model.SecretReference;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SecretReferenceRepository extends JpaRepository<SecretReference, String> {

  /**
   * Lists the credential references whose status is stale enough to be re-checked.
   *
   * <p>Restricted to {@link CredentialSecretReference} and to the auth methods a validator actually
   * exists for: the table is a SINGLE_TABLE hierarchy, so an unfiltered query would burn the run's
   * budget on username/password and hash references that can never be checked remotely.
   *
   * <p>JPQL on purpose, not a native query: {@code secret_references} is a v2 tenant-scoped table,
   * and a native statement would have to match a FROM/JOIN shape {@code TenantStatementInspector}
   * already accepts. The caller must pass a Pageable with a deterministic sort (oldest first, id as
   * tie-breaker), otherwise successive runs can keep re-checking the same subset.
   *
   * @param methods the auth methods that have a validator
   * @param threshold references never verified, or verified before this instant, are due
   * @param pageable the run budget and its sort
   * @return the references due for validation
   */
  @Query(
      "SELECT r FROM CredentialSecretReference r "
          + "WHERE r.credentialAuthMethod IN :methods "
          + "AND (r.lastVerifiedAt IS NULL OR r.lastVerifiedAt < :threshold)")
  List<CredentialSecretReference> findDueForValidation(
      @Param("methods") Collection<CREDENTIAL_AUTH_METHOD> methods,
      @Param("threshold") Instant threshold,
      Pageable pageable);
}
