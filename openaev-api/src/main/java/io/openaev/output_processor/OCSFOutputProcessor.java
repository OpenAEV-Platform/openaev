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
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
 *
 * <p>Field mapping below was cross-checked against Prowler's own OCSF sample fixtures (see {@code
 * prowler-cloud/prowler:examples/output/*.ocsf.json}), since the OCSF spec alone leaves some of
 * Prowler's conventions (e.g. where compliance data actually lives) unspecified.
 */
@Component
public class OCSFOutputProcessor extends FindingCapableOutputProcessor {

  public static final String FINDING_INFO = "finding_info";
  public static final String TITLE = "title";
  public static final String UID = "uid";
  public static final String SEVERITY = "severity";
  public static final String RESOURCES = "resources";
  public static final String DATA = "data";
  public static final String METADATA = "metadata";
  public static final String ARN = "arn";
  public static final String CLOUD = "cloud";
  public static final String ACCOUNT = "account";
  public static final String REGION = "region";
  public static final String PROVIDER = "provider";
  public static final String REMEDIATION = "remediation";
  public static final String DESC = "desc";
  public static final String REFERENCES = "references";
  public static final String UNMAPPED = "unmapped";
  public static final String COMPLIANCE = "compliance";
  public static final String STATUS_CODE = "status_code";
  public static final String STATUS_CODE_PASS = "PASS";

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
            new ContractOutputField(REMEDIATION, ContractOutputTechnicalType.Object, false),
            new ContractOutputField(UNMAPPED, ContractOutputTechnicalType.Object, false)),
        findingService);
    this.assetRepository = assetRepository;
  }

  /**
   * A record is a candidate Finding only if it has a title and its check did not simply "PASS".
   * "PASS" means the resource is compliant (no misconfiguration), so turning it into a Finding
   * would be misleading. "FAIL" and "MANUAL" (requires human review) are both kept, as well as any
   * unrecognized/missing status_code, to fail open rather than silently drop data.
   */
  @Override
  public boolean validate(JsonNode jsonNode) {
    JsonNode findingInfo = jsonNode.get(FINDING_INFO);
    if (findingInfo == null || !findingInfo.hasNonNull(TITLE)) {
      return false;
    }
    JsonNode statusCode = jsonNode.get(STATUS_CODE);
    return statusCode == null || !STATUS_CODE_PASS.equalsIgnoreCase(statusCode.asText());
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
      String identifier = resourceIdentifier(resource);
      if (identifier != null) {
        finding.setResource(identifier);
      }
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
      if (cloud.hasNonNull(PROVIDER)) {
        finding.setCloudProvider(cloud.get(PROVIDER).asText());
      }
    }

    String remediation = remediationText(jsonNode);
    if (remediation != null) {
      finding.setRemediation(remediation);
    }

    String compliance = complianceText(jsonNode);
    if (compliance != null) {
      finding.setCompliance(compliance);
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

  /**
   * A resource's ARN is nested under {@code resource.data.metadata.arn} in Prowler's OCSF output
   * (not a top-level {@code resource.arn}, as the OCSF spec's generic "resource details" object
   * would suggest) - falls back to the OCSF-standard top-level {@code resource.uid} when no ARN is
   * present, which is the case for providers without ARNs (e.g. Azure, GCP, Kubernetes).
   */
  private String resourceIdentifier(JsonNode resource) {
    JsonNode arnNode = resource.path(DATA).path(METADATA).get(ARN);
    if (arnNode != null && !arnNode.asText().isBlank()) {
      return arnNode.asText();
    }
    JsonNode uidNode = resource.get(UID);
    if (uidNode != null && !uidNode.asText().isBlank()) {
      return uidNode.asText();
    }
    return null;
  }

  /** ARNs (falling back to uid) of every referenced resource, deduplicated, in order. */
  private List<String> resourceIdentifiers(JsonNode jsonNode) {
    JsonNode resources = jsonNode.get(RESOURCES);
    if (resources == null || !resources.isArray()) {
      return Collections.emptyList();
    }
    Set<String> identifiers = new LinkedHashSet<>();
    for (JsonNode resource : resources) {
      String identifier = resourceIdentifier(resource);
      if (identifier != null) {
        identifiers.add(identifier);
      }
    }
    return new ArrayList<>(identifiers);
  }

  /**
   * Builds the remediation text from the OCSF {@code remediation} object, which carries both a
   * free-text description and a list of actionable references (CLI commands and/or documentation
   * links). Both are kept, since the references are often the most actionable part (e.g. the exact
   * AWS CLI command to run).
   */
  private String remediationText(JsonNode jsonNode) {
    JsonNode remediation = jsonNode.get(REMEDIATION);
    if (remediation == null) {
      return null;
    }
    String desc = remediation.hasNonNull(DESC) ? remediation.get(DESC).asText() : null;
    JsonNode referencesNode = remediation.get(REFERENCES);
    List<String> references =
        referencesNode != null && referencesNode.isArray()
            ? StreamSupport.stream(referencesNode.spliterator(), false)
                .map(JsonNode::asText)
                .filter(reference -> !reference.isBlank())
                .collect(Collectors.toList())
            : Collections.emptyList();

    StringBuilder text = new StringBuilder();
    if (desc != null && !desc.isBlank()) {
      text.append(desc);
    }
    if (!references.isEmpty()) {
      if (text.length() > 0) {
        text.append("\n\n");
      }
      text.append(references.stream().collect(Collectors.joining("\n- ", "- ", "")));
    }
    return text.length() > 0 ? text.toString() : null;
  }

  /**
   * Prowler stores violated compliance requirements as a map of framework name to requirement ids
   * (e.g. {@code {"CIS-2.0": ["1.20"], "MITRE-ATTACK": ["T1098"]}}) under {@code
   * unmapped.compliance} - not as a flat {@code requirements} array at the record's root, which is
   * not part of Prowler's actual OCSF output despite being a plausible-looking OCSF field name.
   */
  private String complianceText(JsonNode jsonNode) {
    JsonNode compliance = jsonNode.path(UNMAPPED).get(COMPLIANCE);
    if (compliance == null || !compliance.isObject()) {
      return null;
    }
    List<String> frameworks = new ArrayList<>();
    Iterator<Map.Entry<String, JsonNode>> fields = compliance.fields();
    while (fields.hasNext()) {
      Map.Entry<String, JsonNode> entry = fields.next();
      JsonNode requirementsNode = entry.getValue();
      if (requirementsNode == null || !requirementsNode.isArray() || requirementsNode.isEmpty()) {
        continue;
      }
      String requirements =
          StreamSupport.stream(requirementsNode.spliterator(), false)
              .map(JsonNode::asText)
              .collect(Collectors.joining(", "));
      frameworks.add(entry.getKey() + ": " + requirements);
    }
    return frameworks.isEmpty() ? null : String.join("; ", frameworks);
  }
}
