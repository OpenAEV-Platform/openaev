package io.openaev.service;

import static io.openaev.database.model.InjectExpectation.EXPECTATION_TYPE.*;
import static io.openaev.database.model.InjectExpectation.EXPECTATION_TYPE.VULNERABILITY;
import static io.openaev.database.model.InjectorContract.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.InjectorContract;
import io.openaev.expectation.ExpectationBuilderService;
import io.openaev.model.inject.form.Expectation;
import io.openaev.rest.injector_contract.InjectorContractService;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class ExpectationService {

  private final ExpectationBuilderService expectationBuilderService;
  private final InjectorContractService injectorContractService;

  @Resource protected ObjectMapper mapper;

  /**
   * Get available expectations for an inject by injector contract id
   *
   * @param injectorContractId injector contract id
   * @return available expectations
   */
  public List<Expectation> getAvailableExpectationsForInject(String injectorContractId) {
    InjectorContract injectorContract =
        injectorContractService.injectorContract(injectorContractId);
    ObjectNode injectorContractContent = injectorContract.getConvertedContent();
    boolean isHumanInject = false;
    List<io.openaev.model.inject.form.Expectation> availableExpectations = new ArrayList<>();
    Iterator<JsonNode> it = injectorContractContent.get(CONTRACT_CONTENT_FIELDS).elements();
    while (it.hasNext()) {
      JsonNode node = it.next();
      if (node.get(CONTRACT_ELEMENT_CONTENT_KEY)
          .asText()
          .equals(CONTRACT_ELEMENT_CONTENT_KEY_TEAMS)) {
        isHumanInject = true;
      }
      if (node.get(CONTRACT_ELEMENT_CONTENT_KEY)
          .asText()
          .equals(CONTRACT_ELEMENT_CONTENT_KEY_EXPECTATIONS)) {
        try {
          availableExpectations =
              mapper.readValue(
                  node.get(PREDEFINED_EXPECTATIONS).traverse(), new TypeReference<>() {});
        } catch (Exception e) {
          log.error(
              "Can't access to content predefinedExpectations for injector contract id {}",
              injectorContractId,
              e);
        }
      }
    }
    if (isHumanInject) {
      if (availableExpectations.stream()
          .noneMatch(expectation -> expectation.getType().equals(MANUAL))) {
        availableExpectations.add(expectationBuilderService.buildManualExpectation());
      }
    } else {
      if (availableExpectations.stream()
          .noneMatch(expectation -> expectation.getType().equals(DETECTION))) {
        availableExpectations.add(expectationBuilderService.buildDetectionExpectation());
      }
      if (availableExpectations.stream()
          .noneMatch(expectation -> expectation.getType().equals(PREVENTION))) {
        availableExpectations.add(expectationBuilderService.buildPreventionExpectation());
      }
      if (availableExpectations.stream()
          .noneMatch(expectation -> expectation.getType().equals(VULNERABILITY))) {
        availableExpectations.add(expectationBuilderService.buildVulnerabilityExpectation());
      }
    }
    return availableExpectations;
  }
}
