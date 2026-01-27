package io.openaev.datapack;

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
@Profile("!test")
public class DataPackProcessor {
  private final List<DataPack> packs;

  @PostConstruct
  public void process() {
    List<DataPack> sortedPacks =
        packs.stream().sorted(Comparator.comparing(DataPack::getPackId)).toList();
    log.info(
        "Processed {} additional datapacks.",
        sortedPacks.stream()
            .filter(pack -> DataPackProcessingResult.PROCESSED.equals(pack.process()))
            .count());
  }
}
