package io.openaev.runner;

import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;
import static io.openaev.utils.injector_contract.InjectorContractContentUtils.FIELDS;
import static io.openaev.utils.injector_contract.InjectorContractContentUtils.OUTPUTS;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.Asset;
import io.openaev.database.model.AssetCategory;
import io.openaev.database.model.ContractOutputType;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.ExecutionStatus;
import io.openaev.database.model.Finding;
import io.openaev.database.model.FindingTriage;
import io.openaev.database.model.FindingTriageHistory;
import io.openaev.database.model.FindingTriageStatus;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectStatus;
import io.openaev.database.model.Injector;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.AssetRepository;
import io.openaev.database.repository.FindingRepository;
import io.openaev.database.repository.FindingTriageHistoryRepository;
import io.openaev.database.repository.FindingTriageRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.database.repository.InjectorRepository;
import io.openaev.rest.inject.form.InjectExecutionAction;
import io.openaev.rest.inject.form.InjectExecutionInput;
import io.openaev.rest.inject.service.InjectExecutionService;
import io.openaev.scheduler.TenantScopedJobRunner;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Opt-in demo data for development and disposable feature environments.
 *
 * <p>Every sample is submitted through the normal Inject callback pipeline so the demo exercises
 * output validation, legacy Finding compatibility, stable identity aggregation and occurrence
 * persistence. The profile and property gates keep synthetic data out of production deployments.
 */
