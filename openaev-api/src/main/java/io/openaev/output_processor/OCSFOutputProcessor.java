package io.openaev.output_processor;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.database.model.ContractOutputField;
import io.openaev.database.model.ContractOutputTechnicalType;
import io.openaev.database.model.ContractOutputType;
import io.openaev.database.model.Finding;
import io.openaev.database.repository.AssetRepository;
import io.openaev.rest.finding.FindingService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.springframework.stereotype.Component;

/**
 * Parses OCSF (Open Cybersecurity Schema Framework) Detection Finding records, as emitted
 * unmodified by Prowler's {@code -M json-ocsf} output, into OpenAEV {@link Finding}s.
 *
 * <p>The Prowler injector never transforms Prowler's output: it forwards the native OCSF JSON
 * as-is, and this processor is the single place responsible for interpreting that schema. Each
 * array element is one OCSF Detection Finding, which can reference multiple {@code resources[]};
 * for simplicity (and because Prowler emits one resource per check result in practice) only the
 * first resource is used to populate the resource/cloud fields - see {@link #firstResource}.
 */
@Component
public class OCSFOutputProcessor extends FindingCapableOutputProcessor {

  public static final String FINDING_INFO = "finding_info";
  public static final String TITLE = "title";
  public static final String UID = "uid";
  public static final String SEVERITY = "severity";
  public static final String RESOURCES = "resources";
  public static final String ARN = "arn";
  public static final String CLOUD = "cloud";
  public static final String ACCOUNT = "account";
  public static final String REGION = "region";
  public static final String REMEDIATION = "remediation";
  public static final String DESC = "desc";
  public static final String COMPLIANCE = "compliance";
  public static final String REQUIREMENTS = "requirements";

  private final AssetRepository assetRepository;

  public OCSFOutputProcessor(FindingService findingService, AssetRepository assetRepository) {
    super(
        ContractOutputType.OCSF,
        ContractOutputTechnicalType.Object,
        List.of(
            new ContractOutputField(FINDING_INFO, ContractOutputTechnicalType.Object, true),
            new ContractOutputField(SEVERITY, ContractOutputTechnicalType.Text, false),
            new ContractOutputField(RESOURCES, ContractOutputTechnicalType.Object, false),
            new ContractOutputField(CLOUD, ContractOutputTechnicalType.Object, false),
            new ContractOutputField(REMEDIATION, ContractOutputTechnicalType.Object, false)),
        findingService);
    this.assetRepository = assetRepository;
  }

  @Override
  public boolean validate(JsonNode jsonNode) {
    JsonNode findingInfo = jsonNode.get(FINDING_INFO);
    return findingInfo != null && findingInfo.hasNonNull(TITLE);
  }

  /** The check's title (e.g. "S3 Bucket Server Access Logging Disabled") is the finding value. */
  @Override
  public String toFindingValue(JsonNode jsonNode) {
    return jsonNode.get(FINDING_INFO).get(TITLE).asText();
  }

  /**
   * Resolves the OCSF resources (identified by ARN/uid) to existing OpenAEV assets pre-created with
   * a matching {@code asset_external_reference}. Prowler's cloud resources have no inherent
   * relationship to OpenAEV asset UUIDs, unlike other processors (e.g. CVE) which receive an
   * explicit {@code asset_id} from the scanner's payload.
   */
  @Override
  public List<String> toFindingAssets(JsonNode jsonNode) {
    List<String> externalReferences = resourceIdentifiers(jsonNode);
    if (externalReferences.isEmpty()) {
      return Collections.emptyList();
    }
    return assetRepository.findByExternalReferenceIn(externalReferences).stream()
        .map(io.openaev.database.model.Asset::getId)
        .collect(Collectors.toList());
  }

  @Override
  public void enrichFinding(JsonNode jsonNode, Finding finding) {
    JsonNode severityNode = jsonNode.get(SEVERITY);
    if (severityNode != null) {
      finding.setSeverity(severityNode.asText());
    }

    JsonNode resource = firstResource(jsonNode);
    if (resource != null) {
      JsonNode arnNode = resource.get(ARN);
      JsonNode uidNode = resource.get(UID);
      finding.setResource(
          arnNode != null ? arnNode.asText() : uidNode != null ? uidNode.asText() : null);
    }

    JsonNode cloud = jsonNode.get(CLOUD);
    if (cloud != null) {
      JsonNode account = cloud.get(ACCOUNT);
      if (account != null && account.hasNonNull(UID)) {
        finding.setCloudAccount(account.get(UID).asText());
      }
      if (cloud.hasNonNull(REGION)) {
        finding.setCloudRegion(cloud.get(REGION).asText());
      }
    }

    JsonNode remediation = jsonNode.get(REMEDIATION);
    if (remediation != null && remediation.hasNonNull(DESC)) {
      finding.setRemediation(remediation.get(DESC).asText());
    }

    JsonNode compliance = jsonNode.get(COMPLIANCE);
    if (compliance != null && compliance.get(REQUIREMENTS) != null) {
      String joined =
          StreamSupport.stream(compliance.get(REQUIREMENTS).spliterator(), false)
              .map(JsonNode::asText)
              .collect(Collectors.joining(", "));
      if (!joined.isEmpty()) {
        finding.setCompliance(joined);
      }
    }
  }

  /** First entry of {@code resources[]}, or {@code null} if absent/empty. */
  private JsonNode firstResource(JsonNode jsonNode) {
    JsonNode resources = jsonNode.get(RESOURCES);
    if (resources == null || !resources.isArray() || resources.isEmpty()) {
      return null;
    }
    return resources.get(0);
  }

  /** ARNs (falling back to uid) of every referenced resource, deduplicated, in order. */
  private List<String> resourceIdentifiers(JsonNode jsonNode) {
    JsonNode resources = jsonNode.get(RESOURCES);
    if (resources == null || !resources.isArray()) {
      return Collections.emptyList();
    }
    Set<String> identifiers = new LinkedHashSet<>();
    for (JsonNode resource : resources) {
      JsonNode arnNode = resource.get(ARN);
      JsonNode uidNode = resource.get(UID);
      if (arnNode != null && !arnNode.asText().isBlank()) {
        identifiers.add(arnNode.asText());
      } else if (uidNode != null && !uidNode.asText().isBlank()) {
        identifiers.add(uidNode.asText());
      }
    }
    return new ArrayList<>(identifiers);
  }
}
