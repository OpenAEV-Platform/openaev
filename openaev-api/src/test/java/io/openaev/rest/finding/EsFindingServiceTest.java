package io.openaev.rest.finding;

import static io.openaev.utils.fixtures.FindingFixture.*;
import static io.openaev.utils.fixtures.InjectFixture.getDefaultInject;
import static org.junit.jupiter.api.Assertions.*;

import io.openaev.IntegrationTest;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Finding;
import io.openaev.database.model.Tenant;
import io.openaev.utils.fixtures.composers.FindingComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
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
    Finding finding = wrapper.get();
    Finding result =
        findingService.finding(TxCtx.forTenant(finding.getTenant().getId()), finding.getId());

    // -- ASSERT --
    assertNotNull(result);
    assertEquals(TEXT_FIELD, result.getField());
  }

  @Test
  void given_invalid_finding_id_should_throw_exception() {
    // -- EXECUTE & ASSERT --
    assertThrows(
        EntityNotFoundException.class,
        () -> findingService.finding(TxCtx.forTenant(Tenant.DEFAULT_TENANT_UUID), "id"));
  }
}
