package io.openaev.rest.attack_pattern.service;

import static io.openaev.helper.StreamHelper.fromIterable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.context.TenantContext;
import io.openaev.database.model.*;
import io.openaev.database.repository.AttackPatternRepository;
import io.openaev.database.repository.KillChainPhaseRepository;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.rest.attack_pattern.form.AnalysisResultFromTTPExtractionAIWebserviceOutput;
import io.openaev.rest.attack_pattern.form.AttackPatternCreateInput;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.utils.SecurityCoverageUtils;
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
  private final KillChainPhaseRepository killChainPhaseRepository;
  private final EnterpriseEditionService enterpriseEditionService;
  private final RestTemplate restTemplate;
  private final SecurityCoverageUtils securityCoverageUtils;

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
    String certificate = enterpriseEditionService.getEnterpriseEditionLicensePem();
    if (certificate == null || certificate.isBlank()) {
      throw new IllegalStateException("Enterprise Edition is not available");
    }
    String encodedCertificate =
        Base64.getEncoder().encodeToString(certificate.getBytes(StandardCharsets.UTF_8));

    // Set up the headers for the request
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.MULTIPART_FORM_DATA);
    headers.add("X-OpenAEV-Certificate", encodedCertificate);

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
    return this.attackPatternRepository.findAllByExternalIdInIgnoreCaseAndTenantId(
        new ArrayList<>(ids), TenantContext.getCurrentTenant());
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
    return getAttackPatternsByExternalIds(securityCoverageUtils.getExternalIds(stixRefs)).stream()
        .collect(Collectors.toMap(attack -> attack.getId(), Function.identity()));
  }

  public List<AttackPattern> getAttackPattern(List<String> idsAttackPattern) {
    return attackPatternRepository.findAllByIdIn(idsAttackPattern);
  }

  private AttackPattern createAttackPatternFromAttackPatternCreateInput(
      AttackPatternCreateInput input) {
    AttackPattern newAttackPattern = new AttackPattern();
    newAttackPattern.setName(input.getName());
    newAttackPattern.setStixId(input.getStixId());
    newAttackPattern.setDescription(input.getDescription());
    newAttackPattern.setExternalId(input.getExternalId());
    newAttackPattern.setPlatforms(input.getPlatforms());
    newAttackPattern.setPermissionsRequired(input.getPermissionsRequired());
    newAttackPattern.setTenant(new Tenant(TenantContext.getCurrentTenant()));
    return newAttackPattern;
  }

  /**
   * Finds an existing attack pattern by external ID, or creates a new one if none exists.
   *
   * <p>Unlike {@link #internalUpsertAttackPatterns}, this method does <b>not</b> update an existing
   * entity.
   *
   * @param input the attack pattern data used for lookup (by external ID) and creation
   * @return the existing or newly created attack pattern
   */
  public AttackPattern findOrCreate(AttackPatternCreateInput input) {
    String tenant = TenantContext.getCurrentTenant();
    Optional<AttackPattern> attackPattern =
        attackPatternRepository
            .findAllByExternalIdInIgnoreCaseAndTenantId(List.of(input.getExternalId()), tenant)
            .stream()
            .findFirst();
    return attackPattern.orElseGet(
        () -> attackPatternRepository.save(createAttackPatternFromAttackPatternCreateInput(input)));
  }

  public List<AttackPattern> internalUpsertAttackPatterns(
      List<AttackPatternCreateInput> attackPatterns, Boolean ignoreDependencies) {
    List<AttackPattern> upserted = new ArrayList<>();
    attackPatterns.forEach(
        attackPatternInput -> {
          String attackPatternExternalId = attackPatternInput.getExternalId();
          Optional<AttackPattern> optionalAttackPattern =
              attackPatternRepository.findByExternalId(attackPatternExternalId);
          List<KillChainPhase> killChainPhases =
              attackPatternInput.getKillChainPhasesIds() != null
                      && !attackPatternInput.getKillChainPhasesIds().isEmpty()
                  ? fromIterable(
                      killChainPhaseRepository.findAllById(
                          attackPatternInput.getKillChainPhasesIds()))
                  : new ArrayList<>();
          AttackPattern attackPatternParent =
              attackPatternInput.getParentId() != null
                  ? attackPatternRepository
                      .findByStixId(attackPatternInput.getParentId())
                      .orElseThrow(ElementNotFoundException::new)
                  : null;
          if (optionalAttackPattern.isEmpty()) {
            attackPatternInput.setExternalId(attackPatternExternalId);
            AttackPattern newAttackPattern =
                createAttackPatternFromAttackPatternCreateInput(attackPatternInput);
            newAttackPattern.setKillChainPhases(killChainPhases);
            newAttackPattern.setExternalId(attackPatternExternalId);
            upserted.add(newAttackPattern);
          } else {
            AttackPattern attackPattern = optionalAttackPattern.get();
            // In this case, the input may not contain kill chain phases or parent, we keep the
            // original
            if (ignoreDependencies) {
              if (killChainPhases.isEmpty() && !attackPattern.getKillChainPhases().isEmpty()) {
                killChainPhases = attackPattern.getKillChainPhases();
              }
              if (attackPatternParent == null && attackPattern.getParent() != null) {
                attackPatternParent = attackPattern.getParent();
              }
            }
            attackPattern.setStixId(attackPatternInput.getStixId());
            attackPattern.setKillChainPhases(killChainPhases);
            attackPattern.setName(attackPatternInput.getName());
            attackPattern.setDescription(attackPatternInput.getDescription());
            attackPattern.setPlatforms(attackPatternInput.getPlatforms());
            attackPattern.setPermissionsRequired(attackPatternInput.getPermissionsRequired());
            attackPattern.setParent(attackPatternParent);
            upserted.add(attackPattern);
          }
        });
    return fromIterable(this.attackPatternRepository.saveAll(upserted));
  }
}
