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
}
