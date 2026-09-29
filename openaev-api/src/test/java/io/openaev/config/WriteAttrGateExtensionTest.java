package io.openaev.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openaev.config.WriteAttrDetectorRecorder.Violation;
import io.openaev.config.WriteAttrSignature.Relation;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit test of the write-attribution gate's decision logic (no Spring): a wrong-tenant write from a
 * NEW production entry frame must fail the gate, while a baselined signature and every test-driven
 * write are waived. A gate that never fails is worthless, so this proves it can fail.
 */
class WriteAttrGateExtensionTest {

  private static final String CONTROLLER = "io.openaev.rest.scenario.ScenarioApi.createScenario";

  private static Violation prod(String table, Relation relation, String entryFrame) {
    return new Violation(
        table, "2cffad3a-0001-4078-b0e2-ef74274022c3", "tenant-b", relation, entryFrame, "flush");
  }

  /** A write made under a deny-all scope ({@code TxCtx.missing()}): {@code scope} is empty. */
  private static Violation prodEmptyScope(String table, Relation relation, String entryFrame) {
    return new Violation(
        table, "2cffad3a-0001-4078-b0e2-ef74274022c3", "", relation, entryFrame, "flush");
  }

  @Test
  @DisplayName("a write from a new production entry frame fails the gate")
  void newProductionSiteIsOffending() {
    List<String> offending =
        WriteAttrGateExtension.offendingSignatures(
            List.of(prod("scenarios", Relation.DEFAULT, CONTROLLER + ":105")), Set.of());
    assertEquals(List.of("scenarios DEFAULT " + CONTROLLER), offending);
  }

  @Test
  @DisplayName("a baselined signature is waived")
  void baselinedSiteIsWaived() {
    List<String> offending =
        WriteAttrGateExtension.offendingSignatures(
            List.of(prod("scenarios", Relation.DEFAULT, CONTROLLER + ":105")),
            Set.of("scenarios DEFAULT " + CONTROLLER));
    assertTrue(offending.isEmpty(), "a baselined signature must be waived, got " + offending);
  }

  @Test
  @DisplayName("a write with no production entry frame is test-driven and auto-waived")
  void testDrivenWriteIsWaived() {
    List<String> offending =
        WriteAttrGateExtension.offendingSignatures(
            List.of(prod("scenarios", Relation.DEFAULT, null)), Set.of());
    assertTrue(offending.isEmpty(), "a test-driven write must be waived, got " + offending);
  }

  @Test
  @DisplayName("the same entry frame at two flush lines is one signature")
  void duplicatesCollapsedAcrossLineNumbers() {
    List<String> offending =
        WriteAttrGateExtension.offendingSignatures(
            List.of(
                prod("scenarios", Relation.DEFAULT, CONTROLLER + ":105"),
                prod("scenarios", Relation.DEFAULT, CONTROLLER + ":140")),
            Set.of());
    assertEquals(1, offending.size(), "line number must not be part of the key, got " + offending);
  }

  @Test
  @DisplayName("the relation is part of the key: DEFAULT and NULL on one table are distinct")
  void relationIsPartOfTheKey() {
    List<String> offending =
        WriteAttrGateExtension.offendingSignatures(
            List.of(
                prod("scenarios", Relation.DEFAULT, CONTROLLER + ":105"),
                prod("scenarios", Relation.NULL, CONTROLLER + ":105")),
            Set.of());
    assertEquals(2, offending.size(), "relation must be part of the key, got " + offending);
  }

  @Test
  @DisplayName("an unattributed flush-time write is offending unless its key is waived")
  void unattributedWriteIsOffendingUnlessWaived() {
    String key = "unattributed(io.openaev.rest.scenario.ScenarioApiTest.given_x_should_y)";
    List<String> offending =
        WriteAttrGateExtension.offendingSignatures(
            List.of(prod("scenarios", Relation.DEFAULT, key)), Set.of());
    assertEquals(List.of("scenarios DEFAULT " + key), offending);
    assertTrue(
        WriteAttrGateExtension.offendingSignatures(
                List.of(prod("scenarios", Relation.DEFAULT, key)),
                Set.of("scenarios DEFAULT " + key))
            .isEmpty(),
        "a waived unattributed key must be accepted");
  }

  @Test
  @DisplayName(
      "an empty-scope violation never enters the gate's comparison, even mixed with a real one")
  void given_emptyScopeViolation_should_neverOffendEvenMixedWithARealOne() {
    List<Violation> mixed =
        List.of(
            prod("scenarios", Relation.DEFAULT, CONTROLLER + ":105"),
            prodEmptyScope("scenarios", Relation.OTHER, CONTROLLER + ":140"));
    List<String> offending = WriteAttrGateExtension.offendingSignatures(mixed, Set.of());
    assertEquals(
        List.of("scenarios DEFAULT " + CONTROLLER),
        offending,
        "an empty-scope violation must never appear in the gate's offending signatures");
  }

  @Test
  @DisplayName("an empty-scope violation is never checked against the baseline either")
  void given_emptyScopeViolation_should_neverConsumeABaselineLine() {
    List<Violation> violations = List.of(prodEmptyScope("scenarios", Relation.OTHER, CONTROLLER));
    // Even a baseline that happens to list the same table/relation/frame combination must not make
    // an empty-scope violation "waived": it was never a candidate to begin with.
    assertTrue(
        WriteAttrGateExtension.offendingSignatures(
                violations, Set.of("scenarios OTHER " + CONTROLLER))
            .isEmpty());
    assertTrue(WriteAttrGateExtension.offendingSignatures(violations, Set.of()).isEmpty());
  }

  @Test
  @DisplayName("an empty-scope violation is reported through its own channel")
  void given_emptyScopeViolation_should_beReportedSeparately() {
    Violation violation = prodEmptyScope("scenarios", Relation.OTHER, CONTROLLER + ":140");
    assertEquals(
        List.of("scenarios OTHER " + CONTROLLER),
        WriteAttrGateExtension.emptyScopeSignatures(List.of(violation)));
  }

  @Test
  @DisplayName("a test-driven empty-scope write, with no production frame, is ignored entirely")
  void given_emptyScopeWithNoProductionFrame_should_beIgnoredEntirely() {
    Violation violation = prodEmptyScope("scenarios", Relation.OTHER, null);
    assertTrue(WriteAttrGateExtension.emptyScopeSignatures(List.of(violation)).isEmpty());
    assertTrue(WriteAttrGateExtension.offendingSignatures(List.of(violation), Set.of()).isEmpty());
  }

  @Test
  @DisplayName(
      "the empty-scope report content names the distinct-signature count and lists them sorted")
  void given_signatures_should_formatTheEmptyScopeReport() {
    String content =
        WriteAttrGateExtension.emptyScopeReportContent(
            Set.of("scenarios OTHER " + CONTROLLER, "assets DEFAULT " + CONTROLLER));
    assertEquals(
        "# "
            + WriteAttrGateExtension.EMPTY_SCOPE_HEADER
            + ": 2 distinct signatures\n"
            + "assets DEFAULT "
            + CONTROLLER
            + "\n"
            + "scenarios OTHER "
            + CONTROLLER
            + "\n",
        content);
  }

  @Test
  @DisplayName("an empty set of signatures still produces a valid, zero-count report")
  void given_noSignatures_should_formatAZeroCountReport() {
    assertEquals(
        "# " + WriteAttrGateExtension.EMPTY_SCOPE_HEADER + ": 0 distinct signatures\n",
        WriteAttrGateExtension.emptyScopeReportContent(Set.of()));
  }
}
