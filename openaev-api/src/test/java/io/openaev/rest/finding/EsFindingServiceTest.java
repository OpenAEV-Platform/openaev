package io.openaev.rest.finding;

import static io.openaev.utils.fixtures.FindingFixture.*;
import static io.openaev.utils.fixtures.InjectFixture.getDefaultInject;
import static org.junit.jupiter.api.Assertions.*;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Finding;
import io.openaev.database.model.Inject;
import io.openaev.database.repository.InjectRepository;
import io.openaev.utils.fixtures.composers.FindingComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import org.apache.commons.validator.routines.InetAddressValidator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
@Transactional
class EsFindingServiceTest extends IntegrationTest {

  @Autowired private FindingComposer findingComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private FindingService findingService;
  @Autowired private InjectRepository injectRepository;

  FindingComposer.Composer createFindingComposer() {
    return this.findingComposer
        .forFinding(createDefaultTextFinding())
        .withInject(injectComposer.forInject(getDefaultInject()))
        .persist();
  }

  @Test
  void given_findings_should_return_all_findings() {
    // -- PREPARE --
    createFindingComposer();

    // -- EXECUTE --
    List<Finding> results = findingService.findings();

    // -- ASSERT --
    assertEquals(1, results.size());
    assertEquals(TEXT_FIELD, results.getFirst().getField());
  }

  @Test
  void given_finding_id_should_return_finding() {
    // -- PREPARE --
    FindingComposer.Composer wrapper = createFindingComposer();

    // -- EXECUTE --
    Finding result = findingService.finding(wrapper.get().getId());

    // -- ASSERT --
    assertNotNull(result);
    assertEquals(TEXT_FIELD, result.getField());
  }

  @Test
  void given_invalid_finding_id_should_throw_exception() {
    // -- EXECUTE & ASSERT --
    assertThrows(EntityNotFoundException.class, () -> findingService.finding("id"));
  }

  @Nested
  class CreateEsFinding {

    @Test
    void given_new_text_finding_should_create_finding() {
      // -- PREPARE --
      Finding finding = createDefaultTextFinding();
      Inject inject = injectRepository.save(getDefaultInject());

      // -- EXECUTE --
      Finding result = findingService.createFinding(finding, inject.getId());

      // -- ASSERT --
      assertNotNull(result);
      assertEquals(TEXT_FIELD, result.getField());
    }

    @Test
    void given_new_ipv6_finding_should_create_finding() {
      // -- PREPARE --
      Finding finding = createDefaultIPV6Finding();
      Inject inject = injectRepository.save(getDefaultInject());

      // -- EXECUTE --
      Finding result = findingService.createFinding(finding, inject.getId());

      // -- ASSERT --
      InetAddressValidator validator = InetAddressValidator.getInstance();
      assertNotNull(result);
      assertEquals(IPV6_FIELD, result.getField());
      assertTrue(validator.isValid(result.getValue()));
    }

    @Test
    void given_new_credentials_finding_should_create_finding() {
      // -- PREPARE --
      Finding finding = createDefaultFindingCredentials();
      Inject inject = injectRepository.save(getDefaultInject());

      // -- EXECUTE --
      Finding result = findingService.createFinding(finding, inject.getId());

      // -- ASSERT --
      assertNotNull(result);
      assertEquals(CREDENTIALS_FIELD, result.getField());
      assertTrue(result.getValue().contains(":"));
    }
  }

  @Test
  void given_existing_finding_should_update_finding() {
    // -- PREPARE --
    FindingComposer.Composer wrapper = createFindingComposer();
    Finding finding = wrapper.get();
    String newKey = "new_key";
    finding.setField(newKey);

    // -- EXECUTE --
    Finding result = findingService.updateFinding(finding, finding.getInject().getId());

    // -- ASSERT --
    assertNotNull(result);
    assertEquals(newKey, result.getField());
  }

  @Test
  void given_existing_finding_should_delete_finding() {
    // -- PREPARE --
    FindingComposer.Composer wrapper = createFindingComposer();

    // -- EXECUTE --
    String id = wrapper.get().getId();
    findingService.deleteFinding(id);

    // -- ASSERT --
    assertThrows(EntityNotFoundException.class, () -> findingService.finding(id));
  }

  @Test
  void given_invalid_finding_id_should_throw_exception_when_deleting() {
    // -- EXECUTE & ASSERT --
    assertThrows(EntityNotFoundException.class, () -> findingService.deleteFinding("id"));
  }
}
