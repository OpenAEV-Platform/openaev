package io.openaev.service.marking_definition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.api.marking_definition.form.MarkingDefinitionInput;
import io.openaev.context.TxCtx;
import io.openaev.database.model.MarkingDefinition;
import io.openaev.database.repository.MarkingDefinitionRepository;
import io.openaev.service.marking_definition.MarkingDefinitionStixService.ImportLine;
import io.openaev.service.marking_definition.MarkingDefinitionStixService.Outcome;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Marking definitions in OpenCTI's STIX format")
class MarkingDefinitionStixServiceTest {

  private static final String TENANT_ID = "tenant-1";
  private static final String USER_ID = "user-1";
  private static final String AMBER_ID = "marking-definition--f88d31f6-486f-44da-b317-01333bde0b82";
  private static final String CUSTOM_ID =
      "marking-definition--11111111-2222-4333-8444-555555555555";

  private final ObjectMapper mapper = new ObjectMapper();

  @Mock private MarkingDefinitionRepository repository;
  @Mock private MarkingDefinitionService markingDefinitionService;

  private MarkingDefinitionStixService service() {
    return new MarkingDefinitionStixService(repository, markingDefinitionService);
  }

  private static MarkingDefinition definition(
      String type, String name, String stixId, int order, String color) {
    MarkingDefinition definition = new MarkingDefinition();
    definition.setId("local-" + name);
    definition.setType(type);
    definition.setDefinition(name);
    definition.setStixId(stixId);
    definition.setOrder(order);
    definition.setColor(color);
    return definition;
  }

  @Test
  @DisplayName("Given an export should write each cleared marking under its stix id")
  void given_export_should_writeStixIds() {
    // Arrange
    TxCtx ctx = TxCtx.forTenant(TENANT_ID);
    when(markingDefinitionService.listAssignable(ctx))
        .thenReturn(List.of(definition("TLP", "TLP:AMBER", AMBER_ID, 3, "#FFB300")));

    // Act
    ObjectNode bundle = service().exportBundle(ctx);

    // Assert
    JsonNode marking = bundle.get("objects").get(0);
    assertEquals("bundle", bundle.get("type").asText());
    assertEquals("marking-definition", marking.get("type").asText());
    assertEquals(AMBER_ID, marking.get("id").asText());
    assertEquals("TLP:AMBER", marking.get("name").asText());
    assertEquals("TLP", marking.get("definition_type").asText());
    assertEquals(3, marking.get("x_opencti_order").asInt());
    assertEquals(
        "#FFB300",
        marking
            .get("extensions")
            .get(MarkingDefinitionStixService.OPENCTI_EXTENSION)
            .get("color")
            .asText());
  }

  @Test
  @DisplayName("Given an OpenCTI marking the tenant lacks should create it under the same id")
  void given_newMarking_should_createItUnderItsId() throws Exception {
    // Arrange
    JsonNode bundle =
        mapper.readTree(
            """
            {"type":"bundle","objects":[
              {"type":"identity","id":"identity--0d9b2d6a-4a5f-4a2e-9b0e-111111111111"},
              {"type":"marking-definition","id":"%s","definition_type":"PAP","name":"pap:custom",
               "extensions":{"%s":{"order":7,"color":"#123456"}}}]}
            """
                .formatted(CUSTOM_ID, MarkingDefinitionStixService.OPENCTI_EXTENSION));
    when(repository.findByStixIdAndTenantId(CUSTOM_ID, TENANT_ID)).thenReturn(Optional.empty());
    when(repository.findByTypeAndDefinitionAndTenantId("PAP", "PAP:CUSTOM", TENANT_ID))
        .thenReturn(Optional.empty());
    when(markingDefinitionService.create(
            any(MarkingDefinitionInput.class), eq(TENANT_ID), eq(USER_ID), eq(CUSTOM_ID)))
        .thenReturn(definition("PAP", "PAP:CUSTOM", CUSTOM_ID, 7, "#123456"));

    // Act
    List<ImportLine> report = service().importStix(bundle, TENANT_ID, USER_ID);

    // Assert
    ArgumentCaptor<MarkingDefinitionInput> input =
        ArgumentCaptor.forClass(MarkingDefinitionInput.class);
    verify(markingDefinitionService)
        .create(input.capture(), eq(TENANT_ID), eq(USER_ID), eq(CUSTOM_ID));
    assertEquals(new MarkingDefinitionInput("PAP", "PAP:CUSTOM", "#123456", 7), input.getValue());
    assertEquals(List.of(new ImportLine(CUSTOM_ID, "PAP", "PAP:CUSTOM", Outcome.CREATED)), report);
  }

