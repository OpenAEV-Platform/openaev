package io.openaev.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openaev.database.model.KillChainPhase;
import io.openaev.database.repository.KillChainPhaseRepository;
import io.openaev.rest.kill_chain_phase.service.KillChainPhaseService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "openaev.tenant.active-tables=kill_chain_phases")
@DisplayName("kill chain phase reads correlate the phase tenant with its parent's")
class KillChainPhaseQueryCorrelationTest extends TenantIsolationIntegrationTest {

  private static final String OTHER_TENANT = "kcp-corr-other-tenant";
  private static final String OWN_PHASE = "kcp-corr-phase-own";
  private static final String OTHER_PHASE = "kcp-corr-phase-other";

  @Autowired private KillChainPhaseRepository killChainPhaseRepository;
  @Autowired private KillChainPhaseService killChainPhaseService;

  @Nested
  @DisplayName("simulation kill chain phases stay in the simulation's tenant")
  class SimulationPhases {

    private static final String EXERCISE = "kcp-corr-exercise";
    private static final String INJECT = "kcp-corr-inject";
    private static final String CONTRACT = "kcp-corr-contract";
    private static final String PATTERN = "kcp-corr-sim-pattern";

    private String simulationTenant;

    @BeforeEach
    void seedOneSimulationWhoseAttackPatternLinksTwoTenantsPhases() {
      seedTenant(OTHER_TENANT);
      insertExercise(EXERCISE);
      simulationTenant = readTenantOf("exercises", "exercise_id", EXERCISE);
      insertPhase(OWN_PHASE, simulationTenant);
      insertPhase(OTHER_PHASE, OTHER_TENANT);
      insertAttackPattern(PATTERN);
      insertContract(CONTRACT);
      insertContractPatternLink(CONTRACT, PATTERN);
      insertPatternPhaseLink(PATTERN, OWN_PHASE);
      insertPatternPhaseLink(PATTERN, OTHER_PHASE);
      insertInject(INJECT, EXERCISE, CONTRACT);
    }

    @Test
    @DisplayName("a scope holding both tenants returns the simulation's phase only")
    void multiTenantScopeStaysOnTheSimulationTenant() {
      setScope(simulationTenant + "," + OTHER_TENANT);
      assertEquals(
          List.of(OWN_PHASE),
          visiblePhaseIds(),
          "the other tenant's phase is in scope and linked to the same attack pattern; only the"
              + " correlation with the simulation's tenant keeps it out");
    }

    @Test
    @DisplayName("the correlation composes with the scope: an unscoped read stays fail-closed")
    void unscopedReadReturnsNothing() {
      setScope("");
      assertTrue(visiblePhaseIds().isEmpty());
    }

    private List<String> visiblePhaseIds() {
      return killChainPhaseRepository.findDistinctByExerciseId(EXERCISE).stream()
          .map(KillChainPhase::getId)
          .sorted()
          .toList();
    }
  }

  @Nested
  @DisplayName("attack pattern phase ids stay in the pattern's tenant")
  class AttackPatternPhaseIds {

    private static final String PATTERN = "kcp-corr-ap-pattern";

    private String patternTenant;

    @BeforeEach
    void seedOnePatternLinkedToTwoTenantsPhases() {
      seedTenant(OTHER_TENANT);
      insertAttackPattern(PATTERN);
      patternTenant = readTenantOf("attack_patterns", "attack_pattern_id", PATTERN);
      insertPhase(OWN_PHASE, patternTenant);
      insertPhase(OTHER_PHASE, OTHER_TENANT);
      insertPatternPhaseLink(PATTERN, OWN_PHASE);
      insertPatternPhaseLink(PATTERN, OTHER_PHASE);
    }

    @Test
    @DisplayName("a scope holding both tenants yields the pattern's own phase id only")
    void multiTenantScopeStaysOnThePatternTenant() {
      setScope(patternTenant + "," + OTHER_TENANT);
      assertEquals(
          List.of(OWN_PHASE),
          visiblePhaseIds(),
          "the other tenant's phase is in scope and linked to the same pattern; only the"
              + " correlation with the pattern's tenant keeps it out");
    }

    @Test
    @DisplayName("the correlation composes with the scope: an unscoped read stays fail-closed")
    void unscopedReadReturnsNothing() {
      setScope("");
      assertTrue(visiblePhaseIds().isEmpty());
    }

    private List<String> visiblePhaseIds() {
      return killChainPhaseService
          .phaseIdsByAttackPatternId(List.of(PATTERN))
          .getOrDefault(PATTERN, List.of())
          .stream()
          .sorted()
          .toList();
    }
  }

  private String readTenantOf(String table, String idColumn, String id) {
    return (String)
        entityManager
            .createNativeQuery("SELECT tenant_id FROM " + table + " WHERE " + idColumn + " = :id")
            .setParameter("id", id)
            .getSingleResult();
  }

  private void insertPhase(String id, String tenantId) {
    entityManager
        .createNativeQuery(
            "INSERT INTO kill_chain_phases"
                + " (phase_id, phase_name, phase_shortname, phase_kill_chain_name,"
                + "  phase_external_id, phase_order, tenant_id)"
                + " VALUES (:id, :id, :id, 'mitre-attack', :id, 1, :tenant)")
        .setParameter("id", id)
        .setParameter("tenant", tenantId)
        .executeUpdate();
  }

  private void insertAttackPattern(String id) {
    entityManager
        .createNativeQuery(
            "INSERT INTO attack_patterns"
                + " (attack_pattern_id, attack_pattern_name, attack_pattern_external_id)"
                + " VALUES (:id, :id, :id)")
        .setParameter("id", id)
        .executeUpdate();
  }

  private void insertPatternPhaseLink(String patternId, String phaseId) {
    entityManager
        .createNativeQuery(
            "INSERT INTO attack_patterns_kill_chain_phases (attack_pattern_id, phase_id)"
                + " VALUES (:pattern, :phase)")
        .setParameter("pattern", patternId)
        .setParameter("phase", phaseId)
        .executeUpdate();
  }

  private void insertContract(String id) {
    entityManager
        .createNativeQuery(
            "INSERT INTO injectors_contracts"
                + " (injector_contract_id, injector_contract_content) VALUES (:id, '{}')")
        .setParameter("id", id)
        .executeUpdate();
  }

  private void insertContractPatternLink(String contractId, String patternId) {
    entityManager
        .createNativeQuery(
            "INSERT INTO injectors_contracts_attack_patterns"
                + " (injector_contract_id, attack_pattern_id) VALUES (:contract, :pattern)")
        .setParameter("contract", contractId)
        .setParameter("pattern", patternId)
        .executeUpdate();
  }

  private void insertExercise(String id) {
    entityManager
        .createNativeQuery(
            "INSERT INTO exercises (exercise_id, exercise_name, exercise_mail_from)"
                + " VALUES (:id, :id, 'test@openaev.io')")
        .setParameter("id", id)
        .executeUpdate();
  }

  private void insertInject(String id, String exerciseId, String contractId) {
    entityManager
        .createNativeQuery(
            "INSERT INTO injects"
                + " (inject_id, inject_title, inject_all_teams, inject_enabled,"
                + "  inject_depends_duration, inject_exercise, inject_injector_contract)"
                + " VALUES (:id, :id, false, true, 0, :exercise, :contract)")
        .setParameter("id", id)
        .setParameter("exercise", exerciseId)
        .setParameter("contract", contractId)
        .executeUpdate();
  }
}
