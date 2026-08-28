package io.openaev.service.chaining;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.api.chaining.dto.ConditionCreateInput;
import io.openaev.database.model.ConditionType;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.MappingType;
import io.openaev.database.model.PrimitiveType;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.inject.form.InjectInput;
import io.openaev.rest.injector_contract.InjectorContractService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("StepAutoLinkService Tests")
class StepAutoLinkServiceTest {

  private static final String CONTRACT_ID = "contract-id";

  @Mock private InjectorContractService injectorContractService;

  private final ObjectMapper mapper = new ObjectMapper();

  private StepAutoLinkService service() {
    return new StepAutoLinkService(injectorContractService, mapper);
  }

  private InjectInput injectInput() {
    InjectInput input = new InjectInput();
    input.setTitle("action");
    input.setInjectorContract(CONTRACT_ID);
    return input;
  }

  private void givenContractContent(String content) {
    InjectorContract contract = new InjectorContract();
    contract.setContent(content);
    when(injectorContractService.injectorContract(CONTRACT_ID)).thenReturn(contract);
  }

  @Test
  @DisplayName("Should build a global mapper condition per field exposing an argument type")
  void should_buildMapperCondition_perFieldWithArgumentType() {
    givenContractContent(
        """
        {"fields":[
          {"key":"target","type":"text","argumentType":"ipv4"},
          {"key":"port","type":"text","argumentType":"port"}
        ]}
        """);

    List<ConditionCreateInput> conditions = service().buildAutoLinkConditions(injectInput());

    assertEquals(2, conditions.size());
    ConditionCreateInput first = conditions.getFirst();
    assertEquals("0", first.getTemporaryId());
    assertEquals("target", first.getKey());
    assertEquals(ConditionType.MAPPER, first.getType());
    assertEquals(MappingType.GLOBAL, first.getMappingType());
    assertEquals(List.of(PrimitiveType.IPv4), first.getKeyTypes());
    assertEquals("1", conditions.get(1).getTemporaryId());
    assertEquals(List.of(PrimitiveType.Port), conditions.get(1).getKeyTypes());
  }

  @Test
  @DisplayName("Should skip fields without argument type")
  void should_skipFields_withoutArgumentType() {
    givenContractContent(
        """
        {"fields":[
          {"key":"plain","type":"text"},
          {"key":"nulled","type":"text","argumentType":null},
          {"key":"blank","type":"text","argumentType":" "},
          {"key":"unknown","type":"text","argumentType":"not-a-primitive"}
        ]}
        """);

    assertTrue(service().buildAutoLinkConditions(injectInput()).isEmpty());
  }

  @Test
  @DisplayName("Should still auto-link targeted asset fields of contracts without argument type")
  void should_autoLinkTargetedAssetField_withoutArgumentType() {
    givenContractContent(
        """
        {"fields":[{"key":"URL","type":"targeted-asset"}]}
        """);

    List<ConditionCreateInput> conditions = service().buildAutoLinkConditions(injectInput());

    assertEquals(1, conditions.size());
    assertEquals(List.of(PrimitiveType.TargetedAsset), conditions.getFirst().getKeyTypes());
  }

  @Test
  @DisplayName("Should return no condition when the contract content is unusable")
  void should_returnNoCondition_when_contentUnusable() {
    givenContractContent("not json");

    assertTrue(service().buildAutoLinkConditions(injectInput()).isEmpty());
  }

  @Test
  @DisplayName("Should return no condition when the injector contract is unknown")
  void should_returnNoCondition_when_contractNotFound() {
    when(injectorContractService.injectorContract(CONTRACT_ID))
        .thenThrow(new ElementNotFoundException("Threat arsenal item not found"));

    assertTrue(service().buildAutoLinkConditions(injectInput()).isEmpty());
  }

  @Test
  @DisplayName("Should return no condition when the step carries no injector contract")
  void should_returnNoCondition_when_noInjectorContract() {
    InjectInput input = new InjectInput();
    input.setTitle("action");

    assertTrue(service().buildAutoLinkConditions(input).isEmpty());
  }
}