  @Test
  @DisplayName(
      "Given an export imported back should report every marking existing, nothing written")
  void given_roundTrip_should_keepIdsAndWriteNothing() {
    // Arrange
    TxCtx ctx = TxCtx.forTenant(TENANT_ID);
    MarkingDefinition amber = definition("TLP", "TLP:AMBER", AMBER_ID, 3, "#FFB300");
    when(markingDefinitionService.listAssignable(ctx)).thenReturn(List.of(amber));
    when(repository.findByStixIdAndTenantId(AMBER_ID, TENANT_ID)).thenReturn(Optional.of(amber));

    // Act
    List<ImportLine> report = service().importStix(service().exportBundle(ctx), TENANT_ID, USER_ID);

    // Assert
    assertEquals(List.of(new ImportLine(AMBER_ID, "TLP", "TLP:AMBER", Outcome.EXISTING)), report);
    verify(markingDefinitionService, never()).create(any(), anyString(), anyString(), anyString());
  }

  @Test
  @DisplayName(
      "Given a local definition with the same name and another id should report a conflict")
  void given_sameNameOtherId_should_reportConflict() throws Exception {
    // Arrange
    JsonNode marking =
        mapper.readTree(
            """
            {"type":"marking-definition","id":"%s","definition_type":"TLP","name":"TLP:AMBER"}
            """
                .formatted(CUSTOM_ID));
    when(repository.findByStixIdAndTenantId(CUSTOM_ID, TENANT_ID)).thenReturn(Optional.empty());
    when(repository.findByTypeAndDefinitionAndTenantId("TLP", "TLP:AMBER", TENANT_ID))
        .thenReturn(Optional.of(definition("TLP", "TLP:AMBER", AMBER_ID, 3, "#FFB300")));

    // Act
    List<ImportLine> report = service().importStix(marking, TENANT_ID, USER_ID);

    // Assert
    assertEquals(List.of(new ImportLine(CUSTOM_ID, "TLP", "TLP:AMBER", Outcome.CONFLICT)), report);
    verify(markingDefinitionService, never()).create(any(), anyString(), anyString(), anyString());
  }

  @Test
  @DisplayName("Given a STIX 2.0 tlp definition object should read its name")
  void given_stix20Definition_should_readName() throws Exception {
    // Arrange
    JsonNode marking =
        mapper.readTree(
            """
            [{"type":"marking-definition","id":"%s","definition_type":"tlp",
              "definition":{"tlp":"amber"}}]
            """
                .formatted(AMBER_ID));
    when(repository.findByStixIdAndTenantId(AMBER_ID, TENANT_ID)).thenReturn(Optional.empty());
    when(repository.findByTypeAndDefinitionAndTenantId("TLP", "TLP:AMBER", TENANT_ID))
        .thenReturn(Optional.of(definition("TLP", "TLP:AMBER", null, 3, "#FFB300")));

    // Act
    List<ImportLine> report = service().importStix(marking, TENANT_ID, USER_ID);

    // Assert
    assertEquals(List.of(new ImportLine(AMBER_ID, "TLP", "TLP:AMBER", Outcome.CONFLICT)), report);
  }

  @Test
  @DisplayName("Given a malformed id should report it invalid and write nothing")
  void given_malformedId_should_reportInvalid() throws Exception {
    // Arrange
    JsonNode marking =
        mapper.readTree(
            """
            {"type":"marking-definition","id":"not-a-stix-id","definition_type":"PAP","name":"PAP:X"}
            """);

    // Act
    List<ImportLine> report = service().importStix(marking, TENANT_ID, USER_ID);

    // Assert
    assertEquals(Outcome.INVALID, report.get(0).outcome());
    verify(markingDefinitionService, never()).create(any(), anyString(), anyString(), anyString());
  }
}
