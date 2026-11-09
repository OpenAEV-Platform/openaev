package io.openaev.utils.fixtures;

import io.openaev.database.model.Finding;
import io.openaev.database.model.FindingOccurrence;
import io.openaev.database.model.Inject;
import java.time.Instant;

public class FindingOccurrenceFixture {

  public static final String OTHER_FIELD = "other_field";

  public static FindingOccurrence createOccurrence(
      Finding finding, Inject inject, Instant date, String field) {
    FindingOccurrence occurrence = new FindingOccurrence();
    occurrence.setFinding(finding);
    occurrence.setInject(inject);
    occurrence.setDate(date);
    occurrence.setField(field);
    return occurrence;
  }
}
