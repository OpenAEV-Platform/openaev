package io.openaev.api.detection_remediation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.api.detection_remediation.dto.PayloadInput;
import io.openaev.authorisation.HttpClientFactory;
import io.openaev.collectors.utils.CollectorsUtils;
import io.openaev.database.model.*;
import io.openaev.ee.Ee;
import io.openaev.injector_contract.fields.ContractFieldType;
import io.openaev.rest.payload.form.DetectionRemediationInput;
import io.openaev.utils.fixtures.*;
import io.openaev.utils.fixtures.composers.*;
import io.openaev.utils.fixtures.files.AttackPatternFixture;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.annotation.Resource;
import jakarta.persistence.EntityManager;
import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Get detection and remediation rule using AI")
public class DetectionRemediationApiTest extends IntegrationTest {

  @MockBean private Ee enterpriseEdition;

  @MockBean private CloseableHttpClient httpClient;

  @MockBean private HttpClientFactory httpClientFactory;

  @Autowired private MockMvc mockMvc;

  @Autowired private InjectorFixture injectorFixture;

  @Autowired private InjectorContractComposer injectorContractComposer;

  @Autowired private InjectComposer injectComposer;

  @Autowired private PayloadComposer payloadComposer;

  @Autowired private DetectionRemediationComposer detectionRemediationComposer;

  @Autowired private DocumentComposer documentComposer;

  @Autowired private CollectorComposer collectorComposer;

  @Autowired private AttackPatternComposer attackPatternComposer;

  @Autowired private DomainComposer domainComposer;

  @Autowired private EntityManager entityManager;

  @Resource protected ObjectMapper mapper;

  private static final String CROWDSTRIKE_FRONTEND_NAME = "openaev_crowdstrike";
  private static final String SPLUNK_FRONTEND_NAME = "openaev_splunk_es";

  // -- TEST API : POST api/detection-remediations/ai/rules/{collectorType} --

