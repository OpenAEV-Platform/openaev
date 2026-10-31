package io.openaev.database.repository;

import io.openaev.database.model.CredentialSecretReference;
import java.util.List;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CredentialSecretReferenceRepository
    extends CrudRepository<CredentialSecretReference, String>,
        JpaSpecificationExecutor<CredentialSecretReference> {

  @Query("select count(sr) from CredentialSecretReference sr")
  long globalCount();

  /**
   * Counts the credentials grouped by type, auth method and connectivity status.
   *
   * @return rows of {@code [credentialType, credentialAuthMethod, status, count]}
   */
  @Query(
      "select sr.credentialType, sr.credentialAuthMethod, sr.status, count(sr)"
          + " from CredentialSecretReference sr"
          + " group by sr.credentialType, sr.credentialAuthMethod, sr.status")
  List<Object[]> globalCountByTypeAuthMethodAndStatus();

  /**
   * Counts the injects referencing at least one credential.
   *
   * @return the number of distinct injects with a credential
   */
  @Query(
      "select count(distinct i.id) from Inject i join i.secretReferences sr"
          + " where type(sr) = CredentialSecretReference")
  long globalCountInjectsWithCredentials();
}
