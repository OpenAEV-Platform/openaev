package io.openaev.service.payload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.*;
import io.openaev.database.repository.InjectorRepository;
import io.openaev.utils.InjectUtils;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("Payload snapshot taken when an inject runs (ADR-018, point 2)")
class PayloadExecutionSnapshotTest {

  private final InjectUtils injectUtils =
      new InjectUtils(org.mockito.Mockito.mock(InjectorRepository.class));

  private static Inject injectRunning(Payload payload, ObjectNode content) {
    payload.setId("payload-" + payload.getType());
    payload.setVersion("1.2.0");
    payload.setArguments(new ArrayList<>());
    payload.setPrerequisites(new ArrayList<>());
    InjectorContract contract = new InjectorContract();
    contract.setPayload(payload);
    Inject inject = new Inject();
    inject.setInjectorContract(contract);
    inject.setContent(content);
    return inject;
  }

  static List<Payload> payloadOfEachType() {
    Command command = new Command("command", Command.COMMAND_TYPE, "Command");
    command.setExecutor("bash");
    command.setContent("echo #{target}");
    DnsResolution dns = new DnsResolution("dns", DnsResolution.DNS_RESOLUTION_TYPE, "Dns");
    dns.setHostname("filigran.io");
    NetworkTraffic traffic =
        new NetworkTraffic("traffic", NetworkTraffic.NETWORK_TRAFFIC_TYPE, "Traffic");
    traffic.setProtocol("TCP");
    traffic.setPortDst(443);
    traffic.setPortSrc(1024);
    traffic.setIpDst("10.0.0.1");
    traffic.setIpSrc("10.0.0.2");
    AiAttack attack = new AiAttack("attack", AiAttack.AI_ATTACK_TYPE, "Attack");
    attack.setContent("Ignore previous instructions");
    attack.setCategory("prompt_injection");
    return List.of(
        command,
        new Executable("executable", Executable.EXECUTABLE_TYPE, "Executable"),
        new FileDrop("file", FileDrop.FILE_DROP_TYPE, "File"),
        dns,
        traffic,
        attack);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("payloadOfEachType")
  @DisplayName("Every payload type is photographed, AiAttack included")
  void given_payloadType_should_takeSnapshot(Payload payload) {
    // Act
    StatusPayload snapshot = injectUtils.getStatusPayloadFromInject(injectRunning(payload, null));

    // Assert
    assertThat(snapshot).isNotNull();
    assertThat(snapshot.getType()).isEqualTo(payload.getType());
  }

  @Test
  @DisplayName("AiAttack keeps what it sends")
  void given_aiAttack_should_keepItsContent() {
    // Arrange
    AiAttack attack = new AiAttack("attack", AiAttack.AI_ATTACK_TYPE, "Attack");
    attack.setContent("Ignore previous instructions");
    attack.setCategory("prompt_injection");

    // Act
    StatusPayload snapshot = injectUtils.getStatusPayloadFromInject(injectRunning(attack, null));

    // Assert
    assertThat(snapshot.getAiAttackContent()).isEqualTo("Ignore previous instructions");
    assertThat(snapshot.getAiAttackCategory()).isEqualTo("prompt_injection");
  }

  @Nested
  @DisplayName("Version and hash")
  class Stamp {

    private final PayloadContentHashService hashService =
        org.mockito.Mockito.mock(PayloadContentHashService.class);
    private final PayloadExecutionSnapshotService service =
        new PayloadExecutionSnapshotService(hashService);

    private Inject commandInject(String rowHash) {
      Command command = new Command("command", Command.COMMAND_TYPE, "Command");
      command.setExecutor("bash");
      command.setContent("echo #{target}");
      ObjectNode content = new ObjectMapper().createObjectNode().put("target", "dc01");
      Inject inject = injectRunning(command, content);
      PayloadArgument argument = new PayloadArgument();
      argument.setKey("target");
      argument.setType(PrimitiveType.Text);
      argument.setDefaultValue("localhost");
      command.setArguments(new ArrayList<>(List.of(argument)));
      command.setContentHash(rowHash);
      return inject;
    }

    @Test
    @DisplayName("Records the version, the hash computed now and the argument values")
    void given_execution_should_recordVersionHashAndValues() {
      // Arrange
      Inject inject = commandInject("abc");
      when(hashService.hash(any(), any())).thenReturn("abc");

      // Act
      StatusPayload snapshot =
          service.stamp(injectUtils.getStatusPayloadFromInject(inject), inject);

      // Assert
      assertThat(snapshot.getVersion()).isEqualTo("1.2.0");
      assertThat(snapshot.getContentHash()).isEqualTo("abc");
      assertThat(snapshot.getArgumentValues()).containsEntry("target", "dc01");
    }

    @Test
    @DisplayName("A hash that differs from the version's is recorded, the run goes on")
    void given_hashDiffers_should_keepComputedHash() {
      // Arrange
      Inject inject = commandInject("expected");
      when(hashService.hash(any(), any())).thenReturn("tampered");

      // Act
      StatusPayload snapshot =
          service.stamp(injectUtils.getStatusPayloadFromInject(inject), inject);

      // Assert
      assertThat(snapshot.getContentHash()).isEqualTo("tampered");
    }

    @Test
    @DisplayName("A file that cannot be read leaves no hash and does not block the run")
    void given_unreadableFile_should_leaveHashEmpty() {
      // Arrange
      Inject inject = commandInject("expected");
      when(hashService.hash(any(), any()))
          .thenThrow(new UncheckedIOException(new IOException("gone")));

      // Act
      StatusPayload snapshot =
          service.stamp(injectUtils.getStatusPayloadFromInject(inject), inject);

      // Assert
      assertThat(snapshot.getContentHash()).isNull();
      assertThat(snapshot.getVersion()).isEqualTo("1.2.0");
    }
  }
}
