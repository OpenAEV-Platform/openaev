package io.openaev.api.detection_remediation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.api.detection_remediation.dto.PayloadInput;
import io.openaev.authorisation.HttpClientFactory;
import io.openaev.collectors.utils.CollectorsUtils;
import io.openaev.database.model.*;
import io.openaev.database.repository.*;
import io.openaev.ee.Ee;
import io.openaev.injector_contract.fields.ContractFieldType;
import io.openaev.service.detection_remediation.DetectionRemediationAIResponse;
import io.openaev.service.detection_remediation.DetectionRemediationCrowdstrikeResponse;
import io.openaev.service.detection_remediation.DetectionRemediationSplunkResponse;
import io.openaev.utils.fixtures.*;
import io.openaev.utils.fixtures.composers.DocumentComposer;
import io.openaev.utils.fixtures.composers.PayloadComposer;
import io.openaev.utils.fixtures.files.AttackPatternFixture;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.annotation.Resource;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Get detection and remediation rule using AI")
public class DetectionRemediationApiTest extends IntegrationTest {

    @MockBean
    private Ee enterpriseEdition;

    @MockBean
    private CloseableHttpClient httpClient;

    @MockBean
    private HttpClientFactory httpClientFactory;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AttackPatternRepository attackPatternRepository;
    @Autowired
    private PayloadRepository payloadRepository;
    @Autowired
    private InjectorContractRepository injectorContractRepository;
    @Autowired
    private InjectRepository injectRepository;
    @Autowired
    private InjectorRepository injectorRepository;
    @Autowired
    private PayloadComposer payloadComposer;
    @Autowired
    private DocumentComposer documentComposer;

