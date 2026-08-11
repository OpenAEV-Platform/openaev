package io.openaev.output_processor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.database.model.Asset;
import io.openaev.database.model.Finding;
import io.openaev.database.repository.AssetRepository;
import io.openaev.rest.finding.FindingService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link OCSFOutputProcessor}, using JSON payloads shaped exactly like Prowler's own
 * OCSF fixtures (see {@code prowler-cloud/prowler:examples/output/*.ocsf.json}) rather than
 * hand-guessed OCSF-spec-shaped JSON, since Prowler's actual output diverges from a naive reading
 * of the OCSF spec on several fields (ARN location, compliance location/shape, status_code).
 */
class OCSFOutputProcessorTest {

  private final FindingService findingService = mock(FindingService.class);
  private final AssetRepository assetRepository = mock(AssetRepository.class);
  private final OCSFOutputProcessor processor =
      new OCSFOutputProcessor(findingService, assetRepository);
  private final ObjectMapper objectMapper = new ObjectMapper();

  private static final String AWS_FAIL_SAMPLE =
      """
      {
        "severity": "Low",
        "status_code": "FAIL",
        "finding_info": { "title": "Check if IAM Access Analyzer is enabled", "uid": "finding-1" },
        "resources": [
          {
            "cloud_partition": "aws",
            "region": "eu-west-1",
            "data": { "metadata": { "arn": "arn:aws:accessanalyzer:eu-west-1:123456789012:analyzer/x" } },
            "uid": "analyzer/x"
          }
        ],
        "cloud": { "account": { "uid": "123456789012" }, "region": "eu-west-1", "provider": "aws" },
        "remediation": {
          "desc": "Enable IAM Access Analyzer.",
          "references": ["aws accessanalyzer create-analyzer --analyzer-name X", "https://docs.aws.amazon.com/x"]
        },
        "unmapped": { "compliance": { "CIS-2.0": ["1.20"], "CIS-3.0": ["1.20"], "MITRE-ATTACK": ["T1580"] } }
      }
      """;

  @Nested
  @DisplayName("validate")
  class Validate {

    @Test
    @DisplayName("Should accept a FAIL record")
    void shouldAcceptFailRecord() throws Exception {
      JsonNode node = objectMapper.readTree(AWS_FAIL_SAMPLE);
      assertTrue(processor.validate(node));
    }

    @Test
    @DisplayName("Should accept a MANUAL record (requires human review)")
    void shouldAcceptManualRecord() throws Exception {
      JsonNode node =
          objectMapper.readTree(
              "{\"status_code\": \"MANUAL\", \"finding_info\": {\"title\": \"Check X\"}}");
      assertTrue(processor.validate(node));
    }

    @Test
    @DisplayName("Should reject a PASS record (compliant, not a misconfiguration)")
    void shouldRejectPassRecord() throws Exception {
      JsonNode node =
          objectMapper.readTree(
              "{\"status_code\": \"PASS\", \"finding_info\": {\"title\": \"Check X\"}}");
      assertFalse(processor.validate(node));
    }

    @Test
    @DisplayName("Should reject a record missing finding_info.title")
    void shouldRejectRecordMissingTitle() throws Exception {
      JsonNode node = objectMapper.readTree("{\"status_code\": \"FAIL\"}");
      assertFalse(processor.validate(node));
    }
  }

  @Nested
  @DisplayName("enrichFinding")
  class EnrichFinding {

    @Test
    @DisplayName("Should read the ARN nested under resources[0].data.metadata.arn")
    void shouldReadArnNestedUnderResourceDataMetadata() throws Exception {
      JsonNode node = objectMapper.readTree(AWS_FAIL_SAMPLE);
      Finding finding = new Finding();
      processor.enrichFinding(node, finding);
      assertEquals(
          "arn:aws:accessanalyzer:eu-west-1:123456789012:analyzer/x", finding.getResource());
    }

    @Test
    @DisplayName("Should fall back to resources[0].uid when no ARN is present (e.g. Azure/GCP/K8s)")
    void shouldFallBackToResourceUidWhenNoArn() throws Exception {
      JsonNode node =
          objectMapper.readTree(
              "{\"resources\": [{\"uid\": \"pod-uid-123\", \"data\": {\"metadata\": {}}}]}");
      Finding finding = new Finding();
      processor.enrichFinding(node, finding);
      assertEquals("pod-uid-123", finding.getResource());
    }

    @Test
    @DisplayName("Should combine remediation desc and references into finding_remediation")
    void shouldCombineRemediationDescAndReferences() throws Exception {
      JsonNode node = objectMapper.readTree(AWS_FAIL_SAMPLE);
      Finding finding = new Finding();
      processor.enrichFinding(node, finding);
      assertEquals(
          "Enable IAM Access Analyzer.\n\n"
              + "- aws accessanalyzer create-analyzer --analyzer-name X\n"
              + "- https://docs.aws.amazon.com/x",
          finding.getRemediation());
    }

    @Test
    @DisplayName("Should read the multi-framework compliance map from unmapped.compliance")
    void shouldReadComplianceFromUnmapped() throws Exception {
      JsonNode node = objectMapper.readTree(AWS_FAIL_SAMPLE);
      Finding finding = new Finding();
      processor.enrichFinding(node, finding);
      assertEquals("CIS-2.0: 1.20; CIS-3.0: 1.20; MITRE-ATTACK: T1580", finding.getCompliance());
    }

    @Test
    @DisplayName("Should leave compliance null when unmapped.compliance is absent")
    void shouldLeaveComplianceNullWhenAbsent() throws Exception {
      JsonNode node = objectMapper.readTree("{\"unmapped\": {}}");
      Finding finding = new Finding();
      processor.enrichFinding(node, finding);
      assertNull(finding.getCompliance());
    }

    @Test
    @DisplayName("Should read cloud account uid and region")
    void shouldReadCloudAccountAndRegion() throws Exception {
      JsonNode node = objectMapper.readTree(AWS_FAIL_SAMPLE);
      Finding finding = new Finding();
      processor.enrichFinding(node, finding);
      assertEquals("123456789012", finding.getCloudAccount());
      assertEquals("eu-west-1", finding.getCloudRegion());
    }

    @Test
    @DisplayName(
        "Should read the cloud provider (used by the frontend to label e.g. \"Cloud (AWS)\")")
    void shouldReadCloudProvider() throws Exception {
      JsonNode node = objectMapper.readTree(AWS_FAIL_SAMPLE);
      Finding finding = new Finding();
      processor.enrichFinding(node, finding);
      assertEquals("aws", finding.getCloudProvider());
    }
  }

  @Nested
  @DisplayName("toFindingAssets")
  class ToFindingAssets {

    @Test
    @DisplayName("Should resolve assets by the resource's ARN via findByExternalReferenceIn")
    void shouldResolveAssetsByArn() throws Exception {
      JsonNode node = objectMapper.readTree(AWS_FAIL_SAMPLE);
      Asset asset = mock(Asset.class);
      when(asset.getId()).thenReturn("asset-1");
      when(assetRepository.findByExternalReferenceIn(
              List.of("arn:aws:accessanalyzer:eu-west-1:123456789012:analyzer/x")))
          .thenReturn(List.of(asset));

      List<String> result = processor.toFindingAssets(node);

      assertEquals(List.of("asset-1"), result);
    }

    @Test
    @DisplayName("Should return empty list when there are no resources")
    void shouldReturnEmptyListWhenNoResources() throws Exception {
      JsonNode node = objectMapper.readTree("{}");
      List<String> result = processor.toFindingAssets(node);
      assertTrue(result.isEmpty());
    }
  }
}