@Component
@Profile({"dev", "test-feature-branch"})
@ConditionalOnProperty(prefix = "openaev.dev", name = "seed-findings", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class FindingDemoSeeder implements CommandLineRunner {

  static final String DEMO_INJECTOR_TYPE = "openaev_prowler_demo";
  static final String NATIVE_DEMO_INJECTOR_TYPE = "openaev_findings_demo";
  static final String OUTPUT_KEY = "findings";
  static final int OCCURRENCES_PER_FINDING = 3;
  static final int DEMO_FINDING_COUNT = 4;
  static final String PROWLER_EXAMPLES_RESOURCE = "finding-demo/prowler-ocsf-examples.json";

  // Assets carried over from the poc/FindingPage demo seed. Every native record targets at least
  // two of them so the "Also Detected On" tab always has several Locations to show.
  private static final List<DemoAsset> DEMO_ASSETS =
      List.of(
          new DemoAsset("finding-demo:prod-payment-gateway", "Prod Payment Gateway"),
          new DemoAsset("finding-demo:internal-api-server", "Internal API Server"),
          new DemoAsset("finding-demo:staging-web-app", "Staging Web App"),
          new DemoAsset("finding-demo:dev-sandbox-vm", "Dev Sandbox VM"),
          new DemoAsset("finding-demo:unclassified-legacy-host", "Unclassified Legacy Host"));
  private static final int GATEWAY = 0;
  private static final int API_SERVER = 1;
  private static final int WEB_APP = 2;
  private static final int SANDBOX = 3;
  private static final int LEGACY_HOST = 4;

  // One contract output per native finding type (Asset and ExpectationSignature outputs never
  // produce Findings, so they are not part of the demo).
  static final List<NativeOutput> NATIVE_OUTPUTS =
      List.of(
          new NativeOutput("surface", ContractOutputType.PortsScan, "Port scan"),
          new NativeOutput("ports", ContractOutputType.Port, "Open ports"),
          new NativeOutput("ipv4", ContractOutputType.IPv4, "IPv4 addresses"),
          new NativeOutput("ipv6", ContractOutputType.IPv6, "IPv6 addresses"),
          new NativeOutput("identities", ContractOutputType.Username, "Usernames"),
          new NativeOutput("admins", ContractOutputType.AdminUsername, "Admin usernames"),
          new NativeOutput("emails", ContractOutputType.Email, "Emails"),
          new NativeOutput("credentials", ContractOutputType.Credentials, "Credentials"),
          new NativeOutput(
              "no_password",
              ContractOutputType.AccountWithPasswordNotRequired,
              "Accounts without password requirement"),
          new NativeOutput(
              "asreproastable", ContractOutputType.AsreproastableAccount, "AS-REP roastable"),
          new NativeOutput(
              "kerberoastable", ContractOutputType.KerberoastableAccount, "Kerberoastable"),
          new NativeOutput("privileges", ContractOutputType.Group, "Groups"),
          new NativeOutput("delegations", ContractOutputType.Delegation, "Delegations"),
          new NativeOutput("sids", ContractOutputType.Sid, "SIDs"),
          new NativeOutput("computers", ContractOutputType.Computer, "Computers"),
          new NativeOutput("cves", ContractOutputType.CVE, "CVEs"),
          new NativeOutput("weaknesses", ContractOutputType.Vulnerability, "Vulnerabilities"),
          new NativeOutput("shares", ContractOutputType.Share, "Shares"),
          new NativeOutput("files", ContractOutputType.File, "Files"),
          new NativeOutput("posture", ContractOutputType.PasswordPolicy, "Password policy"),
          new NativeOutput("informative", ContractOutputType.Text, "Text"),
          new NativeOutput("metrics", ContractOutputType.Number, "Numbers"),
          new NativeOutput("actions", ContractOutputType.ActionOutput, "Action output"));

  // Triage decisions applied once, on the legacy Findings of the latest scan, so the triage
  // facet and the Activity log are populated out of the box.
  private static final List<DemoTriage> DEMO_TRIAGES =
      List.of(
          new DemoTriage(
              ContractOutputType.Credentials,
              value -> value.startsWith("alice:"),
              FindingTriageStatus.CONFIRMED),
          new DemoTriage(
              ContractOutputType.Vulnerability,
              value -> value.startsWith("CVE-2021-44228"),
              FindingTriageStatus.CONFIRMED),
          new DemoTriage(
              ContractOutputType.Share,
              value -> value.contains("\\Public "),
              FindingTriageStatus.FALSE_POSITIVE),
          new DemoTriage(
              ContractOutputType.PortsScan,
              value -> value.contains(":3389"),
              FindingTriageStatus.RISK_ACCEPTED),
          new DemoTriage(
              ContractOutputType.OCSF,
              "s3_bucket_public_read_access"::equals,
              FindingTriageStatus.CONFIRMED),
          new DemoTriage(
              ContractOutputType.OCSF,
              "storage_blob_public_access_level_is_disabled"::equals,
              FindingTriageStatus.RISK_ACCEPTED),
          new DemoTriage(
              ContractOutputType.OCSF,
              "apiserver_always_pull_images_plugin"::equals,
              FindingTriageStatus.FALSE_POSITIVE));

  private static final String ACCOUNT_ID = "123456789012";
  private static final String REGION = "eu-west-1";
  private static final List<Instant> SCAN_TIMES =
      List.of(
          Instant.parse("2026-08-20T09:15:00Z"),
          Instant.parse("2026-09-03T09:15:00Z"),
          Instant.parse("2026-09-17T09:15:00Z"));
  private static final List<String> SCAN_IDS =
      List.of(
          "prowler-demo-scan-20260820", "prowler-demo-scan-20260903", "prowler-demo-scan-20260917");
  private static final List<DemoFinding> FINDINGS =
      List.of(
          new DemoFinding(
              "s3_bucket_public_access",
              "S3 bucket allows public access",
              "The production-assets bucket permits public access through its bucket policy.",
              "Critical",
              5,
              "arn:aws:s3:::production-assets",
              "production-assets",
              "AWS::S3::Bucket",
              "Amazon S3",
              "Public data exposure can disclose sensitive objects to unauthenticated users.",
              List.of("Cloud Security", "Data Protection", "Public Exposure"),
              List.of("T1530"),
              Map.of(
                  "CIS-AWS-Foundations-2.0",
                  List.of("2.1.5"),
                  "AWS-Foundational-Security-Best-Practices",
                  List.of("S3.2")),
              "Enable S3 Block Public Access and remove public principals from the bucket policy.",
              "https://docs.aws.amazon.com/AmazonS3/latest/userguide/access-control-block-public-access.html"),
          new DemoFinding(
              "s3_bucket_public_access",
              "S3 bucket allows public access",
              "The public-assets bucket contains intentionally published marketing material.",
              "Critical",
              5,
              "arn:aws:s3:::public-assets",
              "public-assets",
              "AWS::S3::Bucket",
              "Amazon S3",
              "Public access is intentional for this location, but policy drift still requires"
                  + " review.",
              List.of("Cloud Security", "Data Protection", "Public Exposure"),
              List.of("T1530"),
              Map.of(
                  "CIS-AWS-Foundations-2.0",
                  List.of("2.1.5"),
                  "AWS-Foundational-Security-Best-Practices",
                  List.of("S3.2")),
              "Confirm the public-data exception and restrict write access to approved principals.",
              "https://docs.aws.amazon.com/AmazonS3/latest/userguide/access-control-block-public-access.html"),
          new DemoFinding(
              "iam_user_console_access_no_mfa",
              "IAM console user does not have MFA enabled",
              "The console-enabled IAM user deploy-operator has no active MFA device.",
              "High",
              4,
              "arn:aws:iam::123456789012:user/deploy-operator",
              "deploy-operator",
              "AWS::IAM::User",
              "AWS Identity and Access Management",
              "Compromised credentials can be used interactively without a second authentication"
                  + " factor.",
              List.of("Cloud Security", "Identity and Access Management"),
              List.of("T1078.004"),
              Map.of(
                  "CIS-AWS-Foundations-2.0",
                  List.of("1.10"),
                  "NIST-800-53-Rev5",
                  List.of("IA-2(1)")),
              "Enroll a virtual or hardware MFA device and require MFA for console sessions.",
              "https://docs.aws.amazon.com/IAM/latest/UserGuide/id_credentials_mfa_enable_virtual.html"),
          new DemoFinding(
              "ec2_securitygroup_allow_ingress_from_internet_to_tcp_port_22",
              "EC2 security group allows SSH from the Internet",
              "The bastion-host security group permits TCP port 22 from 0.0.0.0/0.",
              "High",
              4,
              "arn:aws:ec2:eu-west-1:123456789012:security-group/sg-0a1b2c3d4e5f67890",
              "bastion-host",
              "AWS::EC2::SecurityGroup",
              "Amazon EC2",
              "Internet-wide SSH exposure increases brute-force and remote access risk.",
              List.of("Cloud Security", "Network Security", "Public Exposure"),
              List.of("T1021.004"),
              Map.of(
                  "CIS-AWS-Foundations-2.0", List.of("5.2"), "NIST-800-53-Rev5", List.of("AC-4")),
              "Restrict port 22 ingress to approved administration networks or use AWS Systems"
                  + " Manager Session Manager.",
              "https://docs.aws.amazon.com/systems-manager/latest/userguide/session-manager.html"));

  private final ObjectMapper objectMapper;
  private final AssetRepository assetRepository;
  private final InjectorRepository injectorRepository;
  private final InjectorContractRepository injectorContractRepository;
  private final InjectRepository injectRepository;
  private final FindingRepository findingRepository;
  private final FindingTriageRepository findingTriageRepository;
  private final FindingTriageHistoryRepository findingTriageHistoryRepository;
  private final InjectExecutionService injectExecutionService;
  private final TenantScopedJobRunner tenantScopedJobRunner;

  @Override
  public void run(String... args) {
    tenantScopedJobRunner.runInTenant(DEFAULT_TENANT_UUID, this::seed);
  }

  private void seed() {
    Injector injector = ensureInjector();
    InjectorContract contract = ensureContract(injector);

    int created = 0;
    for (int scanIndex = 0; scanIndex < SCAN_IDS.size(); scanIndex++) {
      for (int findingIndex = 0; findingIndex < FINDINGS.size(); findingIndex++) {
        String injectTitle = injectTitle(scanIndex, findingIndex);
        if (injectRepository.existsByTitleAndTenantId(injectTitle, DEFAULT_TENANT_UUID)) {
          continue;
        }
        Inject inject = createInject(injectTitle, injector, contract, scanIndex);
        injectRepository.save(inject);
        injectExecutionService.handleInjectExecutionCallback(
            inject.getId(), null, callback(scanIndex, findingIndex));
        created++;
      }
    }
    List<Inject> latestScanInjects = new ArrayList<>();
    created += seedProwlerExamples(injector, contract, latestScanInjects);
    created += seedNativeFindings(latestScanInjects);
    seedTriages(latestScanInjects);
    log.info("Finding demo seed complete: {} inject occurrences created", created);
  }

  /**
   * Replays the user-provided Prowler examples (see {@link #PROWLER_EXAMPLES_RESOURCE}) once per
   * scan. Records sharing a check id are spread over separate callbacks: the legacy Finding layer
   * merges same-value records of a single callback, and each resource must stay its own Location.
   */
  private int seedProwlerExamples(
      Injector injector, InjectorContract contract, List<Inject> latestScanInjects) {
    List<List<JsonNode>> batches = exampleBatches();
    int created = 0;
    for (int scanIndex = 0; scanIndex < SCAN_IDS.size(); scanIndex++) {
      for (int batchIndex = 0; batchIndex < batches.size(); batchIndex++) {
        String injectTitle =
            "Prowler examples scan %s (batch %d)"
                .formatted(SCAN_IDS.get(scanIndex), batchIndex + 1);
        if (injectRepository.existsByTitleAndTenantId(injectTitle, DEFAULT_TENANT_UUID)) {
          continue;
        }
        Inject inject = createInject(injectTitle, injector, contract, scanIndex);
        injectRepository.save(inject);
        ArrayNode records = objectMapper.createArrayNode();
        for (JsonNode example : batches.get(batchIndex)) {
          records.add(examplePayload(example, scanIndex));
        }
        injectExecutionService.handleInjectExecutionCallback(
            inject.getId(), null, structuredCallback("Prowler scan completed", records));
        if (scanIndex == SCAN_IDS.size() - 1) {
          latestScanInjects.add(inject);
        }
        created++;
      }
    }
    return created;
  }

  List<List<JsonNode>> exampleBatches() {
    Map<String, List<JsonNode>> byCheck = new LinkedHashMap<>();
    try (InputStream stream = new ClassPathResource(PROWLER_EXAMPLES_RESOURCE).getInputStream()) {
      for (JsonNode example : objectMapper.readTree(stream)) {
        byCheck
            .computeIfAbsent(
                example.path("metadata").path("event_code").asText(), key -> new ArrayList<>())
            .add(example);
      }
    } catch (IOException exception) {
      throw new UncheckedIOException("Cannot read " + PROWLER_EXAMPLES_RESOURCE, exception);
    }
    List<List<JsonNode>> batches = new ArrayList<>();
    byCheck
        .values()
        .forEach(
            records -> {
              for (int index = 0; index < records.size(); index++) {
                if (batches.size() <= index) {
                  batches.add(new ArrayList<>());
                }
                batches.get(index).add(records.get(index));
              }
            });
    return batches;
  }

  private ObjectNode examplePayload(JsonNode example, int scanIndex) {
    ObjectNode payload = example.deepCopy();
    Instant observedAt = SCAN_TIMES.get(scanIndex);
    payload.put("time", observedAt.toEpochMilli());
    payload.put("time_dt", observedAt.toString());
    ((ObjectNode) payload.path("metadata")).put("uid", SCAN_IDS.get(scanIndex));
    ObjectNode findingInfo = (ObjectNode) payload.path("finding_info");
    findingInfo.put("uid", findingInfo.path("uid").asText() + ":" + SCAN_IDS.get(scanIndex));
    return payload;
  }

  private InjectExecutionInput structuredCallback(String message, ArrayNode records) {
    InjectExecutionInput input = new InjectExecutionInput();
    input.setMessage(message);
    input.setStatus("INFO");
    input.setAction(InjectExecutionAction.complete);
    input.setDuration(45_000);
    ObjectNode structuredOutput = objectMapper.createObjectNode();
    structuredOutput.set(OUTPUT_KEY, records);
    input.setOutputStructured(structuredOutput.toString());
    return input;
  }

  /** Applies {@link #DEMO_TRIAGES} as System decisions, only on Findings not triaged yet. */
  private void seedTriages(List<Inject> latestScanInjects) {
    for (Inject inject : latestScanInjects) {
      for (Finding finding :
          findingRepository.findAllByInjectIdAndTenantId(inject.getId(), DEFAULT_TENANT_UUID)) {
        DEMO_TRIAGES.stream()
            .filter(
                triage ->
                    triage.type() == finding.getType() && triage.value().test(finding.getValue()))
            .findFirst()
            .ifPresent(triage -> applyTriage(finding, triage.status()));
      }
    }
  }

  private void applyTriage(Finding finding, FindingTriageStatus status) {
    if (findingTriageRepository.findByFinding_Id(finding.getId()).isPresent()) {
      return;
    }
    // Risk acceptance is only reachable from Confirmed, so its history shows both steps.
    List<FindingTriageStatus> path =
        status == FindingTriageStatus.RISK_ACCEPTED
            ? List.of(FindingTriageStatus.CONFIRMED, FindingTriageStatus.RISK_ACCEPTED)
            : List.of(status);
    FindingTriageStatus from = FindingTriageStatus.UNTRIAGED;
    for (FindingTriageStatus to : path) {
      FindingTriageHistory history = new FindingTriageHistory();
      history.setFinding(finding);
      history.setFromStatus(from);
      history.setToStatus(to);
      history.setJustification("Demo triage decision seeded for the Findings PoC.");
      history.setTenant(new Tenant(DEFAULT_TENANT_UUID));
      findingTriageHistoryRepository.save(history);
      from = to;
    }
    FindingTriage triage = new FindingTriage();
    triage.setFinding(finding);
    triage.setStatus(status);
    triage.setTenant(new Tenant(DEFAULT_TENANT_UUID));
    findingTriageRepository.save(triage);
  }

  private Injector ensureInjector() {
    return ensureInjector(DEMO_INJECTOR_TYPE, "Prowler", "misconfiguration_scanner");
  }

  private Injector ensureInjector(String type, String name, String category) {
    return injectorRepository
        .findByTypeAndTenantId(type, DEFAULT_TENANT_UUID)
        .orElseGet(
            () -> {
              Injector injector = new Injector();
              injector.setId(UUID.randomUUID().toString());
              injector.setTenantId(DEFAULT_TENANT_UUID);
              injector.setName(name);
              injector.setType(type);
              injector.setCategory(category);
              return injectorRepository.save(injector);
            });
  }

  private InjectorContract ensureContract(Injector injector) {
    InjectorContract contract =
        injectorContractRepository.findByInjectorsContaining(injector).stream()
            .findFirst()
            .orElseGet(
                () -> {
                  InjectorContract created = new InjectorContract();
                  created.setId(UUID.randomUUID().toString());
                  created.setTenant(new Tenant(DEFAULT_TENANT_UUID));
                  created.setLabels(Map.of("en", "Prowler OCSF scan"));
                  created.setManual(false);
                  created.setCustom(false);
                  created.setNeedsExecutor(false);
                  created.setAtomicTesting(true);
                  created.setPlatforms(
                      new Endpoint.PLATFORM_TYPE[] {Endpoint.PLATFORM_TYPE.Generic});
                  created.addInjector(injector);
                  return created;
                });

    ObjectNode content = objectMapper.createObjectNode();
    content.putArray(FIELDS);
    ObjectNode output = content.putArray(OUTPUTS).addObject();
    output.put("type", "ocsf");
    output.put("field", OUTPUT_KEY);
    output.putArray("labels").add("Prowler findings");
    output.put("isMultiple", true);
    output.put("isFindingCompatible", true);
    contract.setContent(content.toString());
    contract.setConvertedContent(content);
    InjectorContract saved = injectorContractRepository.save(contract);
    injectorRepository.linkContract(injector.getId(), saved.getId(), DEFAULT_TENANT_UUID);
    return saved;
  }

  /**
   * Seeds every native finding type once per scan, so each Finding gets a three-entry Timeline, and
   * on at least two Assets, so its "Also Detected On" tab lists several Locations.
   */
  private int seedNativeFindings(List<Inject> latestScanInjects) {
    List<Asset> assets = DEMO_ASSETS.stream().map(this::ensureAsset).toList();
    Injector injector =
        ensureInjector(NATIVE_DEMO_INJECTOR_TYPE, "OpenAEV Demo Scanner", "security_scanner");
    InjectorContract contract = ensureNativeContract(injector);
    int created = 0;
    for (int scanIndex = 0; scanIndex < SCAN_IDS.size(); scanIndex++) {
      String injectTitle = nativeInjectTitle(scanIndex);
      if (injectRepository.existsByTitleAndTenantId(injectTitle, DEFAULT_TENANT_UUID)) {
        continue;
      }
      Inject inject = createInject(injectTitle, injector, contract, scanIndex);
      injectRepository.save(inject);
      injectExecutionService.handleInjectExecutionCallback(
          inject.getId(), null, nativeCallback(assets, scanIndex));
      if (scanIndex == SCAN_IDS.size() - 1) {
        latestScanInjects.add(inject);
      }
      created++;
    }
    return created;
  }

  static String nativeInjectTitle(int scanIndex) {
    return "OpenAEV findings demo scan %s".formatted(SCAN_IDS.get(scanIndex));
  }

  private Asset ensureAsset(DemoAsset demoAsset) {
    return assetRepository
        .findByExternalReferenceAndTenantId(demoAsset.externalReference(), DEFAULT_TENANT_UUID)
        .orElseGet(
            () -> {
              Asset asset = new Asset();
              asset.setName(demoAsset.name());
              asset.setDescription("Synthetic asset used only by the opt-in Findings demo seed.");
              asset.setExternalReference(demoAsset.externalReference());
              asset.setCategory(AssetCategory.GENERIC_ASSET);
              asset.setTenant(new Tenant(DEFAULT_TENANT_UUID));
              return assetRepository.save(asset);
            });
  }

  private InjectorContract ensureNativeContract(Injector injector) {
    InjectorContract contract =
        injectorContractRepository.findByInjectorsContaining(injector).stream()
            .findFirst()
            .orElseGet(
                () -> {
                  InjectorContract created = new InjectorContract();
                  created.setId(UUID.randomUUID().toString());
                  created.setTenant(new Tenant(DEFAULT_TENANT_UUID));
                  created.setLabels(Map.of("en", "Categorized findings demo"));
                  created.setManual(false);
                  created.setCustom(false);
                  created.setNeedsExecutor(false);
                  created.setAtomicTesting(true);
                  created.setPlatforms(
                      new Endpoint.PLATFORM_TYPE[] {Endpoint.PLATFORM_TYPE.Generic});
                  created.addInjector(injector);
                  return created;
                });

    ObjectNode content = objectMapper.createObjectNode();
    content.putArray(FIELDS);
    ArrayNode outputs = content.putArray(OUTPUTS);
    NATIVE_OUTPUTS.forEach(output -> addOutput(outputs, output));
    contract.setContent(content.toString());
    contract.setConvertedContent(content);
    InjectorContract saved = injectorContractRepository.save(contract);
    injectorRepository.linkContract(injector.getId(), saved.getId(), DEFAULT_TENANT_UUID);
    return saved;
  }

  private void addOutput(ArrayNode outputs, NativeOutput nativeOutput) {
    ObjectNode output = outputs.addObject();
    output.put("type", nativeOutput.type().getLabel());
    output.put("field", nativeOutput.field());
    output.putArray("labels").add(nativeOutput.label());
    output.put("isMultiple", true);
    output.put("isFindingCompatible", true);
  }

  private Inject createInject(
      String injectTitle, Injector injector, InjectorContract contract, int scanIndex) {
    Inject inject = new Inject();
    inject.setTitle(injectTitle);
    inject.setDescription("DEV demo occurrence from " + SCAN_IDS.get(scanIndex));
    inject.setInjector(injector);
    inject.setInjectorContract(contract);
    inject.setTenant(new Tenant(DEFAULT_TENANT_UUID));
    inject.setDependsDuration(0L);
    inject.setContent(objectMapper.createObjectNode());
    InjectStatus status = new InjectStatus();
    status.setInject(inject);
    status.setName(ExecutionStatus.PENDING);
    status.setTrackingSentDate(SCAN_TIMES.get(scanIndex).minusSeconds(45));
    inject.setStatus(status);
    return inject;
  }

  private InjectExecutionInput callback(int scanIndex, int findingIndex) {
    InjectExecutionInput input = new InjectExecutionInput();
    input.setMessage("Prowler scan completed");
    input.setStatus("INFO");
    input.setAction(InjectExecutionAction.complete);
    input.setDuration(45_000);
    ObjectNode structuredOutput = objectMapper.createObjectNode();
    structuredOutput.set(
        OUTPUT_KEY, objectMapper.createArrayNode().add(payload(scanIndex, findingIndex)));
    input.setOutputStructured(structuredOutput.toString());
    return input;
  }

  private InjectExecutionInput nativeCallback(List<Asset> assets, int scanIndex) {
    InjectExecutionInput input = new InjectExecutionInput();
    input.setMessage("Categorized finding demo completed");
    input.setStatus("INFO");
    input.setAction(InjectExecutionAction.complete);
    input.setDuration(15_000);
    input.setOutputStructured(nativeOutput(assets, scanIndex).toString());
    return input;
  }

  ObjectNode nativeOutput(List<Asset> assets, int scanIndex) {
    Instant at = SCAN_TIMES.get(scanIndex);
    NativeRecords records = new NativeRecords(objectMapper.createObjectNode(), assets, at);
    boolean secondScan = scanIndex >= 1;
    boolean lastScan = scanIndex == SCAN_IDS.size() - 1;

    // PortScanOutputProcessor only reads a single asset_id: each scan reports the port on a
    // different Asset instead, so the Locations still add up across the Timeline.
    int alternate = scanIndex % 2;
    records
        .addSingle("surface", alternate == 0 ? GATEWAY : WEB_APP)
        .put("host", "198.51.100.10")
        .put("port", "443")
        .put("service", "https");
    records
        .addSingle("surface", alternate == 0 ? GATEWAY : API_SERVER)
        .put("host", "198.51.100.10")
        .put("port", "22")
        .put("service", "ssh");
    records
        .addSingle("surface", alternate == 0 ? LEGACY_HOST : SANDBOX)
        .put("host", "10.20.0.15")
        .put("port", "3389")
        .put("service", "rdp");
    records.primitives("ports", "22", "443", "3389");
    records.primitives("ipv4", "198.51.100.10", "10.20.0.15");
    records.primitives("ipv6", "2001:db8::10", "fe80::1ff:fe23:4567:890a");

    records
        .add("identities", API_SERVER, LEGACY_HOST)
        .put("domain", "DEMO")
        .put("username", "alice");
    records
        .add("identities", API_SERVER, SANDBOX)
        .put("domain", "DEMO")
        .put("username", "backup-operator");
    records.add("admins", API_SERVER, LEGACY_HOST).put("username", "Administrator");
    records.add("admins", API_SERVER, GATEWAY).put("username", "da-maintenance");
    records.add("emails", API_SERVER, WEB_APP).put("email", "alice@demo.example");
    records.add("emails", API_SERVER, SANDBOX).put("email", "it-support@demo.example");

    records
        .add("credentials", API_SERVER, LEGACY_HOST)
        .put("username", "alice")
        .put("hash", "DEMO_HASH_ALICE");
    records
        .add("credentials", API_SERVER, SANDBOX)
        .put("username", "backup-operator")
        .put("hash", "DEMO_HASH_BACKUP");
    records
        .add("credentials", LEGACY_HOST, SANDBOX)
        .put("username", "service-deploy")
        .put("password", "Summer2026!");
    records
        .add("no_password", API_SERVER, LEGACY_HOST)
        .put("account", "guest-kiosk")
        .put("status", "enabled");
    records.add("asreproastable", API_SERVER, LEGACY_HOST).put("username", "legacy-svc");
    if (secondScan) {
      // First detected on the second scan: its Timeline is shorter than the others.
      records.add("kerberoastable", API_SERVER, SANDBOX).put("username", "svc-sql");
    }
    records.add("kerberoastable", API_SERVER, LEGACY_HOST).put("username", "svc-backup");

    records
        .add("privileges", API_SERVER, LEGACY_HOST)
        .put("group_name", "Domain Admins")
        .put("member_count", "5");
    records
        .add("privileges", API_SERVER, SANDBOX)
        .put("group_name", "Backup Operators")
        .put("member_count", "12");
    records
        .add("delegations", API_SERVER, WEB_APP)
        .put("account", "svc-web")
        .put("delegation_type", "Unconstrained");
    records
        .add("delegations", API_SERVER, LEGACY_HOST)
        .put("account", "svc-sql")
        .put("delegation_type", "Constrained")
        .put("rights_to", "MSSQLSvc/db01.demo.local");
    records
        .add("sids", API_SERVER, LEGACY_HOST)
        .put("sid", "S-1-5-21-1004336348-1177238915-682003330-500");
    records.add("computers", API_SERVER, LEGACY_HOST).put("computer_name", "SRV-LEGACY-01");
    records.add("computers", API_SERVER, SANDBOX).put("computer_name", "WS-SANDBOX-07");

    records
        .add("cves", GATEWAY, WEB_APP)
        .put("id", "CVE-2023-44487")
        .put("host", "198.51.100.10")
        .put("severity", "high");
    if (lastScan) {
      // Only seen on the latest scan: a brand-new Finding.
      records
          .add("cves", GATEWAY, API_SERVER)
          .put("id", "CVE-2024-3400")
          .put("host", "198.51.100.10")
          .put("severity", "critical");
    }
    records
        .add("weaknesses", GATEWAY, API_SERVER)
        .put("name", "CVE-2024-6387")
        .put("status", "vulnerable")
        .put("details", "OpenSSH regreSSHion exposure");
    records
        .add("weaknesses", LEGACY_HOST, SANDBOX)
        .put("name", "CVE-2021-44228")
        .put("status", "vulnerable")
        .put("details", "Log4Shell-compatible component detected");

    records
        .add("shares", LEGACY_HOST, API_SERVER)
        .put("host", "files.demo.internal")
        .put("share_name", "Finance")
        .put("permissions", "READ");
    records
        .add("shares", LEGACY_HOST, SANDBOX)
        .put("host", "files.demo.internal")
        .put("share_name", "Public")
        .put("permissions", "ANONYMOUS_READ");
    records
        .add("files", LEGACY_HOST, API_SERVER)
        .put("host", "files.demo.internal")
        .put("share", "Finance")
        .put("file_name", "payroll-2026.xlsx")
        .put("path", "HR/payroll-2026.xlsx");
    records
        .add("files", WEB_APP, SANDBOX)
        .put("file_name", ".env")
        .put("path", "/var/www/app/.env");

    records
        .add("posture", API_SERVER, LEGACY_HOST)
        .put("key", "MinimumPasswordLength")
        .put("value", "8");
    records.add("posture", API_SERVER, SANDBOX).put("key", "LockoutThreshold").put("value", "0");

    records.primitives(
        "informative",
        "Synthetic reconnaissance completed",
        "Demo environment contains no production data");
    records.primitives("metrics", "42", "1337");
    records.primitives("actions", "whoami /all executed successfully");
    return records.output();
  }

  /** Builds the structured output of one native scan, stamping every record with its time. */
  private record NativeRecords(ObjectNode output, List<Asset> assets, Instant observedAt) {

    ObjectNode add(String field, int... assetIndexes) {
      ArrayNode array = output.has(field) ? (ArrayNode) output.get(field) : output.putArray(field);
      ObjectNode record = array.addObject();
      ArrayNode assetIds = record.putArray("asset_id");
      for (int assetIndex : assetIndexes) {
        assetIds.add(assets.get(assetIndex).getId());
      }
      record.put("time_dt", observedAt.toString());
      return record;
    }

    ObjectNode addSingle(String field, int assetIndex) {
      ArrayNode array = output.has(field) ? (ArrayNode) output.get(field) : output.putArray(field);
      ObjectNode record = array.addObject();
      record.put("asset_id", assets.get(assetIndex).getId());
      record.put("time_dt", observedAt.toString());
      return record;
    }

    void primitives(String field, String... values) {
      ArrayNode array = output.putArray(field);
      for (String value : values) {
        array.add(value);
      }
    }
  }

  private ObjectNode payload(int scanIndex, int findingIndex) {
    DemoFinding finding = FINDINGS.get(findingIndex);
    Instant observedAt = SCAN_TIMES.get(scanIndex);
    String scanId = SCAN_IDS.get(scanIndex);
    ObjectNode payload = objectMapper.createObjectNode();
    payload.put("time", observedAt.toEpochMilli());
    payload.put("time_dt", observedAt.toString());
    payload.put("severity", finding.severity());
    payload.put("severity_id", finding.severityId());
    payload.put("status_code", "FAIL");
    payload.put("status_detail", "The check failed against the scanned AWS resource.");
    payload.put("message", finding.description());
    payload.put("category_name", "Detection Finding");
    payload.put("class_name", "Detection Finding");
    payload.put("activity_name", "Create");

    ObjectNode metadata = payload.putObject("metadata");
    metadata.put("uid", scanId);
    metadata.put("event_code", finding.eventCode());
    metadata.putObject("product").put("uid", "prowler").put("name", "Prowler");

    ObjectNode findingInfo = payload.putObject("finding_info");
    findingInfo.put("uid", finding.eventCode() + ":" + scanId);
    findingInfo.put("title", finding.title());
    findingInfo.put("desc", finding.description());
    findingInfo.set("types", objectMapper.valueToTree(finding.categories()));

    ObjectNode resource = payload.putArray("resources").addObject();
    resource.put("uid", finding.resourceUid());
    resource.put("name", finding.resourceName());
    resource.put("type", finding.resourceType());
    resource.put("cloud_partition", "aws");
    resource.put("region", REGION);
    resource.putObject("group").put("name", finding.resourceGroup());
    resource.putObject("data").putObject("metadata").put("arn", finding.resourceUid());

    ObjectNode cloud = payload.putObject("cloud");
    cloud.putObject("account").put("uid", ACCOUNT_ID);
    cloud.put("region", REGION);
    cloud.put("provider", "aws");

    payload.putObject("risk_details").put("description", finding.risk());
    ObjectNode unmapped = payload.putObject("unmapped");
    unmapped.set("categories", objectMapper.valueToTree(finding.categories()));
    ObjectNode compliance = unmapped.putObject("compliance");
    finding
        .compliance()
        .forEach(
            (framework, controls) -> compliance.set(framework, objectMapper.valueToTree(controls)));
    compliance.set("MITRE-ATTACK", objectMapper.valueToTree(finding.attackPatterns()));

    ObjectNode remediation = payload.putObject("remediation");
    remediation.put("desc", finding.remediation());
    remediation.set(
        "references", objectMapper.valueToTree(List.of(finding.remediationReference())));
    return payload;
  }

  static String injectTitle(int scanIndex, int findingIndex) {
    DemoFinding finding = FINDINGS.get(findingIndex);
    return "Prowler scan %s: %s (%s)"
        .formatted(SCAN_IDS.get(scanIndex), finding.title(), finding.resourceName());
  }

  private record DemoFinding(
      String eventCode,
      String title,
      String description,
      String severity,
      int severityId,
      String resourceUid,
      String resourceName,
      String resourceType,
      String resourceGroup,
      String risk,
      List<String> categories,
      List<String> attackPatterns,
      Map<String, List<String>> compliance,
      String remediation,
      String remediationReference) {}

  private record DemoAsset(String externalReference, String name) {}

  record NativeOutput(String field, ContractOutputType type, String label) {}

  private record DemoTriage(
      ContractOutputType type, Predicate<String> value, FindingTriageStatus status) {}
}
