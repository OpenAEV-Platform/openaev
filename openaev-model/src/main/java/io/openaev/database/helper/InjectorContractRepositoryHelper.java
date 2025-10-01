package io.openaev.database.helper;

import io.openaev.database.model.InjectorContract;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class InjectorContractRepositoryHelper {

  @PersistenceContext private EntityManager entityManager;

  /**
   * Searches InjectorContract from database based on the attack pattern and a list of
   * platform-architecture pairs
   *
   * @param attackPatternExternalId the external ID of the attack pattern to search for
   * @param platformArchitecturePairs a list of platform-architecture pairs to filter the contracts
   *     (e.g., Linux:x86_64, macOS:arm64)
   * @param limit the maximum number of results to return
   * @return a list of InjectorContract objects that match the search criteria
   */
  public List<InjectorContract> searchInjectorContractsByAttackPatternAndEnvironment(
      String attackPatternExternalId, List<String> platformArchitecturePairs, Integer limit) {
    StringBuilder sql =
        new StringBuilder(
            "SELECT ic.* FROM injectors_contracts ic "
                + "JOIN payloads p ON ic.injector_contract_payload = p.payload_id "
                + "JOIN injectors_contracts_attack_patterns injectorAttack ON ic.injector_contract_id = injectorAttack.injector_contract_id "
                + "JOIN attack_patterns a ON  injectorAttack.attack_pattern_id = a.attack_pattern_id "
                + "WHERE a.attack_pattern_external_id LIKE :attackPatternExternalId");

    for (String pair : platformArchitecturePairs) {
      String[] parts = pair.split(":");
      String platform = parts[0];
      String architecture = parts.length > 1 ? parts[1] : "";

      sql.append(" AND '").append(platform).append("' = ANY(ic.injector_contract_platforms)");
      if (!architecture.isEmpty()) {
        sql.append(" AND (p.payload_execution_arch = '")
            .append(architecture)
            .append("' OR p.payload_execution_arch = 'ALL_ARCHITECTURES')");
      }
    }

    sql.append(" ORDER BY RANDOM() LIMIT :limit");

    Query query = this.entityManager.createNativeQuery(sql.toString(), InjectorContract.class);
    query.setParameter("attackPatternExternalId", attackPatternExternalId + "%");
    query.setParameter("limit", limit);

    List<InjectorContract> results = query.getResultList();
    return results;
  }
}
