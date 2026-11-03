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

  /**
   * Counts the credentials attached to at least one inject.
   *
   * @return the number of distinct credentials referenced by an inject
   */
  @Query(
      "select count(distinct sr.id) from Inject i join i.secretReferences sr"
          + " where type(sr) = CredentialSecretReference")
  long globalCountCredentialsAttachedToInjects();

  /**
   * Counts the distinct authors of the injects referencing at least one credential. An inject keeps
   * its author, not the user who attached the credential, so this is the closest available proxy.
   *
   * @return the number of distinct users owning an inject with a credential
   */
  @Query(
      "select count(distinct i.user.id) from Inject i join i.secretReferences sr"
          + " where type(sr) = CredentialSecretReference")
  long globalCountUsersWithInjectsUsingCredentials();

  /**
   * Counts the inject-credential links grouped by credential type and injector type. An inject
   * whose injector is no longer installed is grouped under a {@code null} injector type.
   *
   * @return rows of {@code [credentialType, injectorType, count]}
   */
  @Query(
      "select sr.credentialType, inj.type, count(sr)"
          + " from Inject i join treat(i.secretReferences as CredentialSecretReference) sr"
          + " left join i.injector inj"
          + " group by sr.credentialType, inj.type")
  List<Object[]> globalCountUsagesByCredentialTypeAndInjectorType();
}
