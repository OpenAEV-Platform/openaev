package io.openaev.utils.fixtures.composers;

import io.openaev.database.model.FindingOccurrence;
import io.openaev.database.repository.FindingOccurrenceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class FindingOccurrenceComposer extends ComposerBase<FindingOccurrence> {
  @Autowired private FindingOccurrenceRepository findingOccurrenceRepository;

  public class Composer extends InnerComposerBase<FindingOccurrence> {
    private final FindingOccurrence occurrence;

    public Composer(FindingOccurrence occurrence) {
      this.occurrence = occurrence;
      // Kept in sync so readers in the same session see it, as the write paths do
      occurrence.getFinding().getOccurrences().add(occurrence);
    }

    @Override
    public Composer persist() {
      findingOccurrenceRepository.save(occurrence);
      return this;
    }

    @Override
    public Composer delete() {
      findingOccurrenceRepository.delete(occurrence);
      return this;
    }

    @Override
    public FindingOccurrence get() {
      return this.occurrence;
    }
  }

  public Composer forOccurrence(FindingOccurrence occurrence) {
    generatedItems.add(occurrence);
    return new Composer(occurrence);
  }
}
