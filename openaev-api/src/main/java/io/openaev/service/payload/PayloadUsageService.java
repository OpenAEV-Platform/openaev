package io.openaev.service.payload;

import io.openaev.database.repository.InjectorContractRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.ListUtils;
import org.springframework.stereotype.Service;

/** Picks, in a bulk delete, the contracts whose payload no inject runs (PayloadDeleteGuard). */
@Service
@RequiredArgsConstructor
public class PayloadUsageService {

  private static final int BATCH_SIZE = 1000;

  private final InjectorContractRepository injectorContractRepository;

  /** The contracts among these that can be deleted: their payload, if any, runs no inject. */
  public List<String> notInUse(List<String> contractIds) {
    Set<String> inUse = new HashSet<>();
    ListUtils.partition(contractIds, BATCH_SIZE)
        .forEach(batch -> inUse.addAll(injectorContractRepository.findIdsInUse(batch)));
    return contractIds.stream().filter(id -> !inUse.contains(id)).toList();
  }
}
