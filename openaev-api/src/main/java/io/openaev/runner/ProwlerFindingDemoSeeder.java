package io.openaev.runner;

import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;
import static io.openaev.utils.injector_contract.InjectorContractContentUtils.FIELDS;
import static io.openaev.utils.injector_contract.InjectorContractContentUtils.OUTPUTS;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.ExecutionStatus;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectStatus;
import io.openaev.database.model.Injector;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.database.repository.InjectorRepository;
import io.openaev.rest.inject.form.InjectExecutionAction;
import io.openaev.rest.inject.form.InjectExecutionInput;
import io.openaev.rest.inject.service.InjectExecutionService;
import io.openaev.scheduler.TenantScopedJobRunner;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
@ConditionalOnProperty(prefix = "openaev.dev", name = "seed-prowler-findings", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class ProwlerFindingDemoSeeder implements CommandLineRunner {

  static final String DEMO_INJECTOR_TYPE = "openaev_prowler_demo";
  static final String OUTPUT_KEY = "findings";
  static final int OCCURRENCES_PER_FINDING = 3;
  static final int DEMO_FINDING_COUNT = 4;

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
  private final InjectorRepository injectorRepository;
  private final InjectorContractRepository injectorContractRepository;
  private final InjectRepository injectRepository;
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
    log.info("Prowler finding demo seed complete: {} inject occurrences created", created);
  }

  private Injector ensureInjector() {
    return injectorRepository
        .findByTypeAndTenantId(DEMO_INJECTOR_TYPE, DEFAULT_TENANT_UUID)
        .orElseGet(
            () -> {
              Injector injector = new Injector();
              injector.setId(UUID.randomUUID().toString());
              injector.setTenantId(DEFAULT_TENANT_UUID);
              injector.setName("Prowler");
              injector.setType(DEMO_INJECTOR_TYPE);
              injector.setCategory("misconfiguration_scanner");
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
}
