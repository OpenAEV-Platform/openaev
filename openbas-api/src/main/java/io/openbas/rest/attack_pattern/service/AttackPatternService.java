package io.openbas.rest.attack_pattern.service;

import static io.openbas.helper.StreamHelper.fromIterable;
import static io.openbas.utils.SecurityCoverageUtils.getExternalIds;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openbas.database.model.AttackPattern;
import io.openbas.database.model.SecurityCoverage;
import io.openbas.database.model.StixRefToExternalRef;
import io.openbas.database.repository.AttackPatternRepository;
import io.openbas.ee.Ee;
import io.openbas.rest.attack_pattern.form.AnalysisResultFromTTPExtractionAIWebserviceOutput;
import io.openbas.rest.exception.ElementNotFoundException;
import jakarta.annotation.Resource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttackPatternService {

  @Resource protected ObjectMapper mapper;

  private final Environment env;
  private final AttackPatternRepository attackPatternRepository;
  private final Ee ee;
  private final RestTemplate restTemplate;

  /**
   * Call the TTP Extraction AI Webservice to analyze files and text input.
   *
   * @param files List of files to be analyzed, maximum 5 files.
   * @param text Text input to be analyzed.
   * @return Response body from the TTP Extraction AI Webservice, expected to be a JSON array
   * @throws IOException
   */
  private String callTTPExtractionAIWebservice(List<MultipartFile> files, String text)
      throws IOException {
    String url = Objects.requireNonNull(env.getProperty("ttp.extraction.ai.webservice.url"));
    String certificate = ee.getEnterpriseEditionLicensePem();
    if (certificate == null || certificate.isBlank()) {
      throw new IllegalStateException("Enterprise Edition is not available");
    }
    String encodedCertificate =
        Base64.getEncoder().encodeToString(certificate.getBytes(StandardCharsets.UTF_8));

    // Set up the headers for the request
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.MULTIPART_FORM_DATA);
    headers.add("X-OpenBAS-Certificate", encodedCertificate);

    // Set up the request body
    MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
    for (MultipartFile file : files) {
      ByteArrayResource resource =
          new ByteArrayResource(file.getBytes()) {
            @Override
            public String getFilename() {
              return file.getOriginalFilename();
            }
          };
      bodyBuilder.part("files", resource);
    }
    bodyBuilder.part("text", text);

    HttpEntity<MultiValueMap<String, HttpEntity<?>>> requestEntity =
        new HttpEntity<>(bodyBuilder.build(), headers);

    // Make the POST request to the TTP Extraction AI Webservice
    ResponseEntity<String> response = restTemplate.postForEntity(url, requestEntity, String.class);

    if (response.getStatusCode().isError()) {
      log.error("Request to TTP Extraction AI Webservice failed: {}", response.getBody());
      throw new RestClientException(
          "Request to TTP Extraction AI Webservice failed: " + response.getBody());
    }
    return response.getBody();
  }

  /**
   * Find external attack pattern from Id.
   *
   * @param attackPatternId Id
   * @return attackPattern
   * @throws IOException
   */
  public AttackPattern findById(String attackPatternId) {
    return this.attackPatternRepository
        .findById(attackPatternId)
        .orElseThrow(
            () ->
                new ElementNotFoundException(
                    "Attack pattern not found with id: " + attackPatternId));
  }

  /**
   * Extract external attack pattern IDs from the response body of the TTP Extraction AI Webservice.
   *
   * @param responseBody The response body from the TTP Extraction AI Webservice, expected to be a
   *     JSON array
   * @return Set of external attack pattern IDs extracted from the response
   * @throws IOException
   */
  private Set<String> extractExternalAttackPatternIdsFromResponse(String responseBody)
      throws IOException {
    JsonNode fileOrTextJsonArray = mapper.readTree(responseBody);
    Set<String> externalAttackPatternIds = new HashSet<>();

    // For each (file or text_input) key-value pair in the JSON root
    for (JsonNode fileOrText : fileOrTextJsonArray) {
      for (JsonNode chunk : fileOrText) {
        AnalysisResultFromTTPExtractionAIWebserviceOutput result =
            mapper.convertValue(chunk, AnalysisResultFromTTPExtractionAIWebserviceOutput.class);

        externalAttackPatternIds.addAll(result.getPredictions().keySet());
      }
    }
    return externalAttackPatternIds;
  }

  public List<AttackPattern> getAttackPatternsByExternalIds(Set<String> ids) {
    if (ids.isEmpty()) {
      return Collections.emptyList();
    }
    return this.attackPatternRepository.findAllByExternalIdInIgnoreCase(new ArrayList<>(ids));
  }

  private List<AttackPattern> getAttackPatternsByInternalIds(Set<String> ids) {
    if (ids.isEmpty()) {
      return Collections.emptyList();
    }
    return fromIterable(this.attackPatternRepository.findAllById(new ArrayList<>(ids)));
  }

  /**
   * Get the attack pattern IDs from the external IDs.
   *
   * @param externalAttackPatternIds Set of external attack pattern IDs to be converted to internal
   *     IDs.
   * @return List of attack pattern IDs corresponding to the external IDs.
   */
  private List<String> getAttackPatternInternalIdsFromExternalIds(
      Set<String> externalAttackPatternIds) {
    return this.getAttackPatternsByExternalIds(externalAttackPatternIds).stream()
        .map(AttackPattern::getId)
        .toList();
  }

  public List<AttackPattern> getAttackPatternsByExternalIdsThrowIfMissing(
      Set<String> externalAttackPatternIds) {
    List<AttackPattern> attackPatterns =
        this.getAttackPatternsByExternalIds(externalAttackPatternIds);
    List<String> missingIds =
        externalAttackPatternIds.stream()
            .filter(
                id ->
                    !attackPatterns.stream()
                        .map(ap -> ap.getExternalId().toLowerCase())
                        .toList()
                        .contains(id.toLowerCase()))
            .toList();
    if (!missingIds.isEmpty()) {
      throw new ElementNotFoundException(
          String.format("Missing attack patterns: %s", String.join(", ", missingIds)));
    }
    return attackPatterns;
  }

  public List<AttackPattern> findAllByInternalIdsThrowIfMissing(Set<String> ids) {
    List<AttackPattern> attackPatterns = this.getAttackPatternsByInternalIds(ids);
    List<String> missingIds =
        ids.stream()
            .filter(id -> !attackPatterns.stream().map(AttackPattern::getId).toList().contains(id))
            .toList();
    if (!missingIds.isEmpty()) {
      throw new ElementNotFoundException(
          String.format("Missing attack patterns: %s", String.join(", ", missingIds)));
    }
    return attackPatterns;
  }

  /**
   * Validate the inputs for the TTP Extraction AI Webservice.
   *
   * @param files List of files to be analyzed, maximum 5 files.
   * @param text Text input to be analyzed.
   */
  private void validateInputs(List<MultipartFile> files, String text) {
    if (files.isEmpty() && (text == null || text.isBlank())) {
      throw new IllegalArgumentException("Either files or text must be provided");
    }
    if (files.size() > 5) {
      throw new IllegalArgumentException("Maximum of 5 files allowed");
    }
  }

  /**
   * Search for attack patterns using the TTP Extraction AI Webservice.
   *
   * @param files List of files to be analyzed, maximum 5 files.
   * @param text Text input to be analyzed.
   * @return List of attack pattern IDs found in the analysis.
   */
  public List<String> searchAttackPatternWithTTPAIWebservice(
      List<MultipartFile> files, String text) {
    validateInputs(files, text);
    try {
      String responseBody = callTTPExtractionAIWebservice(files, text);
      Set<String> attackPatternExternalIds =
          extractExternalAttackPatternIdsFromResponse(responseBody);
      return getAttackPatternInternalIdsFromExternalIds(attackPatternExternalIds);

    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  // -- STIX --

  /**
   * Resolves external AttackPattern references from a {@link SecurityCoverage} into internal {@link
   * AttackPattern} entities.
   *
   * @param stixRefs list of tuples linking an atatck pattern ext ID with a stix ID
   * @return list of resolved internal AttackPattern entities
   */
  public Map<String, AttackPattern> fetchInternalAttackPatternIds(
      Set<StixRefToExternalRef> stixRefs) {
    return getAttackPatternsByExternalIds(getExternalIds(stixRefs)).stream()
        .collect(Collectors.toMap(attack -> attack.getId(), Function.identity()));
  }
}
