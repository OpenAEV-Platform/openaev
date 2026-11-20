package io.openaev.service;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

import io.openaev.database.model.ExecutionStatus;
import io.openaev.database.model.ExecutionTraceStatus;
import io.openaev.database.model.InjectStatus;
import io.openaev.database.repository.ExecutionTraceRepository;
import io.openaev.service.stream.StreamClearanceService;
import io.openaev.utils.InjectStatusUtils;
import io.openaev.utils.InjectStatusUtils.CompleteTrace;
import jakarta.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * The status of injects as their reader sees it (see {@link InjectStatusUtils#visibleStatus}).
 *
 * <p>Only while markings filter the agents ({@link StreamClearanceService#isFiltering()}): with the
 * MARKING preview feature off, every reader sees the stored status, with no query and no recompute.
 */
@Service
@RequiredArgsConstructor
public class InjectVisibleStatusService {

  /** What the status row of an inject stores. */
  public record StoredStatus(ExecutionStatus name, @Nullable Integer expectedAgentCount) {}

  private final ExecutionTraceRepository executionTraceRepository;
  private final StreamClearanceService streamClearanceService;

  /**
   * The status of one inject, from the traces loaded in the reader's transaction, whose marking
   * scope hides the traces of the agents above the reader's clearance.
   */
  public ExecutionStatus visibleStatus(InjectStatus status) {
    if (!streamClearanceService.isFiltering()) {
      return status.getName();
    }
    return InjectStatusUtils.visibleStatus(status);
  }

  /**
   * Runs in the caller's transaction, whose marking scope hides the traces of the agents above the
   * reader's clearance: one query for the closed injects dispatched to agents, none when there is
   * no such inject or when markings filter nothing.
   *
   * @param storedByInjectId the stored status of each inject
   * @return the status each inject shows the reader, by inject id
   */
  public Map<String, ExecutionStatus> visibleStatuses(Map<String, StoredStatus> storedByInjectId) {
    Map<String, ExecutionStatus> visible = new HashMap<>();
    storedByInjectId.forEach((injectId, stored) -> visible.put(injectId, stored.name()));
    if (!streamClearanceService.isFiltering()) {
      return visible;
    }
    Set<String> dependOnTheReader =
        storedByInjectId.entrySet().stream()
            .filter(
                entry ->
                    entry.getValue().name().isClosed()
                        && entry.getValue().expectedAgentCount() != null
                        && entry.getValue().expectedAgentCount() > 0)
            .map(Map.Entry::getKey)
            .collect(Collectors.toSet());
    if (dependOnTheReader.isEmpty()) {
      return visible;
    }
    Map<String, List<CompleteTrace>> completeTracesByInjectId =
        executionTraceRepository
            .findCompleteStatusesAndAgentsByInjectIds(dependOnTheReader)
            .stream()
            .collect(
                groupingBy(
                    row -> (String) row[0],
                    mapping(
                        row -> new CompleteTrace((ExecutionTraceStatus) row[1], (String) row[2]),
                        toList())));
    dependOnTheReader.forEach(
        injectId -> {
          StoredStatus stored = storedByInjectId.get(injectId);
          visible.put(
              injectId,
              InjectStatusUtils.visibleStatus(
                  stored.name(),
                  stored.expectedAgentCount(),
                  completeTracesByInjectId.getOrDefault(injectId, List.of())));
        });
    return visible;
  }
}
