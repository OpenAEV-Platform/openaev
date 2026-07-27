package io.openaev.processor.core;

import static org.junit.jupiter.api.Assertions.*;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Injector;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Payload;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.database.repository.InjectorRepository;
import io.openaev.database.repository.PayloadRepository;
import io.openaev.processor.MigrationProcessingResult;
import io.openaev.service.DataPackService;
import io.openaev.utils.fixtures.InjectorFixture;
import io.openaev.utils.fixtures.PayloadFixture;
import io.openaev.utils.fixtures.composers.PayloadComposer;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tests for the runtime migration that repairs payload contracts broken by the starter-pack import
 * regression (contracts persisted without their payload reference and without any injector link,
 * while the payload itself was created and left orphaned).
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Fix starter pack payload contracts migration tests")
@Transactional
public class V20260725_Fix_starter_pack_payload_contractsTest extends IntegrationTest {

  @Autowired private V20260725_Fix_starter_pack_payload_contracts migration;
  @Autowired private InjectorContractRepository injectorContractRepository;
  @Autowired private InjectorRepository injectorRepository;
  @Autowired private PayloadRepository payloadRepository;
  @Autowired private PayloadComposer payloadComposer;
  @Autowired private DataPackService dataPackService;

  private static final String BROKEN_PAYLOAD_NAME = "Broken starter pack payload - Salt Typhoon";

  private Payload persistOrphanPayload(String name) {
    Payload payload = PayloadFixture.createDefaultCommand();
    payload.setName(name);
    payloadComposer.forPayload(payload).persist();
    return payload;
  }

  /**
   * Reproduces the broken state left by the regressed starter-pack import: a non-custom contract
   * with no payload reference and no injector link, labeled with the payload name.
   */
  private InjectorContract persistBrokenContract(String labelEn) {
    InjectorContract contract = new InjectorContract();
    contract.setId(UUID.randomUUID().toString());
    contract.setTenant(new Tenant(TenantContext.getCurrentTenant()));
    contract.setContent("{}");
    contract.setLabels(Map.of("en", labelEn, "fr", labelEn));
    contract.setCustom(false);
    contract.setManual(false);
    contract.setNeedsExecutor(true);
    contract.setAtomicTesting(true);
    contract.setPlatforms(new Endpoint.PLATFORM_TYPE[] {Endpoint.PLATFORM_TYPE.Windows});
    return injectorContractRepository.save(contract);
  }

  @Test
  @DisplayName("Should reattach orphan payload and link the payload injector")
  void given_brokenContractAndOrphanPayload_should_reattachPayloadAndLinkInjector() {
    // -- PREPARE: broken platform with a registered payload injector --
    Injector payloadInjector =
        injectorRepository.save(InjectorFixture.createDefaultPayloadInjector());
    Payload orphanPayload = persistOrphanPayload(BROKEN_PAYLOAD_NAME);
    InjectorContract brokenContract = persistBrokenContract(BROKEN_PAYLOAD_NAME);

    // -- EXECUTE --
    Tenant tenant = new Tenant(TenantContext.getCurrentTenant());
    MigrationProcessingResult result = migration.process(tenant);

    // -- ASSERT --
    assertEquals(MigrationProcessingResult.PROCESSED, result);
    InjectorContract repaired =
        injectorContractRepository.findById(brokenContract.getId()).orElseThrow();
    assertNotNull(repaired.getPayload(), "The orphan payload must be reattached to the contract");
    assertEquals(orphanPayload.getId(), repaired.getPayload().getId());
    assertTrue(
        repaired.getInjectors().stream().anyMatch(i -> payloadInjector.getId().equals(i.getId())),
        "The repaired contract must be linked to the payload injector");
    assertNotEquals("{}", repaired.getContent(), "The contract content must be rebuilt");

    // The migration is idempotency-tracked: a second run is skipped
    assertTrue(
        dataPackService
            .findByIdAndTenant(
                V20260725_Fix_starter_pack_payload_contracts.class.getCanonicalName(), tenant)
            .isPresent());
    assertEquals(MigrationProcessingResult.SKIPPED, migration.process(tenant));
  }

  @Test
  @DisplayName("Should attach the payload even when no payload injector is registered yet")
  void given_brokenContractWithoutPayloadInjector_should_attachPayloadOnly() {
    // -- PREPARE: broken platform where the payload injector has not registered yet --
    Payload orphanPayload = persistOrphanPayload(BROKEN_PAYLOAD_NAME);
    InjectorContract brokenContract = persistBrokenContract(BROKEN_PAYLOAD_NAME);

    // -- EXECUTE --
    MigrationProcessingResult result =
        migration.process(new Tenant(TenantContext.getCurrentTenant()));

    // -- ASSERT: payload attached, adoption happens later at injector registration --
    assertEquals(MigrationProcessingResult.PROCESSED, result);
    InjectorContract repaired =
        injectorContractRepository.findById(brokenContract.getId()).orElseThrow();
    assertNotNull(repaired.getPayload());
    assertEquals(orphanPayload.getId(), repaired.getPayload().getId());
    assertTrue(repaired.getInjectors().isEmpty());
  }

  @Test
  @DisplayName("Should leave static injector-less contracts untouched")
  void given_staticContractWithoutOrphanPayload_should_leaveContractUntouched() {
    // -- PREPARE: a static contract awaiting its injector registration (e.g. nuclei), for which
    // no orphan payload exists --
    injectorRepository.save(InjectorFixture.createDefaultPayloadInjector());
    InjectorContract staticContract = persistBrokenContract("Nuclei - CVE Scan");

    // -- EXECUTE --
    MigrationProcessingResult result =
        migration.process(new Tenant(TenantContext.getCurrentTenant()));

    // -- ASSERT --
    assertEquals(MigrationProcessingResult.PROCESSED, result);
    InjectorContract untouched =
        injectorContractRepository.findById(staticContract.getId()).orElseThrow();
    assertNull(untouched.getPayload());
    assertTrue(untouched.getInjectors().isEmpty());
    assertEquals("{}", untouched.getContent());
  }
}
