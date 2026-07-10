package io.openaev.datapack;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.TagRepository;
import io.openaev.database.repository.TenantRepository;
import io.openaev.datapack.local_fixtures.TestDataPack;
import io.openaev.processor.MigrationProcessor;
import io.openaev.service.DataPackService;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public class MigrationProcessorTest extends IntegrationTest {
  @Autowired private DataPackService dataPackService;
  @Autowired private TestDataPack testDataPack;
  @Autowired private TagRepository tagRepository;
  @Autowired private TenantRepository tenantRepository;

  @Test
  @DisplayName("Processor processes all known datapacks")
  public void processorProcessesAllKnownDatapacks() {
    MigrationProcessor processor =
        new MigrationProcessor(List.of(testDataPack), Collections.emptyList(), tenantRepository);

    // act
    processor.process();

    // assert
    assertThat(
            dataPackService.findByIdAndTenant(
                TestDataPack.class.getCanonicalName(),
                new Tenant(TenantContext.getCurrentTenant())))
        .isPresent();
    assertThat(tagRepository.findByName(testDataPack.tagName)).isPresent();
  }

  @Test
  @DisplayName("Already processed datapacks don't process again")
  public void alreadyProcessedDatapackDontProcessAgain() {
    MigrationProcessor processor =
        new MigrationProcessor(List.of(testDataPack), Collections.emptyList(), tenantRepository);
    // fake registering the data pack
    dataPackService.registerDataPack(
        testDataPack.getPackId(), new Tenant(TenantContext.getCurrentTenant()));

    // act
    processor.process();

    // assert
    assertThat(
            dataPackService.findByIdAndTenant(
                TestDataPack.class.getCanonicalName(),
                new Tenant(TenantContext.getCurrentTenant())))
        .isPresent();
    // not that we prevented the pack from processing so we shouldn't find the contents in db
    assertThat(tagRepository.findByName(testDataPack.tagName)).isEmpty();
  }
}
