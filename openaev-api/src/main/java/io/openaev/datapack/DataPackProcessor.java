package io.openaev.datapack;

import io.openaev.service.DataPackService;
import jakarta.annotation.PostConstruct;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class DataPackProcessor {
  private final List<DataPack> packs;
  private final DataPackService dataPackService;

  @PostConstruct
  @Profile("!test")
  public void process() {
    List<DataPack> sortedPacks =
        packs.stream().sorted(Comparator.comparing(DataPack::getPackId)).toList();
    log.info(
        "Process {} additional datapacks.",
        packs.stream()
            .filter(
                pack -> DataPackProcessingResult.PROCESSED.equals(pack.process(dataPackService)))
            .count());
  }
}
