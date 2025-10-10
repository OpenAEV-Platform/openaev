package io.openaev.service.detection_remediation;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.api.detection_remediation.dto.PayloadInput;
import io.openaev.database.model.*;
import io.openaev.injector_contract.fields.ContractFieldType;
import io.openaev.utils.fixtures.OutputParserFixture;
import io.openaev.utils.fixtures.PayloadFixture;
import io.openaev.utils.fixtures.TagFixture;
import io.openaev.utils.fixtures.files.AttackPatternFixture;
import io.openaev.utils.fixtures.payload_fixture.OutputParserInputFixture;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class DetectionRemediationRequestTest extends IntegrationTest {

  @Test
  public void getPayloadValueForWebserviceFromPayloadInput_Command() {
    Command payload = new Command();
    List<AttackPattern> attackPatterns = getAttackPatterns();

    List<String> attackPatternsIds =
        payload.getAttackPatterns().stream().map(AttackPattern::getId).toList();
    PayloadInput payloadInput = getPayloadInput(payload, attackPatternsIds);
    DetectionRemediationRequest detectionRemediationRequest =
        new DetectionRemediationRequest(payloadInput, attackPatterns);
    String payloadValue = detectionRemediationRequest.getPayload();
    assertThat(payloadValue)
        .isEqualTo(
            """
                        Name: VaultcmdCredentialsAccess
                        Type: Command
                        Command executor: cmd
                        Attack command: vaultcmd /listcreds:"Windows Credentials"
                        Description: Access Saved Credentials via VaultCmd

                        List credentials currently stored in Windows Credential Manager via the native Windows
                        utility vaultcmd.exe. Credential Manager stores credentials for signing into websites,
                        applications, and/or devices that request authentication through NTLM or Kerberos.

                        Platform : Linux
                        Attack patterns: [T1594]AttackPattern-T1594,
                         [T1491]AttackPattern-T1491,
                         [T1548]AttackPattern-T1548
                        Architecture: ALL_ARCHITECTURES
                        Arguments: guest_user : guest
                        """);
  }

  @Test
  public void getPayloadValueForWebserviceFromPayloadInput_DnsResolution() {
    DnsResolution payload = new DnsResolution();

    List<AttackPattern> attackPatterns = getAttackPatterns();

    List<String> attackPatternsIds =
        payload.getAttackPatterns().stream().map(AttackPattern::getId).toList();
    PayloadInput payloadInput = getPayloadInput(payload, attackPatternsIds);
    DetectionRemediationRequest detectionRemediationRequest =
        new DetectionRemediationRequest(payloadInput, attackPatterns);
    String payloadValue = detectionRemediationRequest.getPayload();
    assertThat(payloadValue)
        .isEqualTo(
            """
                        Name: VaultcmdCredentialsAccess
                        Type: DnsResolution
                        Hostname: example.com
                        Description: Access Saved Credentials via VaultCmd

                        List credentials currently stored in Windows Credential Manager via the native Windows
                        utility vaultcmd.exe. Credential Manager stores credentials for signing into websites,
                        applications, and/or devices that request authentication through NTLM or Kerberos.

                        Platform : Linux
                        Attack patterns: [T1594]AttackPattern-T1594,
                         [T1491]AttackPattern-T1491,
                         [T1548]AttackPattern-T1548
                        Architecture: ALL_ARCHITECTURES
                        Arguments: guest_user : guest
                        """);
  }

  @Test
  public void getPayloadValueForWebserviceFromPayloadInject_Command() {
    Command payload = new Command();
    List<AttackPattern> attackPatterns = getAttackPatterns();
    getPayload(payload, attackPatterns);
    DetectionRemediationRequest detectionRemediationRequest =
        new DetectionRemediationRequest(payload);

    String payloadValue = detectionRemediationRequest.getPayload();
    assertThat(payloadValue)
        .isEqualTo(
            """
                        Name: VaultcmdCredentialsAccess
                        Type: Command
                        Command executor: cmd
                        Attack command: vaultcmd /listcreds:"Windows Credentials"
                        Description: Access Saved Credentials via VaultCmd

                        List credentials currently stored in Windows Credential Manager via the native Windows
                        utility vaultcmd.exe. Credential Manager stores credentials for signing into websites,
                        applications, and/or devices that request authentication through NTLM or Kerberos.

                        Platform : Linux
                        Attack patterns: [T1594]AttackPattern-T1594,
                         [T1491]AttackPattern-T1491,
                         [T1548]AttackPattern-T1548
                        Architecture: ALL_ARCHITECTURES
                        Arguments: guest_user : guest
                        """);
  }

  @Test
  public void getPayloadValueForWebserviceFromPayloadInject_DnsResolution() {
    DnsResolution payload = new DnsResolution();
    List<AttackPattern> attackPatterns = getAttackPatterns();
    getPayload(payload, attackPatterns);
    DetectionRemediationRequest detectionRemediationRequest =
        new DetectionRemediationRequest(payload);

    String payloadValue = detectionRemediationRequest.getPayload();
    assertThat(payloadValue)
        .isEqualTo(
            """
                        Name: VaultcmdCredentialsAccess
                        Type: DnsResolution
                        Hostname: example.com
                        Description: Access Saved Credentials via VaultCmd

                        List credentials currently stored in Windows Credential Manager via the native Windows
                        utility vaultcmd.exe. Credential Manager stores credentials for signing into websites,
                        applications, and/or devices that request authentication through NTLM or Kerberos.

                        Platform : Linux
                        Attack patterns: [T1594]AttackPattern-T1594,
                         [T1491]AttackPattern-T1491,
                         [T1548]AttackPattern-T1548
                        Architecture: ALL_ARCHITECTURES
                        Arguments: guest_user : guest
                        """);
  }

  private PayloadInput getPayloadInput(Payload payload, List<String> attackPatternsIds) {

    PayloadInput input = new PayloadInput();
    // USED FOR DetectionRemediationRequest.payload value construction
    input.setType(payload.getType());
    input.setName("VaultcmdCredentialsAccess");
    input.setPlatforms(new Endpoint.PLATFORM_TYPE[] {Endpoint.PLATFORM_TYPE.Linux});
    input.setDescription(
        """
                Access Saved Credentials via VaultCmd

                List credentials currently stored in Windows Credential Manager via the native Windows
                utility vaultcmd.exe. Credential Manager stores credentials for signing into websites,
                applications, and/or devices that request authentication through NTLM or Kerberos.
                """);
    input.setExecutionArch(Payload.PAYLOAD_EXECUTION_ARCH.ALL_ARCHITECTURES);
    input.setArguments(getPayloadArguments());
    input.setAttackPatternsIds(attackPatternsIds);
    switch (payload) {
      case Command ignored -> {
        input.setExecutor("cmd");
        input.setContent("vaultcmd /listcreds:\"Windows Credentials\"");
      }
      case DnsResolution ignored -> input.setHostname("example.com");
      default -> {}
    }

    // -- MUST NOT BE USED FOR DetectionRemediationRequest.payload value construction --
    PayloadPrerequisite prerequisite = new PayloadPrerequisite();
    prerequisite.setExecutor("executor");
    prerequisite.setGetCommand("get_command");
    prerequisite.setCheckCommand("check_command");
    prerequisite.setDescription("description");
    input.setPrerequisites(List.of(prerequisite));

    input.setCleanupExecutor("sh");
    input.setCleanupCommand("rm /tmp/encoded.dat \n" + "rm /tmp/art.sh");

    input.setTagIds(List.of(TagFixture.getTag().getId()));
    input.setOutputParsers(Set.of(OutputParserInputFixture.createDefaultOutputParseInput()));
    return input;
  }

  private void getPayload(Payload payload, List<AttackPattern> attackPatterns) {

    // USED FOR DetectionRemediationRequest.payload value construction
    payload.setName("VaultcmdCredentialsAccess");
    payload.setPlatforms(new Endpoint.PLATFORM_TYPE[] {Endpoint.PLATFORM_TYPE.Linux});
    payload.setDescription(
        """
                Access Saved Credentials via VaultCmd

                List credentials currently stored in Windows Credential Manager via the native Windows
                utility vaultcmd.exe. Credential Manager stores credentials for signing into websites,
                applications, and/or devices that request authentication through NTLM or Kerberos.
                """);
    payload.setExecutionArch(Payload.PAYLOAD_EXECUTION_ARCH.ALL_ARCHITECTURES);
    payload.setArguments(getPayloadArguments());
    payload.setAttackPatterns(attackPatterns);
    switch (payload) {
      case Command command -> {
        command.setExecutor("cmd");
        command.setContent("vaultcmd /listcreds:\"Windows Credentials\"");
      }
      case DnsResolution dnsResolution -> dnsResolution.setHostname("example.com");
      default -> {}
    }

    // -- MUST NOT BE USED FOR DetectionRemediationRequest.payload value construction --
    PayloadPrerequisite prerequisite = new PayloadPrerequisite();
    prerequisite.setExecutor("executor");
    prerequisite.setGetCommand("get_command");
    prerequisite.setCheckCommand("check_command");
    prerequisite.setDescription("description");
    payload.setPrerequisites(List.of(prerequisite));

    payload.setCleanupExecutor("sh");
    payload.setCleanupCommand("rm /tmp/encoded.dat \n" + "rm /tmp/art.sh");

    payload.setTags(Set.of(TagFixture.getTag()));
    payload.setOutputParsers(Set.of(OutputParserFixture.getDefaultOutputParser()));
  }

  private List<PayloadArgument> getPayloadArguments() {
    PayloadArgument payloadArgumentText =
        PayloadFixture.createPayloadArgument("guest_user", ContractFieldType.Text, "guest", null);
    return new ArrayList<>(List.of(payloadArgumentText));
  }

  private List<AttackPattern> getAttackPatterns() {
    AttackPattern attackPattern1 = AttackPatternFixture.createAttackPatternsWithExternalId("T1594");
    AttackPattern attackPattern2 = AttackPatternFixture.createAttackPatternsWithExternalId("T1491");
    AttackPattern attackPattern3 = AttackPatternFixture.createAttackPatternsWithExternalId("T1548");
    return new ArrayList<>(Arrays.asList(attackPattern1, attackPattern2, attackPattern3));
  }
}
