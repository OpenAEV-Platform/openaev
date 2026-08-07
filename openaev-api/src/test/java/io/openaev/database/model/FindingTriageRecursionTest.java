package io.openaev.database.model;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Regression test for the {@link Finding} <-> {@link FindingTriage} bidirectional 1:1 relation:
 * both entities use Lombok {@code @Data}, which by default generates {@code toString()}/{@code
 * equals()}/{@code hashCode()} over every field, including the back-reference on each side. Left
 * unexcluded, calling any of these three methods on a {@code Finding} that has an associated
 * {@code FindingTriage} (or vice versa) recurses infinitely and throws a {@link
 * StackOverflowError}. {@code Finding#triage} now carries {@code @EqualsAndHashCode.Exclude} /
 * {@code @ToString.Exclude} to break the cycle - see the field-level comment in {@link Finding}.
 */
class FindingTriageRecursionTest {

  private Finding buildFindingWithTriage() {
    Finding finding = new Finding();
    finding.setId("finding-1");
    finding.setType(ContractOutputType.Text);
    finding.setValue("some-value");

    FindingTriage triage = new FindingTriage();
    triage.setId("triage-1");
    triage.setFinding(finding);
    triage.setStatus(FindingTriageStatus.CONFIRMED);

    finding.setTriage(triage);
    return finding;
  }

  @Test
  @DisplayName(
      "Finding.toString() does not throw StackOverflowError when an associated FindingTriage is set")
  void given_findingWithTriage_toString_shouldNotThrowStackOverflow() {
    Finding finding = buildFindingWithTriage();

    assertDoesNotThrow(finding::toString);
  }

  @Test
  @DisplayName(
      "FindingTriage.toString() does not throw StackOverflowError when its back-reference Finding is set")
  void given_triageWithFinding_toString_shouldNotThrowStackOverflow() {
    Finding finding = buildFindingWithTriage();

    assertDoesNotThrow(finding.getTriage()::toString);
  }

  @Test
  @DisplayName("Finding.equals()/hashCode() do not throw StackOverflowError with a triage set")
  void given_findingWithTriage_equalsAndHashCode_shouldNotThrowStackOverflow() {
    Finding finding = buildFindingWithTriage();
    Finding other = buildFindingWithTriage();

    assertDoesNotThrow(() -> finding.equals(other));
    assertDoesNotThrow(finding::hashCode);
  }

  @Test
  @DisplayName(
      "FindingTriage.equals()/hashCode() do not throw StackOverflowError with a back-reference Finding set")
  void given_triageWithFinding_equalsAndHashCode_shouldNotThrowStackOverflow() {
    Finding finding = buildFindingWithTriage();
    FindingTriage triage = finding.getTriage();
    FindingTriage otherTriage = buildFindingWithTriage().getTriage();

    assertDoesNotThrow(() -> triage.equals(otherTriage));
    assertDoesNotThrow(triage::hashCode);
  }
}