  @Test
  @DisplayName("Generate AI rules detection remediation by payload , EE not available")
  public void getDetectionRemediationRuleByPayloadWithoutLicenceEE() {
    // -- PREPARE -
    Set<Domain> domains = domainComposer.forDomain(null).persist().getSet();

    Command payload =
        (Command) payloadComposer.forPayload(PayloadFixture.createDefaultCommand(domains)).get();

    List<String> attackPatternsIds =
        payload.getAttackPatterns().stream().map(AttackPattern::getId).toList();
    PayloadInput input = payloadComposer.forPayloadInput(payload, attackPatternsIds);

    when(httpClientFactory.httpClientCustom()).thenReturn(httpClient);
    when(enterpriseEdition.getEncodedCertificate()).thenCallRealMethod();

    // -- EXECUTE --
    assertThatThrownBy(
            () ->
                mockMvc.perform(
                    post("/"
                            + DetectionRemediationApi.DETECTION_REMEDIATION_URI
                            + "/rules/"
                            + CROWDSTRIKE_FRONTEND_NAME)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(input))))
        .hasCauseInstanceOf(IllegalStateException.class)
        .hasMessage(
            "Request processing failed: java.lang.IllegalStateException: Enterprise Edition is not available");
  }

  @Test
  @DisplayName("Generate AI rules detection remediation by payload for unknow collector type")
  public void getDetectionRemediationRuleByPayloadForUnknowCollectorType() {
    // -- PREPARE -
    Set<Domain> domains = domainComposer.forDomain(null).persist().getSet();

    Command payload =
        (Command) payloadComposer.forPayload(PayloadFixture.createDefaultCommand(domains)).get();

    List<String> attackPatternsIds =
        payload.getAttackPatterns().stream().map(AttackPattern::getId).toList();
    PayloadInput input = payloadComposer.forPayloadInput(payload, attackPatternsIds);

    // -- EXECUTE --
    assertThatThrownBy(
            () ->
                mockMvc.perform(
                    post("/"
                            + DetectionRemediationApi.DETECTION_REMEDIATION_URI
                            + "/rules/collector_name_unknow")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(input))))
        .hasCauseInstanceOf(IllegalStateException.class)
        .hasMessage(
            "Request processing failed: java.lang.IllegalStateException: Collector :\"collector_name_unknow\" unsupported");
  }

  @Test
  @DisplayName(
      "Generate AI rules detection remediation by payload with bad structured response webservice")
  public void getDetectionRemediationRuleByPayloadWithBadDetectionRemediationAIResponse()
      throws Exception {
    // -- PREPARE -
    Set<Domain> domains = domainComposer.forDomain(null).persist().getSet();

    Command payload =
        (Command) payloadComposer.forPayload(PayloadFixture.createDefaultCommand(domains)).get();

    List<String> attackPatternsIds =
        payload.getAttackPatterns().stream().map(AttackPattern::getId).toList();
    PayloadInput input = payloadComposer.forPayloadInput(payload, attackPatternsIds);

    // -- MOCKING EXTERNAL WEBSERVICE CALL --
    String detectionRemediationAIResponse = getBadDetectionRemediationAIResponse();
    when(httpClientFactory.httpClientCustom()).thenReturn(httpClient);
    when(httpClient.execute(
            Mockito.any(ClassicHttpRequest.class), Mockito.any(HttpClientResponseHandler.class)))
        .thenAnswer(inv -> detectionRemediationAIResponse);

    // -- EXECUTE --
    mockMvc
        .perform(
            post("/"
                    + DetectionRemediationApi.DETECTION_REMEDIATION_URI
                    + "/rules/"
                    + CROWDSTRIKE_FRONTEND_NAME)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(input)))
        .andExpect(status().isBadGateway());
  }

  @Test
  @DisplayName(
      "Generate AI rules detection remediation by payload with retry until service unavailable")
  public void
      getDetectionRemediationRuleByPayloadWithRetryDetectionRemediationAIResponseUnavailable()
          throws Exception {
    // -- PREPARE -
    Set<Domain> domains = domainComposer.forDomain(null).persist().getSet();

    Command payload =
        (Command) payloadComposer.forPayload(PayloadFixture.createDefaultCommand(domains)).get();

    List<String> attackPatternsIds =
        payload.getAttackPatterns().stream().map(AttackPattern::getId).toList();
    PayloadInput input = payloadComposer.forPayloadInput(payload, attackPatternsIds);

    // -- MOCKING EXTERNAL WEBSERVICE CALL --
    when(enterpriseEdition.getEncodedCertificate()).thenReturn("certificate");
    when(httpClientFactory.httpClientCustom()).thenReturn(httpClient);
    when(httpClient.execute(
            Mockito.any(ClassicHttpRequest.class), Mockito.any(HttpClientResponseHandler.class)))
        .thenAnswer(
            inv -> {
              Object[] args = inv.getArguments();
              Field f = args[1].getClass().getDeclaredField("arg$3");
              f.setAccessible(true);
              Integer retry = (Integer) f.get(args[1]);
              return switch (retry) {
                case 1, 2, 3 -> null;
                case null, default -> throw new IllegalStateException("Not expected retry");
              };
            });

    // -- EXECUTE --
    mockMvc
        .perform(
            post("/"
                    + DetectionRemediationApi.DETECTION_REMEDIATION_URI
                    + "/rules/"
                    + CROWDSTRIKE_FRONTEND_NAME)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(input)))
        .andExpect(status().isServiceUnavailable());
  }

  @Test
  @DisplayName(
      "Generate AI rules detection remediation by payload with retry until bad gateway (platform issue)")
  public void
      getDetectionRemediationRuleByPayloadWithRetryDetectionRemediationAIResponseBadGateway()
          throws Exception {
    // -- PREPARE -
    Set<Domain> domains = domainComposer.forDomain(null).persist().getSet();

    Command payload =
        (Command) payloadComposer.forPayload(PayloadFixture.createDefaultCommand(domains)).get();

    List<String> attackPatternsIds =
        payload.getAttackPatterns().stream().map(AttackPattern::getId).toList();
    PayloadInput input = payloadComposer.forPayloadInput(payload, attackPatternsIds);

    // -- MOCKING EXTERNAL WEBSERVICE CALL --
    String detectionRemediationAIBadResponse = getBadDetectionRemediationAIResponse();
    when(enterpriseEdition.getEncodedCertificate()).thenReturn("certificate");
    when(httpClientFactory.httpClientCustom()).thenReturn(httpClient);
    when(httpClient.execute(
            Mockito.any(ClassicHttpRequest.class), Mockito.any(HttpClientResponseHandler.class)))
        .thenAnswer(
            inv -> {
              Object[] args = inv.getArguments();
              Field f = args[1].getClass().getDeclaredField("arg$3");
              f.setAccessible(true);
              Integer retry = (Integer) f.get(args[1]);
              return switch (retry) {
                case 1, 2 -> null;
                case 3 -> detectionRemediationAIBadResponse;
                case null, default -> throw new IllegalStateException("Not expected retry");
              };
            });

    // -- EXECUTE --
    mockMvc
        .perform(
            post("/"
                    + DetectionRemediationApi.DETECTION_REMEDIATION_URI
                    + "/rules/"
                    + CROWDSTRIKE_FRONTEND_NAME)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(input)))
        .andExpect(status().isBadGateway());
  }

  @Test
  @DisplayName(
      "Generate AI rules detection remediation by payload with retry until service available")
  public void getDetectionRemediationRuleByPayloadWithRetryDetectionRemediationAIResponse()
      throws Exception {
    // -- PREPARE -
    Set<Domain> domains = domainComposer.forDomain(null).persist().getSet();

    Command payload =
        (Command) payloadComposer.forPayload(PayloadFixture.createDefaultCommand(domains)).get();

    List<String> attackPatternsIds =
        payload.getAttackPatterns().stream().map(AttackPattern::getId).toList();
    PayloadInput input = payloadComposer.forPayloadInput(payload, attackPatternsIds);

    // -- MOCKING EXTERNAL WEBSERVICE CALL --
    String detectionRemediationAIGoodResponse =
        getDetectionRemediationAIResponseByCollector(CollectorsUtils.CROWDSTRIKE);
    when(enterpriseEdition.getEncodedCertificate()).thenReturn("certificate");
    when(httpClientFactory.httpClientCustom()).thenReturn(httpClient);
    when(httpClient.execute(
            Mockito.any(ClassicHttpRequest.class), Mockito.any(HttpClientResponseHandler.class)))
        .thenAnswer(
            inv -> {
              Object[] args = inv.getArguments();
              Field f = args[1].getClass().getDeclaredField("arg$3");
              f.setAccessible(true);
              Integer retry = (Integer) f.get(args[1]);
              return switch (retry) {
                case 1, 2 -> null;
                case 3 -> detectionRemediationAIGoodResponse;
                case null, default -> throw new IllegalStateException("Not expected retry");
              };
            });

    // -- EXECUTE --
    mockMvc
        .perform(
            post("/"
                    + DetectionRemediationApi.DETECTION_REMEDIATION_URI
                    + "/rules/"
                    + CROWDSTRIKE_FRONTEND_NAME)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(input)))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName(
      "Generate AI rules detection remediation for CrowdStrike using a payload of type command with rules")
  public void getDetectionRemediationRuleBasedPayloadCommandCrowdStrikeWithRules() {

    // -- PREPARE -
    Set<Domain> domains = domainComposer.forDomain(null).persist().getSet();

    Command payload =
        (Command) payloadComposer.forPayload(PayloadFixture.createDefaultCommand(domains)).get();

    List<String> attackPatternsIds =
        payload.getAttackPatterns().stream().map(AttackPattern::getId).toList();
    PayloadInput input = payloadComposer.forPayloadInput(payload, attackPatternsIds);

    DetectionRemediationInput detectionRemediationInput = new DetectionRemediationInput();
    detectionRemediationInput.setValues("I have a rule");
    detectionRemediationInput.setAuthorRule(DetectionRemediation.AUTHOR_RULE.HUMAN);
    detectionRemediationInput.setCollectorType(CollectorsUtils.CROWDSTRIKE);
    input.setDetectionRemediations(List.of(detectionRemediationInput));

    when(enterpriseEdition.getEncodedCertificate()).thenReturn("certificate");

    // -- EXECUTE --
    assertThatThrownBy(
            () ->
                mockMvc.perform(
                    post("/"
                            + DetectionRemediationApi.DETECTION_REMEDIATION_URI
                            + "/rules/"
                            + CROWDSTRIKE_FRONTEND_NAME)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(input))))
        .hasCauseInstanceOf(IllegalStateException.class)
        .hasMessage(
            "Request processing failed: java.lang.IllegalStateException: AI Webservice available only for empty content");
  }

  @Test
  @DisplayName(
      "Generate AI rules detection remediation  for CrowdStrike using a non‑persistent payload of type command without attack patterns and arguments")
  public void
      getDetectionRemediationRuleBasedOnPayloadCommandCrowdStrikeWithoutAttackPatternAndArguments()
          throws Exception {
    // -- PREPARE -
    Set<Domain> domains = domainComposer.forDomain(null).persist().getSet();

    Command payload =
        (Command) payloadComposer.forPayload(PayloadFixture.createDefaultCommand(domains)).get();

    List<String> attackPatternsIds =
        payload.getAttackPatterns().stream().map(AttackPattern::getId).toList();
    PayloadInput input = payloadComposer.forPayloadInput(payload, attackPatternsIds);
    when(enterpriseEdition.getEncodedCertificate()).thenReturn("certificate");

    // -- MOCKING EXTERNAL WEBSERVICE CALL --
    String detectionRemediationAIResponse =
        getDetectionRemediationAIResponseByCollector(CollectorsUtils.CROWDSTRIKE);
    when(httpClientFactory.httpClientCustom()).thenReturn(httpClient);
    when(httpClient.execute(
            Mockito.any(ClassicHttpRequest.class), Mockito.any(HttpClientResponseHandler.class)))
        .thenAnswer(inv -> detectionRemediationAIResponse);

    // -- EXECUTE --
    String output =
        mockMvc
            .perform(
                post("/"
                        + DetectionRemediationApi.DETECTION_REMEDIATION_URI
                        + "/rules/"
                        + CROWDSTRIKE_FRONTEND_NAME)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(input)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // -- ASSERT --
    String rules = JsonPath.read(output, "$.rules");
    assertThat(rules).isNotBlank();
    assertThat(rules)
        .isEqualTo(
            """
                        <p>================================</p>
                        <p>Rule 1</p>
                        <p>Rule Type: Process Creation</p>
                        <p>Action to take: Monitor</p>
                        <p>Severity: Low</p>
                        <p>Rule name: PowerShell Directory Traversal Command Execution</p>
                        <p>Rule description: Monitors for the execution of the 'cd ..' directory traversal command via PowerShell, which may indicate reconnaissance or lateral movement activity.</p>
                        <p>Tactic & Technique: Custom Intelligence via Indicator of Attack</p>
                        <p>Detection Strategy: This rule detects the use of the 'cd ..' command executed by PowerShell, which is a common method for directory traversal and may be part of enumeration or lateral movement. By focusing on the process name and a simple command pattern, the rule is resilient to minor variations and easy to maintain, while minimizing false positives.</p>
                        <p>Field Configuration: </p>
                        <ul><li>Grandparent Image Filename: .*</li>
                        <li>Grandparent Command Line: .*</li>
                        <li>Parent Image Filename: .*</li>
                        <li>Parent Command Line: .*</li>
                        <li>Image Filename: .*powershell\\.exe</li>
                        <li>Command Line: .*cd\\s+\\.\\..*</li>
                        </ul>""");
  }

  @Test
  @DisplayName(
      "Generate AI rules detection remediation  for CrowdStrike using a non‑persistent payload of type command")
  public void getDetectionRemediationRuleBasedOnPayloadCommandCrowdStrike() throws Exception {
    // -- PREPARE -
    List<AttackPattern> attackPatterns = saveAndGetAttackPatterns();

    List<String> attackPatternsIds = attackPatterns.stream().map(AttackPattern::getId).toList();

    List<PayloadArgument> payloadArguments = getPayloadArguments();

    Set<Domain> domains = domainComposer.forDomain(null).persist().getSet();

    Command payload =
        (Command)
            payloadComposer
                .forPayload(
                    PayloadFixture.createDefaultCommandWithAttackPatternAndArguments(
                        attackPatterns, payloadArguments, domains))
                .get();

    PayloadInput input = payloadComposer.forPayloadInput(payload, attackPatternsIds);
    when(enterpriseEdition.getEncodedCertificate()).thenReturn("certificate");

    // -- MOCKING EXTERNAL WEBSERVICE CALL --
    String detectionRemediationAIResponse =
        getDetectionRemediationAIResponseByCollector(CollectorsUtils.CROWDSTRIKE);
    when(httpClientFactory.httpClientCustom()).thenReturn(httpClient);
    when(httpClient.execute(
            Mockito.any(ClassicHttpRequest.class), Mockito.any(HttpClientResponseHandler.class)))
        .thenAnswer(inv -> detectionRemediationAIResponse);

    // -- EXECUTE --
    String output =
        mockMvc
            .perform(
                post("/"
                        + DetectionRemediationApi.DETECTION_REMEDIATION_URI
                        + "/rules/"
                        + CROWDSTRIKE_FRONTEND_NAME)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(input)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // -- ASSERT --
    String rules = JsonPath.read(output, "$.rules");
    assertThat(rules).isNotBlank();
    assertThat(rules)
        .isEqualTo(
            """
                        <p>================================</p>
                        <p>Rule 1</p>
                        <p>Rule Type: Process Creation</p>
                        <p>Action to take: Monitor</p>
                        <p>Severity: Low</p>
                        <p>Rule name: PowerShell Directory Traversal Command Execution</p>
                        <p>Rule description: Monitors for the execution of the 'cd ..' directory traversal command via PowerShell, which may indicate reconnaissance or lateral movement activity.</p>
                        <p>Tactic & Technique: Custom Intelligence via Indicator of Attack</p>
                        <p>Detection Strategy: This rule detects the use of the 'cd ..' command executed by PowerShell, which is a common method for directory traversal and may be part of enumeration or lateral movement. By focusing on the process name and a simple command pattern, the rule is resilient to minor variations and easy to maintain, while minimizing false positives.</p>
                        <p>Field Configuration: </p>
                        <ul><li>Grandparent Image Filename: .*</li>
                        <li>Grandparent Command Line: .*</li>
                        <li>Parent Image Filename: .*</li>
                        <li>Parent Command Line: .*</li>
                        <li>Image Filename: .*powershell\\.exe</li>
                        <li>Command Line: .*cd\\s+\\.\\..*</li>
                        </ul>""");
  }

  @Test
  @DisplayName(
      "Generate AI rules detection remediation  for Splunk using a non‑persistent payload of type command")
  public void getDetectionRemediationRuleBasedOnPayloadCommandSplunk() throws Exception {
    // -- PREPARE -
    List<AttackPattern> attackPatterns = saveAndGetAttackPatterns();

    List<String> attackPatternsIds = attackPatterns.stream().map(AttackPattern::getId).toList();

    List<PayloadArgument> payloadArguments = getPayloadArguments();

    Set<Domain> domains = domainComposer.forDomain(null).persist().getSet();

    Command payload =
        (Command)
            payloadComposer
                .forPayload(
                    PayloadFixture.createDefaultCommandWithAttackPatternAndArguments(
                        attackPatterns, payloadArguments, domains))
                .get();

    PayloadInput input = payloadComposer.forPayloadInput(payload, attackPatternsIds);
    when(enterpriseEdition.getEncodedCertificate()).thenReturn("certificate");

    // -- MOCKING EXTERNAL WEBSERVICE CALL --
    String detectionRemediationAIResponse =
        getDetectionRemediationAIResponseByCollector(CollectorsUtils.SPLUNK);
    when(httpClientFactory.httpClientCustom()).thenReturn(httpClient);
    when(httpClient.execute(
            Mockito.any(ClassicHttpRequest.class), Mockito.any(HttpClientResponseHandler.class)))
        .thenAnswer(inv -> detectionRemediationAIResponse);

    // -- EXECUTE --
    String output =
        mockMvc
            .perform(
                post("/"
                        + DetectionRemediationApi.DETECTION_REMEDIATION_URI
                        + "/rules/"
                        + SPLUNK_FRONTEND_NAME)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(input)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // -- ASSERT --
    String rules = JsonPath.read(output, "$.rules");
    assertThat(rules).isNotBlank();
    assertThat(rules)
        .isEqualTo(
            "index=windows EventCode=4688 CommandLine=\"*Invoke-WebRequest*\" CommandLine=\"*AnyDesk*\" | stats count by Computer, User, CommandLine | sort -count");
  }

  @Test
  @DisplayName(
      "Generate AI rules detection remediation  for CrowdStrike using a non‑persistent payload of type DnsResolution")
  public void getDetectionRemediationRuleBasedOnPayloadDnsResolutionCrowdStrike() throws Exception {
    // -- PREPARE -
    List<AttackPattern> attackPatterns = saveAndGetAttackPatterns();

    List<String> attackPatternsIds = attackPatterns.stream().map(AttackPattern::getId).toList();

    List<PayloadArgument> payloadArguments = getPayloadArguments();

    Set<Domain> domains = domainComposer.forDomain(null).persist().getSet();
    DnsResolution payload =
        (DnsResolution)
            payloadComposer
                .forPayload(
                    PayloadFixture.createDefaultDnsResolutionWithAttackPatternAndArguments(
                        attackPatterns, payloadArguments, domains))
                .get();

    PayloadInput input = payloadComposer.forPayloadInput(payload, attackPatternsIds);
    when(enterpriseEdition.getEncodedCertificate()).thenReturn("certificate");

    // -- MOCKING EXTERNAL WEBSERVICE CALL --
    String detectionRemediationAIResponse =
        getDetectionRemediationAIResponseByCollector(CollectorsUtils.CROWDSTRIKE);
    when(httpClientFactory.httpClientCustom()).thenReturn(httpClient);
    when(httpClient.execute(
            Mockito.any(ClassicHttpRequest.class), Mockito.any(HttpClientResponseHandler.class)))
        .thenAnswer(inv -> detectionRemediationAIResponse);

    // -- EXECUTE --
    String output =
        mockMvc
            .perform(
                post("/"
                        + DetectionRemediationApi.DETECTION_REMEDIATION_URI
                        + "/rules/"
                        + CROWDSTRIKE_FRONTEND_NAME)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(input)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // -- ASSERT --
    String rules = JsonPath.read(output, "$.rules");
    assertThat(rules).isNotBlank();
    assertThat(rules)
        .isEqualTo(
            """
                        <p>================================</p>
                        <p>Rule 1</p>
                        <p>Rule Type: Process Creation</p>
                        <p>Action to take: Monitor</p>
                        <p>Severity: Low</p>
                        <p>Rule name: PowerShell Directory Traversal Command Execution</p>
                        <p>Rule description: Monitors for the execution of the 'cd ..' directory traversal command via PowerShell, which may indicate reconnaissance or lateral movement activity.</p>
                        <p>Tactic & Technique: Custom Intelligence via Indicator of Attack</p>
                        <p>Detection Strategy: This rule detects the use of the 'cd ..' command executed by PowerShell, which is a common method for directory traversal and may be part of enumeration or lateral movement. By focusing on the process name and a simple command pattern, the rule is resilient to minor variations and easy to maintain, while minimizing false positives.</p>
                        <p>Field Configuration: </p>
                        <ul><li>Grandparent Image Filename: .*</li>
                        <li>Grandparent Command Line: .*</li>
                        <li>Parent Image Filename: .*</li>
                        <li>Parent Command Line: .*</li>
                        <li>Image Filename: .*powershell\\.exe</li>
                        <li>Command Line: .*cd\\s+\\.\\..*</li>
                        </ul>""");
  }

  @Test
  @DisplayName(
      "Generate AI rules detection remediation  for Splunk using a non‑persistent payload of type DnsResolution")
  public void getDetectionRemediationRuleBasedOnPayloadDnsResolutionSplunk() throws Exception {
    // -- PREPARE -
    List<AttackPattern> attackPatterns = saveAndGetAttackPatterns();

    List<String> attackPatternsIds = attackPatterns.stream().map(AttackPattern::getId).toList();

    List<PayloadArgument> payloadArguments = getPayloadArguments();

    Set<Domain> domains = domainComposer.forDomain(null).persist().getSet();
    DnsResolution payload =
        (DnsResolution)
            payloadComposer
                .forPayload(
                    PayloadFixture.createDefaultDnsResolutionWithAttackPatternAndArguments(
                        attackPatterns, payloadArguments, domains))
                .get();

    PayloadInput input = payloadComposer.forPayloadInput(payload, attackPatternsIds);
    when(enterpriseEdition.getEncodedCertificate()).thenReturn("certificate");

    // -- MOCKING EXTERNAL WEBSERVICE CALL --
    String detectionRemediationAIResponse =
        getDetectionRemediationAIResponseByCollector(CollectorsUtils.SPLUNK);
    when(httpClientFactory.httpClientCustom()).thenReturn(httpClient);
    when(httpClient.execute(
            Mockito.any(ClassicHttpRequest.class), Mockito.any(HttpClientResponseHandler.class)))
        .thenAnswer(inv -> detectionRemediationAIResponse);

    // -- EXECUTE --
    String output =
        mockMvc
            .perform(
                post("/"
                        + DetectionRemediationApi.DETECTION_REMEDIATION_URI
                        + "/rules/"
                        + SPLUNK_FRONTEND_NAME)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(input)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    // -- ASSERT --
    String rules = JsonPath.read(output, "$.rules");
    assertThat(rules).isNotBlank();
    assertThat(rules)
        .isEqualTo(
            "index=windows EventCode=4688 CommandLine=\"*Invoke-WebRequest*\" CommandLine=\"*AnyDesk*\" | stats count by Computer, User, CommandLine | sort -count");
  }

  @Test
  @DisplayName(
      "Generate AI rules detection remediation  for CrowdStrike using a non‑persistent payload of type FileDrop")
  public void getDetectionRemediationRuleBasedOnPayloadFileDropCrowdStrike() throws Exception {
    // -- PREPARE -
    List<AttackPattern> attackPatterns = saveAndGetAttackPatterns();

    List<String> attackPatternsIds = attackPatterns.stream().map(AttackPattern::getId).toList();

    List<PayloadArgument> payloadArguments = getPayloadArguments();

    Set<Domain> domains = domainComposer.forDomain(null).persist().getSet();

    FileDrop payload =
        (FileDrop)
            payloadComposer
                .forPayload(
                    PayloadFixture.createDefaultFileDropWithAttackPatternAndArguments(
                        attackPatterns, payloadArguments, domains))
                .withFileDrop(
                    documentComposer.forDocument(
                        DocumentFixture.getDocument(FileFixture.getPlainTextFileContent())))
                .get();

    PayloadInput input = payloadComposer.forPayloadInput(payload, attackPatternsIds);

    // -- EXECUTE --
    ResultActions output =
        mockMvc.perform(
            post("/"
                    + DetectionRemediationApi.DETECTION_REMEDIATION_URI
                    + "/rules/"
                    + CROWDSTRIKE_FRONTEND_NAME)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(input)));

    // -- ASSERT --
    output.andExpect(status().isNotImplemented());
  }

  @Test
  @DisplayName(
      "Generate AI rules detection remediation  for Splunk using a non‑persistent payload of type FileDrop")
  public void getDetectionRemediationRuleBasedOnPayloadFileDropSplunk() throws Exception {
    // -- PREPARE -
    List<AttackPattern> attackPatterns = saveAndGetAttackPatterns();

    List<String> attackPatternsIds = attackPatterns.stream().map(AttackPattern::getId).toList();

    List<PayloadArgument> payloadArguments = getPayloadArguments();

    Set<Domain> domains = domainComposer.forDomain(null).persist().getSet();

    FileDrop payload =
        (FileDrop)
            payloadComposer
                .forPayload(
                    PayloadFixture.createDefaultFileDropWithAttackPatternAndArguments(
                        attackPatterns, payloadArguments, domains))
                .withFileDrop(
                    documentComposer.forDocument(
                        DocumentFixture.getDocument(FileFixture.getPlainTextFileContent())))
                .get();

    PayloadInput input = payloadComposer.forPayloadInput(payload, attackPatternsIds);

    // -- EXECUTE --
    ResultActions output =
        mockMvc.perform(
            post("/"
                    + DetectionRemediationApi.DETECTION_REMEDIATION_URI
                    + "/rules/"
                    + SPLUNK_FRONTEND_NAME)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(input)));

    // -- ASSERT --
    output.andExpect(status().isNotImplemented());
  }

  @Test
  @DisplayName(
      "Generate AI rules detection remediation  for CrowdStrike using a non‑persistent payload of type Executable")
  public void getDetectionRemediationRuleBasedOnPayloadExecutableCrowdStrike() throws Exception {
    // -- PREPARE -
    List<AttackPattern> attackPatterns = saveAndGetAttackPatterns();

    List<String> attackPatternsIds = attackPatterns.stream().map(AttackPattern::getId).toList();

    List<PayloadArgument> payloadArguments = getPayloadArguments();

    Set<Domain> domains = domainComposer.forDomain(null).persist().getSet();
    Executable payload =
        (Executable)
            payloadComposer
                .forPayload(
                    PayloadFixture.createDefaultExecutableWithAttackPatternAndArguments(
                        attackPatterns, payloadArguments, domains))
                .withExecutable(
                    documentComposer.forDocument(
                        DocumentFixture.getDocument(FileFixture.getPngGridFileContent())))
                .get();

    PayloadInput input = payloadComposer.forPayloadInput(payload, attackPatternsIds);

    // -- EXECUTE --
    ResultActions output =
        mockMvc.perform(
            post("/"
                    + DetectionRemediationApi.DETECTION_REMEDIATION_URI
                    + "/rules/"
                    + CROWDSTRIKE_FRONTEND_NAME)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(input)));

    // -- ASSERT --
    output.andExpect(status().isNotImplemented());
  }

  @Test
  @DisplayName(
      "Generate AI rules detection remediation  for Splunk using a non‑persistent payload of type Executable")
  public void getDetectionRemediationRuleBasedOnPayloadExecutableSplunk() throws Exception {
    // -- PREPARE -
    List<AttackPattern> attackPatterns = saveAndGetAttackPatterns();

    List<String> attackPatternsIds = attackPatterns.stream().map(AttackPattern::getId).toList();

    List<PayloadArgument> payloadArguments = getPayloadArguments();

    Set<Domain> domains = domainComposer.forDomain(null).persist().getSet();
    Executable payload =
        (Executable)
            payloadComposer
                .forPayload(
                    PayloadFixture.createDefaultExecutableWithAttackPatternAndArguments(
                        attackPatterns, payloadArguments, domains))
                .withExecutable(
                    documentComposer.forDocument(
                        DocumentFixture.getDocument(FileFixture.getPngGridFileContent())))
                .get();

    PayloadInput input = payloadComposer.forPayloadInput(payload, attackPatternsIds);

    // -- EXECUTE --
    ResultActions output =
        mockMvc.perform(
            post("/"
                    + DetectionRemediationApi.DETECTION_REMEDIATION_URI
                    + "/rules/"
                    + SPLUNK_FRONTEND_NAME)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(input)));

    // -- ASSERT --
    output.andExpect(status().isNotImplemented());
  }

  // -- TEST API : POST
  // api/detection-remediations/ai/rules/inject/{injectId}/collector/{collectorType} --

  @Test
  @DisplayName("Generate AI rules detection remediation by inject id , EE not available")
  public void getDetectionRemediationRuleByInjectWithoutLicenceEE() throws JsonProcessingException {
    // -- PREPARE -
    collectorComposer
        .forCollector(CollectorFixture.createDefaultCollector(CollectorsUtils.CROWDSTRIKE))
        .persist();
    Inject inject = getInjectCommandWithPlatformsAndArchitectureAndAttackPatternAndArguments();

    when(httpClientFactory.httpClientCustom()).thenReturn(httpClient);
    when(enterpriseEdition.getEncodedCertificate()).thenCallRealMethod();

    // -- EXECUTE ASSERT --
    assertThatThrownBy(
            () ->
                mockMvc.perform(
                    post(
                        "/"
                            + DetectionRemediationApi.DETECTION_REMEDIATION_URI
                            + "/rules/inject/"
                            + inject.getId()
                            + "/collector/"
                            + CROWDSTRIKE_FRONTEND_NAME)))
        .hasCauseInstanceOf(IllegalStateException.class)
        .hasMessage(
            "Request processing failed: java.lang.IllegalStateException: Enterprise Edition is not available");
  }

  @Test
  @DisplayName("Generate AI rules detection remediation  by inject id for unknow collector type")
  public void getDetectionRemediationRuleByInjectUnknowCollectorType() throws Exception {
    // -- PREPARE -
    Inject inject = getInjectCommandWithPlatformsAndArchitectureAndAttackPatternAndArguments();

    // -- EXECUTE --
    MockHttpServletResponse output =
        mockMvc
            .perform(
                post(
                    "/"
                        + DetectionRemediationApi.DETECTION_REMEDIATION_URI
                        + "/rules/inject/"
                        + inject.getId()
                        + "/collector/collector_name_unknow"))
            .andReturn()
            .getResponse();

    // -- ASSERT --
    assertThat(output.getStatus()).isEqualTo(404);
    String response = JsonPath.read(output.getContentAsString(), "$.message");
    assertThat(response)
        .isEqualTo("Element not found: Collector not found with type: collector_name_unknow");
  }

  @Test
  @DisplayName(
      "Generate AI rules detection remediation for CrowdStrike using an inject of type command with detection remediation with content")
  public void
      getDetectionRemediationRuleBasedInjectCommandCrowdStrikeWithDetectionRemediationWithContent() {
    // -- PREPARE -
    Collector collector =
        collectorComposer
            .forCollector(CollectorFixture.createDefaultCollector(CollectorsUtils.CROWDSTRIKE))
            .persist()
            .get();
    clearEntityManager();
    Inject inject =
        getInjectCommandWithPlatformsAndArchitectureAndAttackPatternAndArgumentsAndDetectionRemediationWithContent(
            collector);

    when(enterpriseEdition.getEncodedCertificate()).thenReturn("certificate");

    // -- EXECUTE --
    assertThatThrownBy(
            () ->
                mockMvc.perform(
                    post(
                        "/"
                            + DetectionRemediationApi.DETECTION_REMEDIATION_URI
                            + "/rules/inject/"
                            + inject.getId()
                            + "/collector/"
                            + CROWDSTRIKE_FRONTEND_NAME)))
        .hasCauseInstanceOf(IllegalStateException.class)
        .hasMessage(
            "Request processing failed: java.lang.IllegalStateException: AI Webservice available only for empty content");
  }

  @Test
  @DisplayName(
      "Generate AI rules detection remediation for CrowdStrike using an inject of type command with detection remediation without content")
  public void
      getDetectionRemediationRuleBasedInjectCommandCrowdStrikeWithDetectionRemediationWithoutContent()
          throws Exception {
    // -- PREPARE -
    Collector collector =
        collectorComposer
            .forCollector(CollectorFixture.createDefaultCollector(CollectorsUtils.CROWDSTRIKE))
            .persist()
            .get();
    clearEntityManager();
    Inject inject =
        getInjectCommandWithPlatformsAndArchitectureAndAttackPatternAndArgumentsAndDetectionRemediationWithoutContent(
            collector);

    when(enterpriseEdition.getEncodedCertificate()).thenReturn("certificate");

    // -- MOCKING EXTERNAL WEBSERVICE CALL --
    String detectionRemediationAIResponse =
        getDetectionRemediationAIResponseByCollector(CollectorsUtils.CROWDSTRIKE);
    when(httpClientFactory.httpClientCustom()).thenReturn(httpClient);
    when(httpClient.execute(
            Mockito.any(ClassicHttpRequest.class), Mockito.any(HttpClientResponseHandler.class)))
        .thenAnswer(inv -> detectionRemediationAIResponse);

    // -- EXECUTE --
    mockMvc
        .perform(
            post(
                "/"
                    + DetectionRemediationApi.DETECTION_REMEDIATION_URI
                    + "/rules/inject/"
                    + inject.getId()
                    + "/collector/"
                    + CROWDSTRIKE_FRONTEND_NAME))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName(
      "Generate AI rules detection remediation  for CrowdStrike using an inject id of type command")
  public void
      getDetectionRemediationRuleBasedOnInjectCommandCrowdStrikeWithoutDetectionRemediation()
          throws Exception {
    // -- PREPARE -
    collectorComposer
        .forCollector(CollectorFixture.createDefaultCollector(CollectorsUtils.CROWDSTRIKE))
        .persist();
    Inject inject = getInjectCommandWithPlatformsAndArchitectureAndAttackPatternAndArguments();

    when(enterpriseEdition.getEncodedCertificate()).thenReturn("certificate");

    // -- MOCKING EXTERNAL WEBSERVICE CALL --
    String detectionRemediationAIResponse =
        getDetectionRemediationAIResponseByCollector(CollectorsUtils.CROWDSTRIKE);
    when(httpClientFactory.httpClientCustom()).thenReturn(httpClient);
    when(httpClient.execute(
            Mockito.any(ClassicHttpRequest.class), Mockito.any(HttpClientResponseHandler.class)))
        .thenAnswer(inv -> detectionRemediationAIResponse);

    // -- EXECUTE --
    String output =
        mockMvc
            .perform(
                post(
                    "/"
                        + DetectionRemediationApi.DETECTION_REMEDIATION_URI
                        + "/rules/inject/"
                        + inject.getId()
                        + "/collector/"
                        + CROWDSTRIKE_FRONTEND_NAME))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // -- ASSERT --
    String collectorType = JsonPath.read(output, "$.detection_remediation_collector");
    assertThat(collectorType).isEqualTo(CollectorsUtils.CROWDSTRIKE);

    String idDetection = JsonPath.read(output, "$.detection_remediation_id");
    assertThat(idDetection).isNotBlank();

    String rules = JsonPath.read(output, "$.detection_remediation_values");
    assertThat(rules).isNotBlank();
    assertThat(rules)
        .isEqualTo(
            """
                        <p>================================</p>
                        <p>Rule 1</p>
                        <p>Rule Type: Process Creation</p>
                        <p>Action to take: Monitor</p>
                        <p>Severity: Low</p>
                        <p>Rule name: PowerShell Directory Traversal Command Execution</p>
                        <p>Rule description: Monitors for the execution of the 'cd ..' directory traversal command via PowerShell, which may indicate reconnaissance or lateral movement activity.</p>
                        <p>Tactic & Technique: Custom Intelligence via Indicator of Attack</p>
                        <p>Detection Strategy: This rule detects the use of the 'cd ..' command executed by PowerShell, which is a common method for directory traversal and may be part of enumeration or lateral movement. By focusing on the process name and a simple command pattern, the rule is resilient to minor variations and easy to maintain, while minimizing false positives.</p>
                        <p>Field Configuration: </p>
                        <ul><li>Grandparent Image Filename: .*</li>
                        <li>Grandparent Command Line: .*</li>
                        <li>Parent Image Filename: .*</li>
                        <li>Parent Command Line: .*</li>
                        <li>Image Filename: .*powershell\\.exe</li>
                        <li>Command Line: .*cd\\s+\\.\\..*</li>
                        </ul>""");
  }

  @Test
  @DisplayName(
      "Generate AI rules detection remediation  for Splunk using an inject id of type command")
  public void getDetectionRemediationRuleBasedOnInjectCommandSplunkWithoutDetectionRemediation()
      throws Exception {
    // -- PREPARE -
    collectorComposer
        .forCollector(CollectorFixture.createDefaultCollector(CollectorsUtils.SPLUNK))
        .persist();
    Inject inject = getInjectCommandWithPlatformsAndArchitectureAndAttackPatternAndArguments();

    when(enterpriseEdition.getEncodedCertificate()).thenReturn("certificate");

    // -- MOCKING EXTERNAL WEBSERVICE CALL --
    String detectionRemediationAIResponse =
        getDetectionRemediationAIResponseByCollector(CollectorsUtils.SPLUNK);
    when(httpClientFactory.httpClientCustom()).thenReturn(httpClient);
    when(httpClient.execute(
            Mockito.any(ClassicHttpRequest.class), Mockito.any(HttpClientResponseHandler.class)))
        .thenAnswer(inv -> detectionRemediationAIResponse);

    // -- EXECUTE --
    String output =
        mockMvc
            .perform(
                post(
                    "/"
                        + DetectionRemediationApi.DETECTION_REMEDIATION_URI
                        + "/rules/inject/"
                        + inject.getId()
                        + "/collector/"
                        + SPLUNK_FRONTEND_NAME))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // -- ASSERT --
    String collectorType = JsonPath.read(output, "$.detection_remediation_collector");
    assertThat(collectorType).isEqualTo(CollectorsUtils.SPLUNK);

    String idDetection = JsonPath.read(output, "$.detection_remediation_id");
    assertThat(idDetection).isNotBlank();

    String rules = JsonPath.read(output, "$.detection_remediation_values");
    assertThat(rules).isNotBlank();
    assertThat(rules)
        .isEqualTo(
            "index=windows EventCode=4688 CommandLine=\"*Invoke-WebRequest*\" CommandLine=\"*AnyDesk*\" | stats count by Computer, User, CommandLine | sort -count");
  }

  @Test
  @DisplayName(
      "Generate AI rules detection remediation  for CrowdStrike using an inject id of type DnsResolution")
  public void
      getDetectionRemediationRuleBasedOnInjectDnsResolutionCrowdStrikeWithoutDetectionRemediation()
          throws Exception {
    // -- PREPARE -
    collectorComposer
        .forCollector(CollectorFixture.createDefaultCollector(CollectorsUtils.CROWDSTRIKE))
        .persist();
    Inject inject =
        getInjectDnsResolutionWithPlatformsAndArchitectureAndAttackPatternAndArguments();

    when(enterpriseEdition.getEncodedCertificate()).thenReturn("certificate");

    // -- MOCKING EXTERNAL WEBSERVICE CALL --
    String detectionRemediationAIResponse =
        getDetectionRemediationAIResponseByCollector(CollectorsUtils.CROWDSTRIKE);
    when(httpClientFactory.httpClientCustom()).thenReturn(httpClient);
    when(httpClient.execute(
            Mockito.any(ClassicHttpRequest.class), Mockito.any(HttpClientResponseHandler.class)))
        .thenAnswer(inv -> detectionRemediationAIResponse);

    // -- EXECUTE --
    String output =
        mockMvc
            .perform(
                post(
                    "/"
                        + DetectionRemediationApi.DETECTION_REMEDIATION_URI
                        + "/rules/inject/"
                        + inject.getId()
                        + "/collector/"
                        + CROWDSTRIKE_FRONTEND_NAME))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // -- ASSERT --
    String collectorType = JsonPath.read(output, "$.detection_remediation_collector");
    assertThat(collectorType).isEqualTo(CollectorsUtils.CROWDSTRIKE);

    String idDetection = JsonPath.read(output, "$.detection_remediation_id");
    assertThat(idDetection).isNotBlank();

    String rules = JsonPath.read(output, "$.detection_remediation_values");
    assertThat(rules).isNotBlank();
    assertThat(rules)
        .isEqualTo(
            """
                        <p>================================</p>
                        <p>Rule 1</p>
                        <p>Rule Type: Process Creation</p>
                        <p>Action to take: Monitor</p>
                        <p>Severity: Low</p>
                        <p>Rule name: PowerShell Directory Traversal Command Execution</p>
                        <p>Rule description: Monitors for the execution of the 'cd ..' directory traversal command via PowerShell, which may indicate reconnaissance or lateral movement activity.</p>
                        <p>Tactic & Technique: Custom Intelligence via Indicator of Attack</p>
                        <p>Detection Strategy: This rule detects the use of the 'cd ..' command executed by PowerShell, which is a common method for directory traversal and may be part of enumeration or lateral movement. By focusing on the process name and a simple command pattern, the rule is resilient to minor variations and easy to maintain, while minimizing false positives.</p>
                        <p>Field Configuration: </p>
                        <ul><li>Grandparent Image Filename: .*</li>
                        <li>Grandparent Command Line: .*</li>
                        <li>Parent Image Filename: .*</li>
                        <li>Parent Command Line: .*</li>
                        <li>Image Filename: .*powershell\\.exe</li>
                        <li>Command Line: .*cd\\s+\\.\\..*</li>
                        </ul>""");
  }

  @Test
  @DisplayName(
      "Generate AI rules detection remediation  for Splunk using an inject id of type DnsResolution")
  public void
      getDetectionRemediationRuleBasedOnInjectDnsResolutionSplunkWithoutDetectionRemediation()
          throws Exception {
    // -- PREPARE -
    collectorComposer
        .forCollector(CollectorFixture.createDefaultCollector(CollectorsUtils.SPLUNK))
        .persist();
    Inject inject =
        getInjectDnsResolutionWithPlatformsAndArchitectureAndAttackPatternAndArguments();

    when(enterpriseEdition.getEncodedCertificate()).thenReturn("certificate");

    // -- MOCKING EXTERNAL WEBSERVICE CALL --
    String detectionRemediationAIResponse =
        getDetectionRemediationAIResponseByCollector(CollectorsUtils.SPLUNK);
    when(httpClientFactory.httpClientCustom()).thenReturn(httpClient);
    when(httpClient.execute(
            Mockito.any(ClassicHttpRequest.class), Mockito.any(HttpClientResponseHandler.class)))
        .thenAnswer(inv -> detectionRemediationAIResponse);

    // -- EXECUTE --
    String output =
        mockMvc
            .perform(
                post(
                    "/"
                        + DetectionRemediationApi.DETECTION_REMEDIATION_URI
                        + "/rules/inject/"
                        + inject.getId()
                        + "/collector/"
                        + SPLUNK_FRONTEND_NAME))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // -- ASSERT --
    String collectorType = JsonPath.read(output, "$.detection_remediation_collector");
    assertThat(collectorType).isEqualTo(CollectorsUtils.SPLUNK);

    String idDetection = JsonPath.read(output, "$.detection_remediation_id");
    assertThat(idDetection).isNotBlank();

    String rules = JsonPath.read(output, "$.detection_remediation_values");
    assertThat(rules).isNotBlank();
    assertThat(rules)
        .isEqualTo(
            "index=windows EventCode=4688 CommandLine=\"*Invoke-WebRequest*\" CommandLine=\"*AnyDesk*\" | stats count by Computer, User, CommandLine | sort -count");
  }

  @Test
  @DisplayName(
      "Generate AI rules detection remediation  for CrowdStrike using an inject id of type FileDrop")
  public void
      getDetectionRemediationRuleBasedOnInjectFileDropCrowdStrikeWithoutDetectionRemediation()
          throws Exception {
    // -- PREPARE -
    collectorComposer
        .forCollector(CollectorFixture.createDefaultCollector(CollectorsUtils.CROWDSTRIKE))
        .persist();
    Inject inject = getInjectFileDropWithPlatformsAndArchitectureAndAttackPatternAndArguments();

    when(enterpriseEdition.getEncodedCertificate()).thenReturn("certificate");

    // -- MOCKING EXTERNAL WEBSERVICE CALL --
    String detectionRemediationAIResponse =
        getDetectionRemediationAIResponseByCollector(CollectorsUtils.CROWDSTRIKE);
    when(httpClientFactory.httpClientCustom()).thenReturn(httpClient);
    when(httpClient.execute(
            Mockito.any(ClassicHttpRequest.class), Mockito.any(HttpClientResponseHandler.class)))
        .thenAnswer(inv -> detectionRemediationAIResponse);

    // -- EXECUTE --
    ResultActions output =
        mockMvc.perform(
            post(
                "/"
                    + DetectionRemediationApi.DETECTION_REMEDIATION_URI
                    + "/rules/inject/"
                    + inject.getId()
                    + "/collector/"
                    + CROWDSTRIKE_FRONTEND_NAME));

    // -- ASSERT --
    output.andExpect(status().isNotImplemented());
  }

  @Test
  @DisplayName(
      "Generate AI rules detection remediation  for Splunk using an inject id of type FileDrop")
  public void getDetectionRemediationRuleBasedOnInjectFileDropSplunkWithoutDetectionRemediation()
      throws Exception {
    // -- PREPARE -
    collectorComposer
        .forCollector(CollectorFixture.createDefaultCollector(CollectorsUtils.SPLUNK))
        .persist();
    Inject inject = getInjectFileDropWithPlatformsAndArchitectureAndAttackPatternAndArguments();

    when(enterpriseEdition.getEncodedCertificate()).thenReturn("certificate");

    // -- MOCKING EXTERNAL WEBSERVICE CALL --
    String detectionRemediationAIResponse =
        getDetectionRemediationAIResponseByCollector(CollectorsUtils.SPLUNK);
    when(httpClientFactory.httpClientCustom()).thenReturn(httpClient);
    when(httpClient.execute(
            Mockito.any(ClassicHttpRequest.class), Mockito.any(HttpClientResponseHandler.class)))
        .thenAnswer(inv -> detectionRemediationAIResponse);

    // -- EXECUTE --
    ResultActions output =
        mockMvc.perform(
            post(
                "/"
                    + DetectionRemediationApi.DETECTION_REMEDIATION_URI
                    + "/rules/inject/"
                    + inject.getId()
                    + "/collector/"
                    + SPLUNK_FRONTEND_NAME));

    // -- ASSERT --
    output.andExpect(status().isNotImplemented());
  }

  @Test
  @DisplayName(
      "Generate AI rules detection remediation  for CrowdStrike using an inject id of type Executable")
  public void
      getDetectionRemediationRuleBasedOnInjectExecutableCrowdStrikeWithoutDetectionRemediation()
          throws Exception {
    // -- PREPARE -
    collectorComposer
        .forCollector(CollectorFixture.createDefaultCollector(CollectorsUtils.CROWDSTRIKE))
        .persist();
    Inject inject = getInjectExecutableWithPlatformsAndArchitectureAndAttackPatternAndArguments();

    when(enterpriseEdition.getEncodedCertificate()).thenReturn("certificate");

    // -- MOCKING EXTERNAL WEBSERVICE CALL --
    String detectionRemediationAIResponse =
        getDetectionRemediationAIResponseByCollector(CollectorsUtils.CROWDSTRIKE);
    when(httpClientFactory.httpClientCustom()).thenReturn(httpClient);
    when(httpClient.execute(
            Mockito.any(ClassicHttpRequest.class), Mockito.any(HttpClientResponseHandler.class)))
        .thenAnswer(inv -> detectionRemediationAIResponse);

    // -- EXECUTE --
    ResultActions output =
        mockMvc.perform(
            post(
                "/"
                    + DetectionRemediationApi.DETECTION_REMEDIATION_URI
                    + "/rules/inject/"
                    + inject.getId()
                    + "/collector/"
                    + CROWDSTRIKE_FRONTEND_NAME));

    // -- ASSERT --
    output.andExpect(status().isNotImplemented());
  }

  @Test
  @DisplayName(
      "Generate AI rules detection remediation  for Splunk using an inject id of type Executable")
  public void getDetectionRemediationRuleBasedOnInjectExecutableSplunkWithoutDetectionRemediation()
      throws Exception {
    // -- PREPARE -
    collectorComposer
        .forCollector(CollectorFixture.createDefaultCollector(CollectorsUtils.SPLUNK))
        .persist();
    Inject inject = getInjectExecutableWithPlatformsAndArchitectureAndAttackPatternAndArguments();

    when(enterpriseEdition.getEncodedCertificate()).thenReturn("certificate");

    // -- MOCKING EXTERNAL WEBSERVICE CALL --
    String detectionRemediationAIResponse =
        getDetectionRemediationAIResponseByCollector(CollectorsUtils.SPLUNK);
    when(httpClientFactory.httpClientCustom()).thenReturn(httpClient);
    when(httpClient.execute(
            Mockito.any(ClassicHttpRequest.class), Mockito.any(HttpClientResponseHandler.class)))
        .thenAnswer(inv -> detectionRemediationAIResponse);

    // -- EXECUTE --
    ResultActions output =
        mockMvc.perform(
            post(
                "/"
                    + DetectionRemediationApi.DETECTION_REMEDIATION_URI
                    + "/rules/inject/"
                    + inject.getId()
                    + "/collector/"
                    + SPLUNK_FRONTEND_NAME));

    // -- ASSERT --
    output.andExpect(status().isNotImplemented());
  }

  // -- HELPER --
  private Inject getInjectCommandWithPlatformsAndArchitectureAndAttackPatternAndArguments()
      throws JsonProcessingException {
    List<AttackPattern> attackPatterns = saveAndGetAttackPatterns();

    List<PayloadArgument> payloadArguments = getPayloadArguments();

    Set<Domain> domains = domainComposer.forDomain(null).persist().getSet();

    Command payloadCommand =
        (Command)
            payloadComposer
                .forPayload(
                    PayloadFixture.createDefaultCommandWithAttackPatternAndArguments(
                        attackPatterns, payloadArguments, domains))
                .persist()
                .get();

    InjectorContract injectorContract =
        injectorContractComposer
            .forInjectorContract(
                InjectorContractFixture.createPayloadInjectorContract(
                    injectorFixture.getWellKnownOaevImplantInjector(), payloadCommand))
            .persist()
            .get();

    Map<String, Object> payloadArgumentMap =
        payloadArguments.stream()
            .collect(Collectors.toMap(PayloadArgument::getKey, PayloadArgument::getDefaultValue));

    return injectComposer
        .forInject(InjectFixture.createInjectWithPayloadArg(injectorContract, payloadArgumentMap))
        .persist()
        .get();
  }

  private Inject
      getInjectCommandWithPlatformsAndArchitectureAndAttackPatternAndArgumentsAndDetectionRemediationWithContent(
          Collector collector) {
    List<AttackPattern> attackPatterns = saveAndGetAttackPatterns();

    List<PayloadArgument> payloadArguments = getPayloadArguments();

    Set<Domain> domains = domainComposer.forDomain(null).persist().getSet();

    DetectionRemediation detectionRemediation = new DetectionRemediation();
    detectionRemediation.setValues("I have a rule");
    detectionRemediation.setAuthorRule(DetectionRemediation.AUTHOR_RULE.HUMAN);

    Command payload =
        (Command)
            PayloadFixture.createDefaultCommandWithAttackPatternAndArguments(
                attackPatterns, payloadArguments, domains);

    return injectComposer
        .forInject(InjectFixture.getDefaultInject())
        .withInjectorContract(
            injectorContractComposer
                .forInjectorContract(InjectorContractFixture.createDefaultInjectorContract())
                .withPayload(
                    payloadComposer
                        .forPayload(payload)
                        .withDetectionRemediation(
                            detectionRemediationComposer
                                .forDetectionRemediation(detectionRemediation)
                                .withCollector(collectorComposer.forCollector(collector)))))
        .persist()
        .get();
  }

  private Inject
      getInjectCommandWithPlatformsAndArchitectureAndAttackPatternAndArgumentsAndDetectionRemediationWithoutContent(
          Collector collector) {
    List<AttackPattern> attackPatterns = saveAndGetAttackPatterns();

    List<PayloadArgument> payloadArguments = getPayloadArguments();

    Set<Domain> domains = domainComposer.forDomain(null).persist().getSet();

    DetectionRemediation detectionRemediation = new DetectionRemediation();
    detectionRemediation.setValues("");
    detectionRemediation.setAuthorRule(DetectionRemediation.AUTHOR_RULE.HUMAN);

    Command payload =
        (Command)
            PayloadFixture.createDefaultCommandWithAttackPatternAndArguments(
                attackPatterns, payloadArguments, domains);

    return injectComposer
        .forInject(InjectFixture.getDefaultInject())
        .withInjectorContract(
            injectorContractComposer
                .forInjectorContract(InjectorContractFixture.createDefaultInjectorContract())
                .withPayload(
                    payloadComposer
                        .forPayload(payload)
                        .withDetectionRemediation(
                            detectionRemediationComposer
                                .forDetectionRemediation(detectionRemediation)
                                .withCollector(collectorComposer.forCollector(collector)))))
        .persist()
        .get();
  }

  private Inject getInjectDnsResolutionWithPlatformsAndArchitectureAndAttackPatternAndArguments()
      throws JsonProcessingException {
    List<AttackPattern> attackPatterns = saveAndGetAttackPatterns();

    List<PayloadArgument> payloadArguments = getPayloadArguments();

    Set<Domain> domains = domainComposer.forDomain(null).persist().getSet();
    DnsResolution payload =
        (DnsResolution)
            payloadComposer
                .forPayload(
                    PayloadFixture.createDefaultDnsResolutionWithAttackPatternAndArguments(
                        attackPatterns, payloadArguments, domains))
                .persist()
                .get();

    InjectorContract injectorContract =
        injectorContractComposer
            .forInjectorContract(
                InjectorContractFixture.createPayloadInjectorContract(
                    injectorFixture.getWellKnownOaevImplantInjector(), payload))
            .persist()
            .get();

    Map<String, Object> payloadArgumentMap =
        payloadArguments.stream()
            .collect(Collectors.toMap(PayloadArgument::getKey, PayloadArgument::getDefaultValue));

    return injectComposer
        .forInject(InjectFixture.createInjectWithPayloadArg(injectorContract, payloadArgumentMap))
        .persist()
        .get();
  }

  private Inject getInjectFileDropWithPlatformsAndArchitectureAndAttackPatternAndArguments()
      throws JsonProcessingException {
    List<AttackPattern> attackPatterns = saveAndGetAttackPatterns();
    List<PayloadArgument> payloadArguments = getPayloadArguments();
    Set<Domain> domains = domainComposer.forDomain(null).persist().getSet();

    FileDrop payload =
        (FileDrop)
            payloadComposer
                .forPayload(
                    PayloadFixture.createDefaultFileDropWithAttackPatternAndArguments(
                        attackPatterns, payloadArguments, domains))
                .withFileDrop(
                    documentComposer.forDocument(
                        DocumentFixture.getDocument(FileFixture.getPlainTextFileContent())))
                .persist()
                .get();

    InjectorContract injectorContract =
        injectorContractComposer
            .forInjectorContract(
                InjectorContractFixture.createPayloadInjectorContract(
                    injectorFixture.getWellKnownOaevImplantInjector(), payload))
            .persist()
            .get();

    Map<String, Object> payloadArgumentMap =
        payloadArguments.stream()
            .collect(Collectors.toMap(PayloadArgument::getKey, PayloadArgument::getDefaultValue));

    return injectComposer
        .forInject(InjectFixture.createInjectWithPayloadArg(injectorContract, payloadArgumentMap))
        .persist()
        .get();
  }

  private Inject getInjectExecutableWithPlatformsAndArchitectureAndAttackPatternAndArguments()
      throws JsonProcessingException {
    List<AttackPattern> attackPatterns = saveAndGetAttackPatterns();

    Set<Domain> domains = domainComposer.forDomain(null).persist().getSet();
    List<PayloadArgument> payloadArguments = getPayloadArguments();
    Executable payload =
        (Executable)
            payloadComposer
                .forPayload(
                    PayloadFixture.createDefaultExecutableWithAttackPatternAndArguments(
                        attackPatterns, payloadArguments, domains))
                .withExecutable(
                    documentComposer.forDocument(
                        DocumentFixture.getDocument(FileFixture.getPngGridFileContent())))
                .persist()
                .get();

    InjectorContract injectorContract =
        injectorContractComposer
            .forInjectorContract(
                InjectorContractFixture.createPayloadInjectorContract(
                    injectorFixture.getWellKnownOaevImplantInjector(), payload))
            .persist()
            .get();

    Map<String, Object> payloadArgumentMap =
        payloadArguments.stream()
            .collect(Collectors.toMap(PayloadArgument::getKey, PayloadArgument::getDefaultValue));
    return injectComposer
        .forInject(InjectFixture.createInjectWithPayloadArg(injectorContract, payloadArgumentMap))
        .persist()
        .get();
  }

  private List<PayloadArgument> getPayloadArguments() {
    PayloadArgument payloadArgumentText =
        PayloadFixture.createPayloadArgument("guest_user", ContractFieldType.Text, "guest", null);
    return new ArrayList<>(List.of(payloadArgumentText));
  }

  // Has to be Saved
  private List<AttackPattern> saveAndGetAttackPatterns() {
    AttackPattern attackPattern1 =
        attackPatternComposer
            .forAttackPattern(AttackPatternFixture.createDefaultAttackPattern())
            .persist()
            .get();
    AttackPattern attackPattern2 =
        attackPatternComposer
            .forAttackPattern(AttackPatternFixture.createDefaultAttackPattern())
            .persist()
            .get();
    AttackPattern attackPattern3 =
        attackPatternComposer
            .forAttackPattern(AttackPatternFixture.createDefaultAttackPattern())
            .persist()
            .get();
    return new ArrayList<>(Arrays.asList(attackPattern1, attackPattern2, attackPattern3));
  }

  private String getBadDetectionRemediationAIResponse() {
    return """
              <html>
              <head><title>Service Temporarily Unavailable</title></head>
              <body style="background-color: #070d19; font-family: 'IBM Plex Sans', Arial, Helvetica, sans-serif; font-size: 16px;">
              <div style="margin: 0 auto; width: 30%; margin-top: 300px; font-size: huge; color: #FFFFFF"><img
                      style="margin-bottom: 10px; width: 150px;" src="https://filigran.io/app/themes/filigran/src/images/logo.svg"/>
                  <p style="line-height: 32px;">The server encountered a temporary error and could not complete your request.<br/>Please
                      try again in a few minutes.</p>
                  <p style="margin-top: 10px; font-size: 14px;"><a href="https://status.filigran.io" style="color: #0fbcff">Infrastructure
                      Status</a> - <a href="https://support.filigran.io" style="color: #0fbcff">Technical Support</a></div>
              <span style="position: absolute; right: 20px; bottom: 20px; font-size: 12px; color: #616161">HTTP 503 Service Unavailable</span></p>
              </body>
              </html>
              """;
  }

  private String getDetectionRemediationAIResponseByCollector(String collectorType) {
    switch (collectorType) {
      case CollectorsUtils.CROWDSTRIKE -> {
        return """
                                               {
                                                 "success": true,
                                                 "rules": [
                                                   {
                                                     "rule_type": "Process Creation",
                                                     "action_to_take": "Monitor",
                                                     "severity": "Low",
                                                     "rule_name": "PowerShell Directory Traversal Command Execution",
                                                     "rule_description": "Monitors for the execution of the 'cd ..' directory traversal command via PowerShell, which may indicate reconnaissance or lateral movement activity.",
                                                     "tactic_technique": "Custom Intelligence via Indicator of Attack",
                                                     "field_configuration": {
                                                       "grandparent_image_filename": ".*",
                                                       "grandparent_command_line": ".*",
                                                       "parent_image_filename": ".*",
                                                       "parent_command_line": ".*",
                                                       "image_filename": ".*powershell\\\\.exe",
                                                       "command_line": ".*cd\\\\s+\\\\.\\\\..*",
                                                       "file_path": null,
                                                       "remote_ip_address": null,
                                                       "remote_port": null,
                                                       "connection_type": null,
                                                       "domain_name": null
                                                     },
                                                     "detection_strategy": "This rule detects the use of the 'cd ..' command executed by PowerShell, which is a common method for directory traversal and may be part of enumeration or lateral movement. By focusing on the process name and a simple command pattern, the rule is resilient to minor variations and easy to maintain, while minimizing false positives."
                                                   }
                                                 ],
                                                 "total_rules": 1,
                                                 "message": "Rules generated successfully"
                                               }
                        """;
      }
      case CollectorsUtils.SPLUNK -> {
        return """
                        {
                          "success": true,
                          "spl_query": "index=windows EventCode=4688 CommandLine=\\"*Invoke-WebRequest*\\" CommandLine=\\"*AnyDesk*\\" | stats count by Computer, User, CommandLine | sort -count",
                          "message": "SPL query generated successfully"
                        }
                        """;
      }
      default ->
          throw new IllegalStateException("Collector :\"" + collectorType + "\" unsupported");
    }
  }

  private void clearEntityManager() {
    entityManager.flush();
    entityManager.clear();
  }
}
