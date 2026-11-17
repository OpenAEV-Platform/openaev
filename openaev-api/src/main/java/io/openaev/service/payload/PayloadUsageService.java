package io.openaev.service.payload;

import io.openaev.database.model.Payload;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.database.repository.PayloadRepository;
import io.openaev.exception.PayloadInUseException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.ListUtils;
import org.springframework.stereotype.Service;

/** Where injects run payloads, before deleting or uninstalling them (PayloadDeleteGuard). */
@Service
@RequiredArgsConstructor
public class PayloadUsageService {

  private static final int BATCH_SIZE = 1000;

  private final InjectorContractRepository injectorContractRepository;
  private final PayloadRepository payloadRepository;

  /**
   * The contracts among these, held by {@code tenantId}, that can be deleted: their payload, if
   * any, runs no inject. A contract id repeats across tenants, so the tenant is named.
   */
  public List<String> notInUse(List<String> contractIds, String tenantId) {
    Set<String> inUse = new HashSet<>();
    ListUtils.partition(contractIds, BATCH_SIZE)
        .forEach(batch -> inUse.addAll(injectorContractRepository.findIdsInUse(batch, tenantId)));
    return contractIds.stream().filter(id -> !inUse.contains(id)).toList();
  }

  /** Refuses when injects run one of these payloads, naming where. */
  public void refuseIfInUse(List<Payload> payloads) {
    List<String> usages =
        payloads.stream()
            .flatMap(
                payload ->
                    payloadRepository
                        .findUsageNamesByPayloadId(payload.getId(), PayloadInUseException.READ)
                        .stream())
            .distinct()
            .toList();
    if (!usages.isEmpty()) {
      throw new PayloadInUseException(usages);
    }
  }
}
