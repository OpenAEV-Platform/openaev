package io.openaev.service.finding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.database.model.Asset;
import io.openaev.database.model.ContractOutputType;
import io.openaev.database.model.Finding;
import io.openaev.database.model.FindingEvidenceScope;
import io.openaev.database.model.FindingLocationType;
import io.openaev.database.model.FindingOccurrence;
import io.openaev.database.model.FindingTargetRole;
import io.openaev.database.model.Inject;
import io.openaev.database.model.Injector;
import io.openaev.database.model.StableFinding;
import io.openaev.database.model.StableFindingCategory;
import io.openaev.database.model.StableFindingLifecycle;
import io.openaev.database.model.Tag;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.FindingOccurrenceRepository;
import io.openaev.database.repository.StableFindingRepository;
import io.openaev.rest.inject.service.ContractOutputContext;
import io.openaev.rest.tag.TagService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Stable finding callback ingestion")
class StableFindingIngestionServiceTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final StableFindingRepository stableRepository = mock(StableFindingRepository.class);
  private final FindingOccurrenceRepository occurrenceRepository =
      mock(FindingOccurrenceRepository.class);
  private final List<FindingOccurrence> occurrences = new ArrayList<>();
  private final Map<String, StableFinding> stableFindings = new LinkedHashMap<>();
  private final AtomicReference<StableFinding> stable = new AtomicReference<>();
  private final TagService tagService = mock(TagService.class);
  private final StableFindingIngestionService service =
      new StableFindingIngestionService(stableRepository, occurrenceRepository, tagService);

  @BeforeEach
  void setup() {
    occurrences.clear();
    stableFindings.clear();
    stable.set(null);
    when(stableRepository.findByKeyAndTenantId(anyString(), anyString()))
        .thenAnswer(
            invocation -> Optional.ofNullable(stableFindings.get(invocation.getArgument(0))));
    when(stableRepository.save(any(StableFinding.class)))
        .thenAnswer(
            invocation -> {
              StableFinding saved = invocation.getArgument(0);
              if (saved.getId() == null) {
                saved.setId("stable-" + (stableFindings.size() + 1));
              }
              stableFindings.put(saved.getKey(), saved);
              stable.set(saved);
              return saved;
            });
    when(occurrenceRepository.save(any(FindingOccurrence.class)))
        .thenAnswer(
            invocation -> {
              FindingOccurrence saved = invocation.getArgument(0);
              saved.setId("occurrence-" + occurrences.size());
              occurrences.add(saved);
              return saved;
            });
    when(occurrenceRepository.findAllByStableFindingIdAndTenantId(anyString(), anyString()))
        .thenAnswer(
            invocation ->
                occurrences.stream()
                    .filter(
                        occurrence ->
                            occurrence.getStableFinding().getId().equals(invocation.getArgument(0)))
                    .toList());
    when(occurrenceRepository
            .findFirstByStableFindingIdAndTenantIdOrderByObservedAtDescInjectIdDescLocationTypeDescLocationKeyDescIdDesc(
                anyString(), anyString()))
        .thenAnswer(
            invocation ->
                occurrences.stream()
                    .filter(
                        occurrence ->
                            occurrence.getStableFinding().getId().equals(invocation.getArgument(0)))
                    .max(
                        Comparator.comparing(FindingOccurrence::getObservedAt)
                            .thenComparing(o -> o.getInject().getId())
                            .thenComparing(
                                o -> o.getLocationKey() == null ? "" : o.getLocationKey())));
    when(occurrenceRepository.existsLiveOccurrence(
            anyString(), anyString(), anyString(), any(), any()))
        .thenAnswer(
            invocation ->
                occurrences.stream()
                    .anyMatch(
                        occurrence ->
                            occurrence.getInject().getId().equals(invocation.getArgument(2))
                                && occurrence.getLocationType() == invocation.getArgument(3)
                                && java.util.Objects.equals(
                                    occurrence.getLocationKey(), invocation.getArgument(4))));
  }

  @Nested
  @DisplayName("Identity and occurrence fanout")
  class IdentityAndFanout {

    @Test
    @DisplayName("Should create one finding per resource and deduplicate a callback replay")
    void given_multiResourceOcsf_should_fanOutAndDeduplicateReplay() throws Exception {
      // Arrange
      Inject inject = inject("tenant-a", "inject-1", "injector-1");
      ContractOutputContext output = output();
      JsonNode source =
          objectMapper.readTree(
              """
              {
                "time_dt": "2026-09-17T07:00:00Z",
                "status_code": "FAIL",
                "status_detail": "policy failed",
                "severity": "High",
                "severity_id": 4,
                "finding_info": {"uid": "finding-uid", "title": "Rule title", "desc": "Rule desc",
                                 "types": ["Configuration", "Cloud"]},
                "metadata": {"uid": "scan-1", "event_code": "rule-1",
                             "product": {"uid": " Prowler "}},
                "risk_details": {"score": 8},
                "category_name": "Configuration",
                "unmapped": {"categories": ["Identity", "Cloud"],
                             "compliance": {"MITRE-ATTACK": ["T1001"]}},
                "mitre_attack": [{"technique_id": "T1002"}, "T1003"],
                "resources": [
                  {"uid": "RESOURCE-A", "name": "first", "type": "Bucket",
                   "cloud_partition": "aws", "region": "eu-west-1",
                   "account": {"uid": "account-a"}, "group": {"name": "storage"}},
                  {"data": {"metadata": {"arn": "arn:aws:s3:::second"}},
                   "name": "second", "type": "Bucket", "cloud_partition": "aws-us-gov",
                   "region": "us-gov-west-1", "account": {"uid": "account-b"}}
                ]
              }
              """);
      Finding finding = finding(inject, output, "rule-1");

      // Act
      service.ingest(inject, output, List.of(finding), List.of(source));
      service.ingest(inject, output, List.of(finding), List.of(source));

      // Assert
      assertThat(occurrences).hasSize(2);
      assertThat(stableFindings).hasSize(2);
      assertThat(stableFindings.values())
          .extracting(StableFinding::getLocationKey)
          .containsExactly(
              "aws/account-a/eu-west-1/resource-a",
              "aws-us-gov/account-b/us-gov-west-1/arn:aws:s3:::second");
      assertThat(occurrences)
          .extracting(FindingOccurrence::getSourceInjectorId)
          .containsOnly("injector-1");
      assertThat(occurrences)
          .extracting(FindingOccurrence::getLocationKey)
          .containsExactly(
              "aws/account-a/eu-west-1/resource-a",
              "aws-us-gov/account-b/us-gov-west-1/arn:aws:s3:::second");
      assertThat(occurrences)
          .extracting(FindingOccurrence::getLocation)
          .containsExactly(
              "aws/account-a/eu-west-1/RESOURCE-A",
              "aws-us-gov/account-b/us-gov-west-1/arn:aws:s3:::second");
      assertThat(occurrences)
          .extracting(FindingOccurrence::getResourceProvider)
          .containsExactly("aws", "aws-us-gov");
      assertThat(occurrences.getFirst().getResourceAccount()).isEqualTo("account-a");
      assertThat(occurrences.getFirst().getResourceService()).isEqualTo("storage");
      assertThat(occurrences.getFirst().getRawPayload()).isEqualTo(source.toString());
      assertThat(occurrences.getFirst().getCategories())
          .containsExactly("Configuration", "Cloud", "Identity");
      assertThat(occurrences.getFirst().getAttackPatterns())
          .containsExactly("T1001", "T1002", "T1003");
      assertThat(stable.get().getSourceNamespace()).isEqualTo("injector-1/findings/prowler");
      assertThat(stable.get().getFirstSeen()).isEqualTo(Instant.parse("2026-09-17T07:00:00Z"));
      assertThat(stable.get().getLifecycle()).isEqualTo(StableFindingLifecycle.ACTIVE);
    }

    @Test
    @DisplayName("Should keep scanner version, links, labels and MITRE attacks of a record")
    void given_ocsfContext_should_persistSourceLinksLabelsAndAttacks() throws Exception {
      // Arrange
      Inject inject = inject("tenant-a", "inject-1", "injector-1");
      ContractOutputContext output = output();
      JsonNode source =
          objectMapper.readTree(
              """
              {
                "time_dt": "2026-09-17T07:00:00Z",
                "status_code": "MANUAL",
                "finding_info": {"uid": "finding-uid", "title": "Rule title",
                                 "attacks": [
                                   {"tactic": {"uid": "TA0009", "name": "Collection"},
                                    "technique": {"uid": "T1119", "name": "Automated Collection"}},
                                   {"technique": {"uid": "T1078"},
                                    "sub_technique": {"uid": "T1078.004"}}]},
                "metadata": {"event_code": "rule-1",
                             "product": {"uid": "prowler", "name": "Prowler",
                                         "version": "5.35.0"}},
                "unmapped": {"additional_urls": ["https://docs.example/a",
                                                 "https://docs.example/b"]},
                "cloud": {"provider": "aws", "region": "eu-central-1",
                          "account": {"uid": "006621354852", "labels": ["team:cloud"]}},
                "resources": [
                  {"uid": "arn:aws:s3:::first", "cloud_partition": "aws",
                   "labels": ["customer:main-ff-oaev"]},
                  {"uid": "arn:aws:s3:::second", "cloud_partition": "aws", "labels": []}
                ]
              }
              """);
      Tag tag = new Tag();
      tag.setId("tag-1");
      when(tagService.findOrCreateTagsFromNames(any(), any())).thenReturn(Set.of(tag));

      // Act
      service.ingest(inject, output, List.of(finding(inject, output, "rule-1")), List.of(source));

      // Assert
      assertThat(occurrences).hasSize(2);
      FindingOccurrence first = occurrences.getFirst();
      assertThat(first.getLocationKey())
          .isEqualTo("aws/006621354852/eu-central-1/arn:aws:s3:::first");
      assertThat(first.getSourceProduct()).isEqualTo("Prowler");
      assertThat(first.getSourceVersion()).isEqualTo("5.35.0");
      assertThat(first.getAdditionalUrls())
          .containsExactly("https://docs.example/a", "https://docs.example/b");
      assertThat(first.getLabels()).containsExactly("team:cloud", "customer:main-ff-oaev");
      assertThat(occurrences.get(1).getLabels()).containsExactly("team:cloud");
      assertThat(first.getAttackPatterns()).containsExactly("T1119", "T1078", "T1078.004");
      assertThat(stable.get().getLifecycle()).isEqualTo(StableFindingLifecycle.REVIEW_REQUIRED);
      assertThat(stable.get().getTags()).containsExactly(tag);
    }

    @Test
    @DisplayName("Should identify a finding by tenant, type, value and location, not by injector")
    void given_identityComponents_should_includeLocationAndTenant() {
      // Arrange
      String first =
          StableFindingIngestionService.stableKey(
              "tenant-a", ContractOutputType.CVE, "CVE-2026-0001", "ASSET", "asset-1");

      // Act
      String same =
          StableFindingIngestionService.stableKey(
              "tenant-a", ContractOutputType.CVE, "CVE-2026-0001", "ASSET", "asset-1");
      String otherLocation =
          StableFindingIngestionService.stableKey(
              "tenant-a", ContractOutputType.CVE, "CVE-2026-0001", "ASSET", "asset-2");
      String otherTenant =
          StableFindingIngestionService.stableKey(
              "tenant-b", ContractOutputType.CVE, "CVE-2026-0001", "ASSET", "asset-1");
      String otherValue =
          StableFindingIngestionService.stableKey(
              "tenant-a", ContractOutputType.CVE, "CVE-2026-0002", "ASSET", "asset-1");

      // Assert
      assertThat(same).isEqualTo(first);
      assertThat(otherLocation).isNotEqualTo(first);
      assertThat(otherTenant).isNotEqualTo(first);
      assertThat(otherValue).isNotEqualTo(first);
    }

    @Test
    @DisplayName("Should merge two injectors finding the same value at the same location")
    void given_twoInjectorsSameLocation_should_keepOneFindingWithBothSources() throws Exception {
      // Arrange
      ContractOutputContext output =
          new ContractOutputContext(
              "cves", "CVEs", ContractOutputType.CVE, true, new String[0], new String[0]);
      Asset asset = new Asset();
      asset.setId("asset-1");
      asset.setName("srv-01");
      Inject nmap = inject("tenant-a", "inject-1", "injector-nmap");
      Inject nuclei = inject("tenant-a", "inject-2", "injector-nuclei");
      Finding byNmap = finding(nmap, output, "CVE-2024-6387");
      byNmap.setAssets(List.of(asset));
      Finding byNuclei = finding(nuclei, output, "CVE-2024-6387");
      byNuclei.setAssets(List.of(asset));
      JsonNode source = objectMapper.readTree("\"CVE-2024-6387\"");

      // Act
      service.ingest(nmap, output, List.of(byNmap), List.of(source));
      service.ingest(nuclei, output, List.of(byNuclei), List.of(source));

      // Assert
      assertThat(stableFindings).hasSize(1);
      assertThat(occurrences)
          .extracting(FindingOccurrence::getSourceInjectorId)
          .containsExactly("injector-nmap", "injector-nuclei");
      assertThat(stable.get().getLocationAsset()).isEqualTo(asset);
      assertThat(stable.get().getLocation()).isEqualTo("srv-01");
    }

    @Test
    @DisplayName("Should derive generic asset and informative occurrences")
    void given_genericFindings_should_deriveLinkedAndInformativeOccurrences() throws Exception {
      // Arrange
      Inject inject = inject("tenant-a", "inject-1", "injector-1");
      ContractOutputContext output =
          new ContractOutputContext(
              "cves", "CVEs", ContractOutputType.CVE, true, new String[0], new String[0]);
      Asset asset = new Asset();
      asset.setId("asset-1");
      asset.setName("server-1");
      Finding localized = finding(inject, output, "CVE-2026-0001");
      localized.setAssets(List.of(asset));
      Finding informative = finding(inject, output, "CVE-2026-0001");
      JsonNode source = objectMapper.readTree("\"CVE-2026-0001\"");

      // Act
      service.ingest(inject, output, List.of(localized, informative), List.of(source, source));

      // Assert
      assertThat(occurrences).hasSize(2);
      assertThat(stableFindings).hasSize(2);
      assertThat(occurrences)
          .extracting(FindingOccurrence::getLocationType)
          .containsExactly(FindingLocationType.ASSET, null);
      assertThat(occurrences.getFirst().getLocationAsset()).isEqualTo(asset);
      assertThat(occurrences)
          .extracting(FindingOccurrence::getTargetRole)
          .containsOnly(FindingTargetRole.EXECUTOR);
      assertThat(occurrences)
          .extracting(FindingOccurrence::getEvidenceScope)
          .containsOnly(FindingEvidenceScope.INDIVIDUAL);
      assertThat(stable.get().getCategory()).isEqualTo(StableFindingCategory.LOCALIZED);
    }
  }

  @Nested
  @DisplayName("Stable category")
  class StableCategory {

    @ParameterizedTest(name = "{0} is {1}")
    @MethodSource("io.openaev.service.finding.StableFindingIngestionServiceTest#categoryCases")
    @DisplayName("Should use the approved contract output type matrix")
    void given_contractOutputType_should_assignApprovedCategory(
        ContractOutputType type, StableFindingCategory expected) {
      // Act
      StableFindingCategory actual = StableFindingIngestionService.category(type);

      // Assert
      assertThat(actual).isEqualTo(expected);
    }
  }

  @Nested
  @DisplayName("Generic host attribution")
  class GenericHostAttribution {

    @ParameterizedTest(name = "{0}")
    @MethodSource("io.openaev.service.finding.StableFindingIngestionServiceTest#hostCases")
    @DisplayName("Should derive target role and evidence scope from host attribution")
    void given_genericHost_should_applyAttributionRules(
        String label,
        String host,
        List<Asset> assets,
        List<FindingTargetRole> expectedRoles,
        FindingEvidenceScope expectedScope)
        throws Exception {
      // Arrange
      Inject inject = inject("tenant-a", "inject-1", "injector-1");
      ContractOutputContext output =
          new ContractOutputContext(
              "cves", "CVEs", ContractOutputType.CVE, true, new String[0], new String[0]);
      Finding finding = finding(inject, output, "CVE-2026-0001");
      finding.setAssets(assets);
      JsonNode source =
          host == null
              ? objectMapper.readTree("{\"id\":\"CVE-2026-0001\"}")
              : objectMapper.readTree("{\"id\":\"CVE-2026-0001\",\"host\":\"%s\"}".formatted(host));

      // Act
      service.ingest(inject, output, List.of(finding), List.of(source));

      // Assert
      assertThat(occurrences)
          .extracting(FindingOccurrence::getTargetRole)
          .containsExactlyInAnyOrderElementsOf(expectedRoles);
      assertThat(occurrences)
          .extracting(FindingOccurrence::getEvidenceScope)
          .containsOnly(expectedScope);
    }
  }

  @Nested
  @DisplayName("Lifecycle")
  class Lifecycle {

    @ParameterizedTest(name = "{0} maps to {1}")
    @MethodSource("io.openaev.service.finding.StableFindingIngestionServiceTest#lifecycleCases")
    @DisplayName("Should map supported OCSF outcomes")
    void given_supportedOutcome_should_mapLifecycle(
        String outcome, StableFindingLifecycle expectedLifecycle) throws Exception {
      // Arrange
      Inject inject = inject("tenant-a", "inject-" + outcome, "injector-1");
      JsonNode source =
          objectMapper.readTree(
              """
              {"status_code":"%s","metadata":{"event_code":"rule-1"},
               "finding_info":{"title":"Rule"}}
              """
                  .formatted(outcome));

      // Act
      service.ingest(
          inject, output(), List.of(finding(inject, output(), "rule-1")), List.of(source));

      // Assert
      assertThat(stable.get().getLifecycle()).isEqualTo(expectedLifecycle);
    }

    @Test
    @DisplayName(
        "Should close the finding on a PASS of the same control and resource, then reopen it")
    void given_passThenFail_should_fixThenReopenFinding() throws Exception {
      // Arrange
      ContractOutputContext output = output();
      Inject firstScan = inject("tenant-a", "inject-1", "injector-1");
      Inject secondScan = inject("tenant-a", "inject-2", "injector-1");
      Inject thirdScan = inject("tenant-a", "inject-3", "injector-1");
      String record =
          """
          {"time_dt": "%s", "status_code": "%s", "finding_info": {"title": "Snapshot"},
           "metadata": {"event_code": "ec2_ebs_snapshots_encrypted", "product": {"uid": "prowler"}},
           "resources": [{"uid": "snap-0a12", "cloud_partition": "aws", "region": "eu-west-1",
                          "account": {"uid": "123456789012"}}]}
          """;
      JsonNode fail = objectMapper.readTree(record.formatted("2026-11-01T08:00:00Z", "FAIL"));
      JsonNode pass = objectMapper.readTree(record.formatted("2026-11-02T08:00:00Z", "PASS"));
      JsonNode failAgain = objectMapper.readTree(record.formatted("2026-11-03T08:00:00Z", "FAIL"));

      // Act
      service.ingest(
          firstScan,
          output,
          List.of(finding(firstScan, output, "ec2_ebs_snapshots_encrypted")),
          List.of(fail));
      service.closeOnPass(
          secondScan,
          output,
          List.of(pass),
          node -> node.path("metadata").path("event_code").asText());
      StableFindingLifecycle afterPass = stable.get().getLifecycle();
      service.ingest(
          thirdScan,
          output,
          List.of(finding(thirdScan, output, "ec2_ebs_snapshots_encrypted")),
          List.of(failAgain));

      // Assert
      assertThat(stableFindings).hasSize(1);
      assertThat(afterPass).isEqualTo(StableFindingLifecycle.FIXED);
      assertThat(occurrences).hasSize(2);
      assertThat(stable.get().getLifecycle()).isEqualTo(StableFindingLifecycle.ACTIVE);
      assertThat(stable.get().getLastSeen()).isEqualTo(Instant.parse("2026-11-03T08:00:00Z"));
    }

    @Test
    @DisplayName("Should retain the lifecycle from the newest observation")
    void given_outOfOrderRedetection_should_useNewestObservation() throws Exception {
      // Arrange
      ContractOutputContext output = output();
      Inject newerInject = inject("tenant-a", "inject-2", "injector-1");
      Inject olderInject = inject("tenant-a", "inject-1", "injector-1");
      JsonNode newer =
          objectMapper.readTree(
              """
              {"time_dt":"2026-09-17T08:00:00Z","status_code":"MUTED",
               "metadata":{"event_code":"rule-1"},"finding_info":{"title":"Rule"}}
              """);
      JsonNode older =
          objectMapper.readTree(
              """
              {"time_dt":"2026-09-17T07:00:00Z","status_code":"FAIL",
               "metadata":{"event_code":"rule-1"},"finding_info":{"title":"Rule"}}
              """);

      // Act
      service.ingest(
          newerInject, output, List.of(finding(newerInject, output, "rule-1")), List.of(newer));
      service.ingest(
          olderInject, output, List.of(finding(olderInject, output, "rule-1")), List.of(older));

      // Assert
      assertThat(occurrences).hasSize(2);
      assertThat(stable.get().getFirstSeen()).isEqualTo(Instant.parse("2026-09-17T07:00:00Z"));
      assertThat(stable.get().getLastSeen()).isEqualTo(Instant.parse("2026-09-17T08:00:00Z"));
      assertThat(stable.get().getLifecycle()).isEqualTo(StableFindingLifecycle.MUTED);
    }
  }

  static Stream<Arguments> lifecycleCases() {
    return Stream.of(
        Arguments.of("FAIL", StableFindingLifecycle.ACTIVE),
        Arguments.of("MANUAL", StableFindingLifecycle.REVIEW_REQUIRED),
        Arguments.of("MUTED", StableFindingLifecycle.MUTED));
  }

  static Stream<Arguments> categoryCases() {
    Set<ContractOutputType> informative =
        Set.of(
            ContractOutputType.Text,
            ContractOutputType.ActionOutput,
            ContractOutputType.Number,
            ContractOutputType.Port,
            ContractOutputType.IPv4,
            ContractOutputType.IPv6,
            ContractOutputType.Email);
    return Stream.of(ContractOutputType.values())
        .map(
            type ->
                Arguments.of(
                    type,
                    informative.contains(type)
                        ? StableFindingCategory.INFORMATIVE
                        : StableFindingCategory.LOCALIZED));
  }

  static Stream<Arguments> hostCases() {
    Asset target = asset("asset-1", "server-1", "server.example", "10.0.0.1", "https://app/x");
    Asset other = asset("asset-2", "server-2", "other.example", "10.0.0.2", null);
    return Stream.of(
        Arguments.of(
            "no host",
            null,
            List.of(target),
            List.of(FindingTargetRole.EXECUTOR),
            FindingEvidenceScope.INDIVIDUAL),
        Arguments.of(
            "matching hostname",
            "SERVER.EXAMPLE",
            List.of(target),
            List.of(FindingTargetRole.TARGET),
            FindingEvidenceScope.INDIVIDUAL),
        Arguments.of(
            "matching URL",
            "https://app/x",
            List.of(target),
            List.of(FindingTargetRole.TARGET),
            FindingEvidenceScope.INDIVIDUAL),
        Arguments.of(
            "different host",
            "elsewhere.example",
            List.of(target),
            List.of(FindingTargetRole.EXECUTOR),
            FindingEvidenceScope.INDIVIDUAL),
        Arguments.of(
            "multiple assets",
            "server.example",
            List.of(target, other),
            List.of(FindingTargetRole.TARGET, FindingTargetRole.EXECUTOR),
            FindingEvidenceScope.GROUP));
  }

  private static Asset asset(String id, String name, String hostname, String ip, String url) {
    Asset asset = new Asset();
    asset.setId(id);
    asset.setName(name);
    asset.setHostname(hostname);
    asset.setIps(ip == null ? null : new String[] {ip});
    asset.setUrl(url);
    return asset;
  }

  private ContractOutputContext output() {
    return new ContractOutputContext(
        "findings", "Findings", ContractOutputType.OCSF, true, new String[0], new String[0]);
  }

  private Inject inject(String tenantId, String injectId, String injectorId) {
    Inject inject = new Inject();
    inject.setId(injectId);
    inject.setTenant(new Tenant(tenantId));
    Injector injector = new Injector();
    injector.setId(injectorId);
    inject.setInjector(injector);
    return inject;
  }

  private Finding finding(Inject inject, ContractOutputContext output, String value) {
    Finding finding = new Finding();
    finding.setInject(inject);
    finding.setTenant(inject.getTenant());
    finding.setField(output.key());
    finding.setName(output.name());
    finding.setType(output.type());
    finding.setValue(value);
    return finding;
  }
}