    @Resource
    protected ObjectMapper mapper;
    //@PostMapping("/rules/{collectorType}")
    @Test
    @DisplayName("Generate AI rules from CrowdStrike using a non‑persistent payload of type command without attack patterns and arguments")
    public void getDetectionRemediationRuleBasedOnPayloadCommandCrowdStrikeWithoutAttackPaterAndArguments() throws Exception {
        // -- PREPARE -

        PayloadComposer.Composer payloadCommand = payloadComposer.forPayload(
                PayloadFixture.createDefaultCommand());

        Command payload = (Command) payloadCommand.get();
        List<String> attackPatternsIds = payload.getAttackPatterns().stream()
                .map(AttackPattern::getId)
                .toList();
        PayloadInput input = getPayloadInput(payload, attackPatternsIds);
        when(enterpriseEdition.getEncodedCertificate()).thenReturn("certificate");

        // -- MOCKING EXTERNAL WEBSERVICE CALL --
        String detectionRemediationAIResponse = mapper.writeValueAsString(getDetectionRemediationAIResponseByCollector("openaev_crowdstrike"));
        when(httpClientFactory.httpClientCustom()).thenReturn(httpClient);
        Mockito.when(httpClient.execute(
                        Mockito.any(ClassicHttpRequest.class),
                        Mockito.any(HttpClientResponseHandler.class)))
                .thenAnswer(inv -> detectionRemediationAIResponse);

        // -- EXECUTE --
        String output = mockMvc
                .perform(
                        post("/" + DetectionRemediationApi.DETECTION_REMEDIATION_URI + "/rules/openaev_crowdstrike")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(mapper.writeValueAsString(input)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // -- ASSERT --
        String rules = JsonPath.read(output, "$.rules");
        assertThat(rules).isNotBlank();
        assertThat(rules).isEqualTo("<p>================================</p>\n" +
                "<p>Rule 1</p>\n" +
                "<p>Rule Type: Process Creation</p>\n" +
                "<p>Action to take: Monitor</p>\n" +
                "<p>Severity: Low</p>\n" +
                "<p>Rule name: PowerShell Directory Traversal Command Execution</p>\n" +
                "<p>Rule description: Monitors for the execution of the 'cd ..' directory traversal command via PowerShell, which may indicate reconnaissance or lateral movement activity.</p>\n" +
                "<p>Tactic & Technique: Custom Intelligence via Indicator of Attack</p>\n" +
                "<p>Detection Strategy: This rule detects the use of the 'cd ..' command executed by PowerShell, which is a common method for directory traversal and may be part of enumeration or lateral movement. By focusing on the process name and a simple command pattern, the rule is resilient to minor variations and easy to maintain, while minimizing false positives.</p>\n" +
                "<p>Field Configuration: </p>\n" +
                "<ul><li>Grandparent Image Filename: .*</li>\n" +
                "<li>Grandparent Command Line: .*</li>\n" +
                "<li>Parent Image Filename: .*</li>\n" +
                "<li>Parent Command Line: .*</li>\n" +
                "<li>Image Filename: .*powershell\\.exe</li>\n" +
                "<li>Command Line: .*cd\\s+\\.\\..*</li>\n" +
                "</ul>");

    }

    @Test
    @DisplayName("Generate AI rules from CrowdStrike using a non‑persistent payload of type commande")
    public void getDetectionRemediationRuleBasedOnPayloadCommandCrowdStrike() throws Exception {
        // -- PREPARE -
        List<AttackPattern> attackPatterns = saveAndGetAttackPatterns();

        List<String> attackPatternsIds = attackPatterns.stream()
                .map(AttackPattern::getId)
                .toList();

        List<PayloadArgument> payloadArguments = getPayloadArguments();

        PayloadComposer.Composer payloadCommand = payloadComposer.forPayload(
                PayloadFixture.createDefaultCommandWithPlatformsAndArchitectureAndAttackPatternAndArguments(
                        attackPatterns, payloadArguments));

        Command payload = (Command) payloadCommand.get();

        PayloadInput input = getPayloadInput(payload, attackPatternsIds);
        when(enterpriseEdition.getEncodedCertificate()).thenReturn("certificate");

        // -- MOCKING EXTERNAL WEBSERVICE CALL --
        String detectionRemediationAIResponse = mapper.writeValueAsString(getDetectionRemediationAIResponseByCollector("openaev_crowdstrike"));
        when(httpClientFactory.httpClientCustom()).thenReturn(httpClient);
        Mockito.when(httpClient.execute(
                        Mockito.any(ClassicHttpRequest.class),
                        Mockito.any(HttpClientResponseHandler.class)))
                .thenAnswer(inv -> detectionRemediationAIResponse);

        // -- EXECUTE --
        String output = mockMvc
                .perform(
                        post("/" + DetectionRemediationApi.DETECTION_REMEDIATION_URI + "/rules/openaev_crowdstrike")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(mapper.writeValueAsString(input)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // -- ASSERT --
        String rules = JsonPath.read(output, "$.rules");
        assertThat(rules).isNotBlank();
        assertThat(rules).isEqualTo("<p>================================</p>\n" +
                "<p>Rule 1</p>\n" +
                "<p>Rule Type: Process Creation</p>\n" +
                "<p>Action to take: Monitor</p>\n" +
                "<p>Severity: Low</p>\n" +
                "<p>Rule name: PowerShell Directory Traversal Command Execution</p>\n" +
                "<p>Rule description: Monitors for the execution of the 'cd ..' directory traversal command via PowerShell, which may indicate reconnaissance or lateral movement activity.</p>\n" +
                "<p>Tactic & Technique: Custom Intelligence via Indicator of Attack</p>\n" +
                "<p>Detection Strategy: This rule detects the use of the 'cd ..' command executed by PowerShell, which is a common method for directory traversal and may be part of enumeration or lateral movement. By focusing on the process name and a simple command pattern, the rule is resilient to minor variations and easy to maintain, while minimizing false positives.</p>\n" +
                "<p>Field Configuration: </p>\n" +
                "<ul><li>Grandparent Image Filename: .*</li>\n" +
                "<li>Grandparent Command Line: .*</li>\n" +
                "<li>Parent Image Filename: .*</li>\n" +
                "<li>Parent Command Line: .*</li>\n" +
                "<li>Image Filename: .*powershell\\.exe</li>\n" +
                "<li>Command Line: .*cd\\s+\\.\\..*</li>\n" +
                "</ul>");

    }

    @Test
    @DisplayName("Generate AI rules from Splunk using a non‑persistent payload of type commande")
    public void getDetectionRemediationRuleBasedOnPayloadCommandSplunk() throws Exception {
        // -- PREPARE -
        List<AttackPattern> attackPatterns = saveAndGetAttackPatterns();

        List<String> attackPatternsIds = attackPatterns.stream()
                .map(AttackPattern::getId)
                .toList();

        List<PayloadArgument> payloadArguments = getPayloadArguments();

        PayloadComposer.Composer payloadCommand = payloadComposer.forPayload(
                PayloadFixture.createDefaultCommandWithPlatformsAndArchitectureAndAttackPatternAndArguments(
                        attackPatterns, payloadArguments));

        Command payload = (Command) payloadCommand.get();

        PayloadInput input = getPayloadInput(payload, attackPatternsIds);
        when(enterpriseEdition.getEncodedCertificate()).thenReturn("certificate");

        // -- MOCKING EXTERNAL WEBSERVICE CALL --
        String detectionRemediationAIResponse = mapper.writeValueAsString(getDetectionRemediationAIResponseByCollector("openaev_splunk_es"));
        when(httpClientFactory.httpClientCustom()).thenReturn(httpClient);
        Mockito.when(httpClient.execute(
                        Mockito.any(ClassicHttpRequest.class),
                        Mockito.any(HttpClientResponseHandler.class)))
                .thenAnswer(inv -> detectionRemediationAIResponse);

        // -- EXECUTE --
        String output = mockMvc
                .perform(
                        post("/" + DetectionRemediationApi.DETECTION_REMEDIATION_URI + "/rules/openaev_splunk_es")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(mapper.writeValueAsString(input)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        // -- ASSERT --
        String rules = JsonPath.read(output, "$.rules");
        assertThat(rules).isNotBlank();
        assertThat(rules).isEqualTo("index=windows EventCode=4688 CommandLine=\"*Invoke-WebRequest*\" CommandLine=\"*AnyDesk*\" | stats count by Computer, User, CommandLine | sort -count");


    }

    @Test
    @DisplayName("Generate AI rules from CrowdStrike using a non‑persistent payload of type DnsResolution")
    public void getDetectionRemediationRuleBasedOnPayloadDnsResolutionCrowdStrike() throws Exception {
        // -- PREPARE -
        List<AttackPattern> attackPatterns = saveAndGetAttackPatterns();

        List<String> attackPatternsIds = attackPatterns.stream()
                .map(AttackPattern::getId)
                .toList();

        List<PayloadArgument> payloadArguments = getPayloadArguments();

        PayloadComposer.Composer payloadDnsResolution = payloadComposer.forPayload(
                PayloadFixture.createDefaultDnsResolutionWithAttackPatternAndArguments(
                        attackPatterns, payloadArguments));

        DnsResolution payload = (DnsResolution) payloadDnsResolution.get();

        PayloadInput input = getPayloadInput(payload, attackPatternsIds);
        when(enterpriseEdition.getEncodedCertificate()).thenReturn("certificate");

        // -- MOCKING EXTERNAL WEBSERVICE CALL --
        String detectionRemediationAIResponse = mapper.writeValueAsString(getDetectionRemediationAIResponseByCollector("openaev_crowdstrike"));
        when(httpClientFactory.httpClientCustom()).thenReturn(httpClient);
        Mockito.when(httpClient.execute(
                        Mockito.any(ClassicHttpRequest.class),
                        Mockito.any(HttpClientResponseHandler.class)))
                .thenAnswer(inv -> detectionRemediationAIResponse);

        // -- EXECUTE --
        String output = mockMvc
                .perform(
                        post("/" + DetectionRemediationApi.DETECTION_REMEDIATION_URI + "/rules/openaev_crowdstrike")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(mapper.writeValueAsString(input)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // -- ASSERT --
        String rules = JsonPath.read(output, "$.rules");
        assertThat(rules).isNotBlank();
        assertThat(rules).isEqualTo("<p>================================</p>\n" +
                "<p>Rule 1</p>\n" +
                "<p>Rule Type: Process Creation</p>\n" +
                "<p>Action to take: Monitor</p>\n" +
                "<p>Severity: Low</p>\n" +
                "<p>Rule name: PowerShell Directory Traversal Command Execution</p>\n" +
                "<p>Rule description: Monitors for the execution of the 'cd ..' directory traversal command via PowerShell, which may indicate reconnaissance or lateral movement activity.</p>\n" +
                "<p>Tactic & Technique: Custom Intelligence via Indicator of Attack</p>\n" +
                "<p>Detection Strategy: This rule detects the use of the 'cd ..' command executed by PowerShell, which is a common method for directory traversal and may be part of enumeration or lateral movement. By focusing on the process name and a simple command pattern, the rule is resilient to minor variations and easy to maintain, while minimizing false positives.</p>\n" +
                "<p>Field Configuration: </p>\n" +
                "<ul><li>Grandparent Image Filename: .*</li>\n" +
                "<li>Grandparent Command Line: .*</li>\n" +
                "<li>Parent Image Filename: .*</li>\n" +
                "<li>Parent Command Line: .*</li>\n" +
                "<li>Image Filename: .*powershell\\.exe</li>\n" +
                "<li>Command Line: .*cd\\s+\\.\\..*</li>\n" +
                "</ul>");

    }

    @Test
    @DisplayName("Generate AI rules from Splunk using a non‑persistent payload of type DnsResolution")
    public void getDetectionRemediationRuleBasedOnPayloadDnsResolutionSplunk() throws Exception {
        // -- PREPARE -
        List<AttackPattern> attackPatterns = saveAndGetAttackPatterns();

        List<String> attackPatternsIds = attackPatterns.stream()
                .map(AttackPattern::getId)
                .toList();

        List<PayloadArgument> payloadArguments = getPayloadArguments();

        PayloadComposer.Composer payloadDnsResolution = payloadComposer.forPayload(
                PayloadFixture.createDefaultDnsResolutionWithAttackPatternAndArguments(
                        attackPatterns, payloadArguments));

        DnsResolution payload = (DnsResolution) payloadDnsResolution.get();

        PayloadInput input = getPayloadInput(payload, attackPatternsIds);
        when(enterpriseEdition.getEncodedCertificate()).thenReturn("certificate");

        // -- MOCKING EXTERNAL WEBSERVICE CALL --
        String detectionRemediationAIResponse = mapper.writeValueAsString(getDetectionRemediationAIResponseByCollector("openaev_splunk_es"));
        when(httpClientFactory.httpClientCustom()).thenReturn(httpClient);
        Mockito.when(httpClient.execute(
                        Mockito.any(ClassicHttpRequest.class),
                        Mockito.any(HttpClientResponseHandler.class)))
                .thenAnswer(inv -> detectionRemediationAIResponse);

        // -- EXECUTE --
        String output = mockMvc
                .perform(
                        post("/" + DetectionRemediationApi.DETECTION_REMEDIATION_URI + "/rules/openaev_splunk_es")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(mapper.writeValueAsString(input)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        // -- ASSERT --
        String rules = JsonPath.read(output, "$.rules");
        assertThat(rules).isNotBlank();
        assertThat(rules).isEqualTo("index=windows EventCode=4688 CommandLine=\"*Invoke-WebRequest*\" CommandLine=\"*AnyDesk*\" | stats count by Computer, User, CommandLine | sort -count");


    }

    @Test
    @DisplayName("Generate AI rules from CrowdStrike using a non‑persistent payload of type FileDrop")
    public void getDetectionRemediationRuleBasedOnPayloadFileDropCrowdStrike() throws Exception {
        // -- PREPARE -
        List<AttackPattern> attackPatterns = saveAndGetAttackPatterns();

        List<String> attackPatternsIds = attackPatterns.stream()
                .map(AttackPattern::getId)
                .toList();

        List<PayloadArgument> payloadArguments = getPayloadArguments();

        PayloadComposer.Composer payloadFileDrop = payloadComposer.forPayload(
                PayloadFixture.createDefaultFileDropWithAttackPatternAndArguments(
                        attackPatterns, payloadArguments)).withFileDrop(
                documentComposer.forDocument(
                        DocumentFixture.getDocument(
                                FileFixture.getPlainTextFileContent())));

        FileDrop payload = (FileDrop) payloadFileDrop.get();

        PayloadInput input = getPayloadInput(payload, attackPatternsIds);

        // -- EXECUTE --
        ResultActions output = mockMvc
                .perform(
                        post("/" + DetectionRemediationApi.DETECTION_REMEDIATION_URI + "/rules/openaev_crowdstrike")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(mapper.writeValueAsString(input)));

        // -- ASSERT --
        output.andExpect(status().isNotImplemented());

    }

    @Test
    @DisplayName("Generate AI rules from Splunk using a non‑persistent payload of type FileDrop")
    public void getDetectionRemediationRuleBasedOnPayloadFileDropSplunk() throws Exception {
        // -- PREPARE -
        List<AttackPattern> attackPatterns = saveAndGetAttackPatterns();

        List<String> attackPatternsIds = attackPatterns.stream()
                .map(AttackPattern::getId)
                .toList();

        List<PayloadArgument> payloadArguments = getPayloadArguments();

        PayloadComposer.Composer payloadFileDrop = payloadComposer.forPayload(
                PayloadFixture.createDefaultFileDropWithAttackPatternAndArguments(
                        attackPatterns, payloadArguments)).withFileDrop(
                documentComposer.forDocument(
                        DocumentFixture.getDocument(
                                FileFixture.getPlainTextFileContent())));

        FileDrop payload = (FileDrop) payloadFileDrop.get();

        PayloadInput input = getPayloadInput(payload, attackPatternsIds);

        // -- EXECUTE --
        ResultActions output = mockMvc
                .perform(
                        post("/" + DetectionRemediationApi.DETECTION_REMEDIATION_URI + "/rules/openaev_splunk_es")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(mapper.writeValueAsString(input)));

        // -- ASSERT --
        output.andExpect(status().isNotImplemented());
    }

    @Test
    @DisplayName("Generate AI rules from CrowdStrike using a non‑persistent payload of type Executable")
    public void getDetectionRemediationRuleBasedOnPayloadExecutableCrowdStrike() throws Exception {
        // -- PREPARE -
        List<AttackPattern> attackPatterns = saveAndGetAttackPatterns();

        List<String> attackPatternsIds = attackPatterns.stream()
                .map(AttackPattern::getId)
                .toList();

        List<PayloadArgument> payloadArguments = getPayloadArguments();

        PayloadComposer.Composer payloadExecutable = payloadComposer.forPayload(
                PayloadFixture.createDefaultExecutableWithAttackPatternAndArguments(
                        attackPatterns, payloadArguments)).withExecutable(
                documentComposer.forDocument(
                        DocumentFixture.getDocument(
                                FileFixture.getPngGridFileContent())));

        Executable payload = (Executable) payloadExecutable.get();

        PayloadInput input = getPayloadInput(payload, attackPatternsIds);

        // -- EXECUTE --
        ResultActions output = mockMvc
                .perform(
                        post("/" + DetectionRemediationApi.DETECTION_REMEDIATION_URI + "/rules/openaev_crowdstrike")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(mapper.writeValueAsString(input)));

        // -- ASSERT --
        output.andExpect(status().isNotImplemented());
    }

    @Test
    @DisplayName("Generate AI rules from Splunk using a non‑persistent payload of type Executable")
    public void getDetectionRemediationRuleBasedOnPayloadExecutableSplunk() throws Exception {
        // -- PREPARE -
        List<AttackPattern> attackPatterns = saveAndGetAttackPatterns();

        List<String> attackPatternsIds = attackPatterns.stream()
                .map(AttackPattern::getId)
                .toList();

        List<PayloadArgument> payloadArguments = getPayloadArguments();

        PayloadComposer.Composer payloadExecutable = payloadComposer.forPayload(
                PayloadFixture.createDefaultExecutableWithAttackPatternAndArguments(
                        attackPatterns, payloadArguments)).withExecutable(
                documentComposer.forDocument(
                        DocumentFixture.getDocument(
                                FileFixture.getPngGridFileContent())));

        Executable payload = (Executable) payloadExecutable.get();

        PayloadInput input = getPayloadInput(payload, attackPatternsIds);

        // -- EXECUTE --
        ResultActions output = mockMvc
                .perform(
                        post("/" + DetectionRemediationApi.DETECTION_REMEDIATION_URI + "/rules/openaev_splunk_es")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(mapper.writeValueAsString(input)));

        // -- ASSERT --
        output.andExpect(status().isNotImplemented());
    }

    //   @PostMapping("rules/inject/{injectId}/collector/{collectorType}")
    @Test
    @DisplayName("Generate AI rules from CrowdStrike using an inject id of type commande")
    public void getDetectionRemediationRuleBasedOnInjectCommandCrowdStrike() throws Exception {
        // -- PREPARE -

        Inject inject = getInjectCommandWithPlatformsAndArchitectureAndAttackPatternAndArguments();

        when(enterpriseEdition.getEncodedCertificate()).thenReturn("certificate");

        // -- MOCKING EXTERNAL WEBSERVICE CALL --
        String detectionRemediationAIResponse = mapper.writeValueAsString(getDetectionRemediationAIResponseByCollector("openaev_crowdstrike"));
        when(httpClientFactory.httpClientCustom()).thenReturn(httpClient);
        Mockito.when(httpClient.execute(
                        Mockito.any(ClassicHttpRequest.class),
                        Mockito.any(HttpClientResponseHandler.class)))
                .thenAnswer(inv -> detectionRemediationAIResponse);

        // -- EXECUTE --
        String output = mockMvc
                .perform(
                        post("/" + DetectionRemediationApi.DETECTION_REMEDIATION_URI +
                                "/rules/inject/"+inject.getId()+"/collector/openaev_crowdstrike"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // -- ASSERT --
        String rules = JsonPath.read(output, "$.rules");
        assertThat(rules).isNotBlank();
        assertThat(rules).isEqualTo("<p>================================</p>\n" +
                "<p>Rule 1</p>\n" +
                "<p>Rule Type: Process Creation</p>\n" +
                "<p>Action to take: Monitor</p>\n" +
                "<p>Severity: Low</p>\n" +
                "<p>Rule name: PowerShell Directory Traversal Command Execution</p>\n" +
                "<p>Rule description: Monitors for the execution of the 'cd ..' directory traversal command via PowerShell, which may indicate reconnaissance or lateral movement activity.</p>\n" +
                "<p>Tactic & Technique: Custom Intelligence via Indicator of Attack</p>\n" +
                "<p>Detection Strategy: This rule detects the use of the 'cd ..' command executed by PowerShell, which is a common method for directory traversal and may be part of enumeration or lateral movement. By focusing on the process name and a simple command pattern, the rule is resilient to minor variations and easy to maintain, while minimizing false positives.</p>\n" +
                "<p>Field Configuration: </p>\n" +
                "<ul><li>Grandparent Image Filename: .*</li>\n" +
                "<li>Grandparent Command Line: .*</li>\n" +
                "<li>Parent Image Filename: .*</li>\n" +
                "<li>Parent Command Line: .*</li>\n" +
                "<li>Image Filename: .*powershell\\.exe</li>\n" +
                "<li>Command Line: .*cd\\s+\\.\\..*</li>\n" +
                "</ul>");

    }
    // -- HELPER --
    private Inject getInjectCommandWithPlatformsAndArchitectureAndAttackPatternAndArguments() throws JsonProcessingException {
        List<AttackPattern> attackPatterns = saveAndGetAttackPatterns();

        List<String> attackPatternsIds = attackPatterns.stream()
                .map(AttackPattern::getId)
                .toList();

        List<PayloadArgument> payloadArguments = getPayloadArguments();
        Command payloadCommand =
                (Command) PayloadFixture.createDefaultCommandWithPlatformsAndArchitectureAndAttackPatternAndArguments(
                        attackPatterns, payloadArguments);
        Payload payloadSaved = payloadRepository.save(payloadCommand);

        Injector injector = injectorRepository.findByType("openaev_implant").orElseThrow();
        InjectorContract injectorContract =
                InjectorContractFixture.createPayloadInjectorContract(injector, payloadSaved);
        InjectorContract injectorContractSaved = injectorContractRepository.save(injectorContract);

        String argValue = "Hello world";
        /*Map<String, Object> payloadArguments = new HashMap<>();
        payloadArguments.put("arg_value", argValue);*/
        Map<String, Object> payloadArgumentMap = payloadArguments.stream()
                .collect(Collectors.toMap(PayloadArgument::getKey, PayloadArgument::getDefaultValue));
        Inject inject =
                InjectFixture.createInjectCommandPayload(injectorContractSaved, payloadArgumentMap);

        return injectRepository.save(inject);
    }
    private PayloadInput getPayloadInput(Payload payload, List<String> attackPatternsIds) {

        PayloadInput input = new PayloadInput();
        input.setType(payload.getType());
        input.setName(payload.getName());
        input.setPlatforms(payload.getPlatforms());
        input.setDescription(payload.getDescription());
        input.setExecutionArch(payload.getExecutionArch());
        input.setArguments(payload.getArguments());
        input.setPrerequisites(payload.getPrerequisites());
        input.setCleanupExecutor(payload.getCleanupExecutor());
        input.setCleanupCommand(payload.getCleanupCommand());
        input.setTagIds(new ArrayList<>());
        input.setDetectionRemediations(new ArrayList<>());
        input.setOutputParsers(new HashSet<>());
        input.setAttackPatternsIds(attackPatternsIds);
        if (payload instanceof Command) {
            input.setExecutor(((Command) payload).getExecutor());
            input.setContent(((Command) payload).getContent());
        } else if (payload instanceof DnsResolution) {
            input.setHostname(((DnsResolution) payload).getHostname());
        } else if (payload instanceof Executable executable) {
            executable.setExecutableFile(((Executable) payload).getExecutableFile());
        } else if (payload instanceof FileDrop fileDrop) {
            fileDrop.setFileDropFile(((FileDrop) payload).getFileDropFile());
        }

        return input;
    }

    private List<PayloadArgument> getPayloadArguments() {
        PayloadArgument payloadArgumentText = PayloadFixture.createPayloadArgument("guest_user", ContractFieldType.Text, "guest", null);
        return new ArrayList<>(Arrays.asList(payloadArgumentText));
    }

    //Has to be Saved
    private List<AttackPattern> saveAndGetAttackPatterns() {
        AttackPattern attackPattern1 =
                attackPatternRepository.save(AttackPatternFixture.createDefaultAttackPattern());
        AttackPattern attackPattern2 =
                attackPatternRepository.save(AttackPatternFixture.createDefaultAttackPattern());
        AttackPattern attackPattern3 =
                attackPatternRepository.save(AttackPatternFixture.createDefaultAttackPattern());
        return new ArrayList<>(Arrays.asList(attackPattern1, attackPattern2, attackPattern3));

    }

    private DetectionRemediationAIResponse getDetectionRemediationAIResponseByCollector(String collectorType) throws JsonProcessingException {
        switch (collectorType) {
            case CollectorsUtils.CROWDSTRIKE -> {
                String jsonResponse = """
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
                return mapper.readValue(jsonResponse, DetectionRemediationCrowdstrikeResponse.class);

            }
            case CollectorsUtils.SPLUNK -> {
                String jsonResponse = """
                        {
                          "success": true,
                          "spl_query": "index=windows EventCode=4688 CommandLine=\\"*Invoke-WebRequest*\\" CommandLine=\\"*AnyDesk*\\" | stats count by Computer, User, CommandLine | sort -count",
                          "message": "SPL query generated successfully"
                        }
                        """;
                return mapper.readValue(jsonResponse, DetectionRemediationSplunkResponse.class);

            }
            default -> throw new IllegalStateException("Collector :\"" + collectorType + "\" unsupported");

        }

    }
}
