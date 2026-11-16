package io.openaev.service.finding;

import io.openaev.database.model.Finding;
import io.openaev.database.model.FindingOccurrence;
import io.openaev.database.model.Inject;
import io.openaev.database.repository.FindingOccurrenceRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FindingOccurrenceService {

  private final FindingOccurrenceRepository findingOccurrenceRepository;

  // -- CREATE --

  /**
   * Records the occurrence of each finding on the inject that detected it, dated when the inject
   * was sent (now when it was not) and carrying the finding's field. A finding that already has an
   * occurrence on this inject is left as it is, so a repeated detection adds nothing.
   */
  @Transactional(rollbackFor = Exception.class)
  public void recordOccurrencesByInject(Inject inject, Collection<Finding> findings) {
    if (findings.isEmpty()) {
      return;
    }
    List<String> findingIds = findings.stream().map(Finding::getId).toList();
    Set<String> findingIdsWithOccurrence =
        new HashSet<>(
            findingOccurrenceRepository.findFindingIdsWithOccurrenceOnInject(
                inject.getId(), findingIds));
    Instant date = Optional.ofNullable(inject.getSentAt()).orElseGet(Instant::now);
    List<FindingOccurrence> occurrences = new ArrayList<>();
    for (Finding finding : findings) {
      if (!findingIdsWithOccurrence.contains(finding.getId())) {
        findingIdsWithOccurrence.add(finding.getId());
        FindingOccurrence occurrence = newOccurrence(finding, inject, date);
        // Kept in sync so readers in the same session see it without a reload
        finding.getOccurrences().add(occurrence);
        occurrences.add(occurrence);
      }
    }
    findingOccurrenceRepository.saveAll(occurrences);
  }

  // -- READ --

  /** Returns the occurrence of a finding for one inject, if it was recorded. */
  @Transactional(readOnly = true)
  public Optional<FindingOccurrence> occurrence(String findingId, String injectId) {
    return findingOccurrenceRepository.findByFindingIdAndInjectId(findingId, injectId);
  }

  // -- PRIVATE --

  private static FindingOccurrence newOccurrence(Finding finding, Inject inject, Instant date) {
    FindingOccurrence occurrence = new FindingOccurrence();
    occurrence.setFinding(finding);
    occurrence.setInject(inject);
    occurrence.setField(finding.getField());
    occurrence.setDate(date);
    return occurrence;
  }
}
