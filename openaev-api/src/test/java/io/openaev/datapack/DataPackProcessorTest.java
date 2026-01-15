package io.openaev.datapack;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.database.repository.TagRepository;
import io.openaev.datapack.local_fixtures.TestDataPack;
import io.openaev.service.DataPackService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public class DataPackProcessorTest extends IntegrationTest {
  @Autowired private DataPackService dataPackService;
  @Autowired private TestDataPack testDataPack;
  @Autowired private TagRepository tagRepository;

  @Test
  @DisplayName("Processor processes all known datapacks")
  public void processorProcessesAllKnownDatapacks() {
    DataPackProcessor processor = new DataPackProcessor(List.of(testDataPack));

    // act
    processor.process();

    // assert
    assertThat(dataPackService.findById(TestDataPack.class.getCanonicalName())).isPresent();
    assertThat(tagRepository.findByName(testDataPack.tagName)).isPresent();
  }

  @Test
  @DisplayName("Already processed datapacks don't process again")
  public void alreadyProcessedDatapackDontProcessAgain() {
    DataPackProcessor processor = new DataPackProcessor(List.of(testDataPack));
    // fake registering the data pack
    dataPackService.registerDataPack(testDataPack.getPackId());

    // act
    processor.process();

    // assert
    assertThat(dataPackService.findById(TestDataPack.class.getCanonicalName())).isPresent();
    // not that we prevented the pack from processing so we shouldn't find the contents in db
    assertThat(tagRepository.findByName(testDataPack.tagName)).isEmpty();
  }
}
