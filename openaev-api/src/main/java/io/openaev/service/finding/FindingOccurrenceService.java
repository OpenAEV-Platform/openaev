package io.openaev.service.finding;

import static io.openaev.helper.StreamHelper.fromIterable;

import io.openaev.database.model.Finding;
import io.openaev.database.model.FindingOccurrence;
import io.openaev.database.model.Inject;
import io.openaev.database.repository.FindingOccurrenceRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FindingOccurrenceService {

  private final FindingOccurrenceRepository findingOccurrenceRepository;

  /** When the inject was sent, now when it was not. */
  public static Instant occurrenceDate(Inject inject) {
    return Optional.ofNullable(inject.getSentAt()).orElseGet(Instant::now);
  }

  // -- CREATE --

  /**
   * Records the occurrence of each finding on the inject. A finding that already has one on this
   * inject keeps it, with its field: a repeated detection adds nothing.
   */
  @Transactional(rollbackFor = Exception.class)
  public List<FindingOccurrence> recordOccurrences(
      Inject inject, Instant date, List<Finding> findings, Map<String, String> fieldByFindingId) {
    Set<String> findingIdsWithOccurrence =
        new HashSet<>(
            findingOccurrenceRepository.findFindingIdsWithOccurrenceOnInject(
                inject.getId(), findings.stream().map(Finding::getId).toList()));
    List<FindingOccurrence> occurrences = new ArrayList<>();
    for (Finding finding : findings) {
      if (!findingIdsWithOccurrence.contains(finding.getId())) {
        occurrences.add(
            newOccurrence(finding, inject, date, fieldByFindingId.get(finding.getId())));
      }
    }
    return fromIterable(findingOccurrenceRepository.saveAll(occurrences));
  }

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
    Instant date = occurrenceDate(inject);
    List<FindingOccurrence> occurrences = new ArrayList<>();
    for (Finding finding : findings) {
      if (!findingIdsWithOccurrence.contains(finding.getId())) {
        findingIdsWithOccurrence.add(finding.getId());
        occurrences.add(newOccurrence(finding, inject, date, finding.getField()));
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

  private static FindingOccurrence newOccurrence(
      Finding finding, Inject inject, Instant date, String field) {
    FindingOccurrence occurrence = new FindingOccurrence();
    occurrence.setFinding(finding);
    occurrence.setInject(inject);
    occurrence.setField(field);
    occurrence.setDate(date);
    // Kept in sync so readers in the same session see it without a reload
    finding.getOccurrences().add(occurrence);
    return occurrence;
  }
}
