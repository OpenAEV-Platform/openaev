package io.openaev.service.marking_definition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openaev.database.model.MarkingDefinitionStixIds;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The ids below are the ones OpenCTI gives these markings: the static TLP 2.0 ids of
 * opencti-graphql src/schema/identifier.js, and the UUIDv5 pycti's MarkingDefinition.generate_id
 * computes for the others (uuid5(OASIS namespace, canonical JSON of definition and type)).
 */
@DisplayName("Marking definition STIX ids, as OpenCTI derives them")
class MarkingDefinitionStixIdsTest {

  @Test
  @DisplayName("Given a TLP 2.0 marking should answer OpenCTI's static id, case and spaces ignored")
  void given_tlpMarking_should_answerStaticId() {
    assertEquals(
        "marking-definition--613f2e26-407d-48c7-9eca-b8e91df99dc9",
        MarkingDefinitionStixIds.generate("TLP", "TLP:CLEAR"));
    assertEquals(
        "marking-definition--613f2e26-407d-48c7-9eca-b8e91df99dc9",
        MarkingDefinitionStixIds.generate("tlp", " tlp:white "));
    assertEquals(
        "marking-definition--34098fce-860f-48ae-8e50-ebd3cc5e41da",
        MarkingDefinitionStixIds.generate("TLP", "TLP:GREEN"));
    assertEquals(
        "marking-definition--f88d31f6-486f-44da-b317-01333bde0b82",
        MarkingDefinitionStixIds.generate("TLP", "TLP:AMBER"));
    assertEquals(
        "marking-definition--826578e1-40ad-459f-bc73-ede076f81f37",
        MarkingDefinitionStixIds.generate("TLP", "TLP:AMBER+STRICT"));
    assertEquals(
        "marking-definition--5e57c739-391a-4eb3-b6be-7d15ca92d5ed",
        MarkingDefinitionStixIds.generate("TLP", "TLP:RED"));
  }

  @Test
  @DisplayName("Given another marking should answer pycti's UUIDv5 in the OASIS namespace")
  void given_otherMarking_should_answerOasisUuidV5() {
    assertEquals(
        "marking-definition--4cdff7eb-acb8-543f-8573-829eb9fe8b34",
        MarkingDefinitionStixIds.generate("PAP", "PAP:CLEAR"));
    assertEquals(
        "marking-definition--89484dde-e3d2-547f-a6c6-d14824429eb1",
        MarkingDefinitionStixIds.generate("PAP", "PAP:GREEN"));
    assertEquals(
        "marking-definition--a6f20d4d-0360-59b6-ba22-3b48707828b1",
        MarkingDefinitionStixIds.generate("PAP", "PAP:AMBER"));
    assertEquals(
        "marking-definition--4e4e3b84-de45-53df-9d9b-b21207699fd8",
        MarkingDefinitionStixIds.generate("pap", "pap:red"));
    assertEquals(
        "marking-definition--04ca03cc-c9d6-59ed-b8c0-ab6a0fd261da",
        MarkingDefinitionStixIds.generate("STATEMENT", "CONFIDENTIAL"));
  }

  @Test
  @DisplayName("Given a TLP definition under another type should not answer the static id")
  void given_tlpDefinitionUnderAnotherType_should_notAnswerStaticId() {
    assertNull(MarkingDefinitionStixIds.staticTlpId("PAP", "TLP:RED"));
    assertFalse(
        MarkingDefinitionStixIds.generate("PAP", "TLP:RED")
            .equals(MarkingDefinitionStixIds.generate("TLP", "TLP:RED")));
  }

  @Test
  @DisplayName("Given a value should tell a well-formed marking-definition id")
  void given_value_should_tellWellFormedId() {
    assertTrue(
        MarkingDefinitionStixIds.isValid(
            "marking-definition--5e57c739-391a-4eb3-b6be-7d15ca92d5ed"));
    assertFalse(MarkingDefinitionStixIds.isValid(null));
    assertFalse(MarkingDefinitionStixIds.isValid("5e57c739-391a-4eb3-b6be-7d15ca92d5ed"));
    assertFalse(
        MarkingDefinitionStixIds.isValid("indicator--5e57c739-391a-4eb3-b6be-7d15ca92d5ed"));
  }
}
