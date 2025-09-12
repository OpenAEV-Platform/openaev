package io.openbas.utils.mapper;

import static io.openbas.database.model.InjectorContract.CONTRACT_ELEMENT_CONTENT_KEY_EXPECTATIONS;
import static io.openbas.utils.InjectExpectationResultUtils.ExpectationResultsByType;
import static io.openbas.utils.InjectExpectationResultUtils.getExpectationResultByTypes;
import static java.util.Collections.emptyList;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openbas.database.model.AttackPattern;
import io.openbas.database.model.Inject;
import io.openbas.database.model.InjectExpectation;
import io.openbas.database.raw.RawInjectExpectation;
import io.openbas.database.repository.InjectRepository;
import io.openbas.expectation.ExpectationType;
import io.openbas.rest.inject.form.InjectExpectationResultsByAttackPattern;
import io.openbas.utils.InjectExpectationResultUtils;
import io.openbas.utils.InjectUtils;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class InjectExpectationMapper {

  public static final String NODE_EXPECTATION_TYPE = "expectation_type";

  private static final EnumSet<ExpectationType> ALL_EXPECTATION_TYPES =
      EnumSet.allOf(ExpectationType.class);

  private final InjectRepository injectRepository;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final InjectUtils injectUtils;

  /**
   * Build ExpectationResultsByType from injectContent
   *
   * @param injectContent
   * @param expectations
   * @param scoreExtractor
   * @return List of ExpectationResultsByType
   */
  public <T> List<ExpectationResultsByType> extractExpectationResults(
      ObjectNode injectContent,
      List<T> expectations,
      BiFunction<List<InjectExpectation.EXPECTATION_TYPE>, List<T>, List<Double>> scoreExtractor) {
    List<ExpectationResultsByType> expectationResultByTypes =
        getExpectationResultByTypes(expectations, scoreExtractor);

    if (!expectationResultByTypes.isEmpty()) {
      return expectationResultByTypes;
    }
    if (injectContent == null) {
      return emptyList();
    }

    return buildExpectationResultsFromInjectContent(injectContent);
  }

  /**
   * Build InjectResults based on expectation defined in the content of inject
   *
   * @param injectContent content of inject where expectations have been defined
   * @return List of InjectResultsByType
   */
  private static List<ExpectationResultsByType> buildExpectationResultsFromInjectContent(
      ObjectNode injectContent) {

    JsonNode contentNode = injectContent.get(CONTRACT_ELEMENT_CONTENT_KEY_EXPECTATIONS);
    if (contentNode == null || !contentNode.isArray()) {
      return emptyList();
    }

    Set<ExpectationType> uniqueTypes = new HashSet<>();
    for (JsonNode expectationNode : contentNode) {
      JsonNode typeNode = expectationNode.get(NODE_EXPECTATION_TYPE);
      if (typeNode != null && typeNode.isTextual()) {
        try {
          ExpectationType type = ExpectationType.of(typeNode.asText().toUpperCase());
          uniqueTypes.add(type);
        } catch (IllegalArgumentException e) {
          log.warn("Expectation Type is no valid", e);
        }
      }
    }

    return buildFallbackResults(uniqueTypes);
  }

  /**
   * Build InjectExpectationResultsByAttackPattern from InjectExpectation related to attackPatterns
   *
   * @param attackPattern
   * @param injects
   * @return List of InjectExpectationResultsByAttackPattern
   */
  public InjectExpectationResultsByAttackPattern toInjectExpectationResultsByattackPattern(
      final AttackPattern attackPattern, @NotNull final List<Inject> injects) {

    return InjectExpectationResultsByAttackPattern.builder()
        .results(
            injects.stream()
                .map(
                    inject -> {
                      InjectExpectationResultsByAttackPattern.InjectExpectationResultsByType
                          result =
                              new InjectExpectationResultsByAttackPattern
                                  .InjectExpectationResultsByType();
                      result.setInjectId(inject.getId());
                      result.setInjectTitle(inject.getTitle());
                      result.setResults(
                          extractExpectationResults(
                              inject.getContent(),
                              injectUtils.getPrimaryExpectations(inject),
                              InjectExpectationResultUtils::getScores));
                      return result;
                    })
                .collect(Collectors.toList()))
        .attackPattern(attackPattern)
        .build();
  }

  /**
   * Extract ExpectationResultsByType from exercises using data from raw queries
   *
   * @param injectIds
   * @param expectations
   * @return List of ExpectationResultsByType
   */
  public List<ExpectationResultsByType> extractExpectationResultByTypesFromRaw(
      Set<String> injectIds, List<RawInjectExpectation> expectations) {

    if (expectations != null && !expectations.isEmpty()) {
      return getExpectationResultByTypes(
          expectations, InjectExpectationResultUtils::getScoresFromRaw);
    }

    return buildExpectationResultsFromInjectContents(injectIds);
  }

  /**
   * Extract ExpectationResultsByType from exercises using data from raw queries
   *
   * @param injectIds
   * @param expectations
   * @return List of ExpectationResultsByType
   */
  public List<ExpectationResultsByType> extractExpectationResultByTypes(
      Set<String> injectIds, List<InjectExpectation> expectations) {

    if (expectations != null && !expectations.isEmpty()) {
      return getExpectationResultByTypes(expectations, InjectExpectationResultUtils::getScores);
    }

    return buildExpectationResultsFromInjectContents(injectIds);
  }

  /**
   * Build InjectResults based on content of injects from an exercise
   *
   * @param injectIds the exercise id
   * @return List of InjectResultsByType
   */
  private List<ExpectationResultsByType> buildExpectationResultsFromInjectContents(
      @NotBlank Set<String> injectIds) {

    // Fetch all inject contents in order to extract expectations defined in every inject
    List<String> rawContents = injectRepository.findContentsByInjectIds(injectIds);
    Set<ExpectationType> foundTypes = new HashSet<>();

    for (String contentJson : rawContents) {
      if (contentJson == null || contentJson.isBlank()) continue;

      try {
        JsonNode jsonNode = objectMapper.readTree(contentJson);

        if (jsonNode != null && jsonNode.isObject()) {
          ObjectNode contentNode = (ObjectNode) jsonNode;

          // ExpectationResults from one injectContent
          List<ExpectationResultsByType> results =
              buildExpectationResultsFromInjectContent(contentNode);

          // Check if all expectation types have already been added, if so stop the loop
          for (ExpectationResultsByType r : results) {
            if (ALL_EXPECTATION_TYPES.contains(r.type())) {
              foundTypes.add(r.type());
              if (foundTypes.size() == ALL_EXPECTATION_TYPES.size()) {
                break;
              }
            }
          }
        }

      } catch (JsonProcessingException e) {
        log.warn("Invalid JSON in inject content", e);
      }
    }

    return buildFallbackResults(foundTypes);
  }

  /**
   * Build final list of ExpectationResults using InjectExpectationResultUtils methods
   *
   * @param foundTypes ExpectationTypes defined in the content of inject
   * @return List of ExpectationResultsByType
   */
  private static List<ExpectationResultsByType> buildFallbackResults(
      Set<ExpectationType> foundTypes) {
    List<ExpectationResultsByType> fallbackResults = new ArrayList<>();
    for (ExpectationType type : foundTypes) {
      InjectExpectationResultUtils.getExpectationByType(type, emptyList())
          .ifPresent(fallbackResults::add);
    }
    return fallbackResults;
  }
}
