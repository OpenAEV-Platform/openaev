package io.openaev.service.payload_approval;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.database.model.*;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("Payload content fingerprint")
class PayloadFingerprintTest {

  private static final List<Class<? extends Payload>> PAYLOAD_TYPES =
      List.of(
          Command.class,
          Executable.class,
          FileDrop.class,
          DnsResolution.class,
          NetworkTraffic.class,
          AiAttack.class);

  @Test
  @DisplayName("every payload field is classified as executable or cosmetic (fail-closed)")
  void given_everyPayloadField_should_beClassified() {
    Set<String> unclassified = new TreeSet<>();
    for (Class<?> type : PAYLOAD_TYPES) {
      for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
        for (Field field : c.getDeclaredFields()) {
          if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
            continue;
          }
          String name = field.getName();
          if (!PayloadFingerprint.EXECUTABLE_FIELDS.contains(name)
              && !PayloadFingerprint.COSMETIC_FIELDS.contains(name)) {
            unclassified.add(c.getSimpleName() + "." + name);
          }
        }
      }
    }
    assertThat(unclassified)
        .as(
            "a new payload field must be added to PayloadFingerprint.EXECUTABLE_FIELDS (and read in"
                + " PayloadFingerprint.of) or to COSMETIC_FIELDS")
        .isEmpty();
  }

  @Test
  @DisplayName("executable and cosmetic sets do not overlap")
  void given_bothSets_should_beDisjoint() {
    assertThat(PayloadFingerprint.EXECUTABLE_FIELDS)
        .doesNotContainAnyElementsOf(PayloadFingerprint.COSMETIC_FIELDS);
  }

  private static Command command() {
    Command command = new Command();
    command.setExecutor("sh");
    command.setContent("echo hello");
    command.setPlatforms(new Endpoint.PLATFORM_TYPE[] {Endpoint.PLATFORM_TYPE.Linux});
    command.setName("Hello");
    command.setDescription("Says hello");
    return command;
  }

  private static Document document(String id) {
    Document document = new Document();
    document.setId(id);
    return document;
  }

  static Stream<Arguments> executableChanges() {
    return Stream.of(
        change(
            "command content",
            PayloadFingerprintTest::command,
            p -> ((Command) p).setContent("rm -rf /tmp/x")),
        change(
            "command executor",
            PayloadFingerprintTest::command,
            p -> ((Command) p).setExecutor("bash")),
        change(
            "platforms",
            PayloadFingerprintTest::command,
            p -> p.setPlatforms(new Endpoint.PLATFORM_TYPE[] {Endpoint.PLATFORM_TYPE.Windows})),
        change(
            "cleanup command", PayloadFingerprintTest::command, p -> p.setCleanupCommand("rm x")),
        change(
            "cleanup executor", PayloadFingerprintTest::command, p -> p.setCleanupExecutor("psh")),
        change(
            "architecture",
            PayloadFingerprintTest::command,
            p -> p.setExecutionArch(Payload.PAYLOAD_EXECUTION_ARCH.arm64)),
        change("elevation", PayloadFingerprintTest::command, p -> p.setElevationRequired(true)),
        change(
            "argument default value",
            PayloadFingerprintTest::command,
            p -> {
              PayloadArgument argument = new PayloadArgument();
              argument.setKey("target");
              argument.setDefaultValue("10.0.0.1");
              p.setArguments(new ArrayList<>(List.of(argument)));
            }),
        change(
            "prerequisite",
            PayloadFingerprintTest::command,
            p -> {
              PayloadPrerequisite prerequisite = new PayloadPrerequisite();
              prerequisite.setExecutor("sh");
              prerequisite.setGetCommand("apt install x");
              p.setPrerequisites(new ArrayList<>(List.of(prerequisite)));
            }),
        change(
            "executable file",
            () -> {
              Executable executable = new Executable();
              executable.setExecutableFile(document("doc-1"));
              return executable;
            },
            p -> ((Executable) p).setExecutableFile(document("doc-2"))),
        change(
            "dropped file",
            () -> {
              FileDrop fileDrop = new FileDrop();
              fileDrop.setFileDropFile(document("doc-1"));
              return fileDrop;
            },
            p -> ((FileDrop) p).setFileDropFile(document("doc-2"))),
        change(
            "DNS hostname",
            () -> {
              DnsResolution dns = new DnsResolution();
              dns.setHostname("a.example.com");
              return dns;
            },
            p -> ((DnsResolution) p).setHostname("b.example.com")),
        change(
            "network destination",
            () -> {
              NetworkTraffic traffic = new NetworkTraffic();
              traffic.setIpDst("10.0.0.1");
              traffic.setPortDst(443);
              traffic.setProtocol("TCP");
              return traffic;
            },
            p -> ((NetworkTraffic) p).setPortDst(22)),
        change(
            "AI attack content",
            () -> {
              AiAttack ai = new AiAttack();
              ai.setContent("prompt");
              return ai;
            },
            p -> ((AiAttack) p).setContent("another prompt")),
        change(
            "AI attack engine",
            AiAttack::new,
            p -> ((AiAttack) p).setEngine(AiAttack.AI_ATTACK_ENGINE.GARAK)));
  }

  private static Arguments change(
      String name, Supplier<Payload> base, Consumer<Payload> modification) {
    return Arguments.of(name, base, modification);
  }

  @ParameterizedTest(name = "{0} changes the fingerprint")
  @MethodSource("executableChanges")
  void given_executableChange_should_changeFingerprint(
      String name, Supplier<Payload> base, Consumer<Payload> modification) {
    Payload original = base.get();
    Payload modified = base.get();
    modification.accept(modified);

    assertThat(PayloadFingerprint.of(modified)).isNotEqualTo(PayloadFingerprint.of(original));
  }

  @Test
  @DisplayName("cosmetic changes keep the fingerprint")
  void given_cosmeticChanges_should_keepFingerprint() {
    Command original = command();
    Command modified = command();
    modified.setName("Renamed");
    modified.setDescription("Another description");
    modified.setExternalId("ext-1");
    modified.setStatus(Payload.PAYLOAD_STATUS.VERIFIED);
    modified.setApprovalStatus(Payload.PAYLOAD_APPROVAL_STATUS.REJECTED);
    modified.setExpectations(
        new BaseInjectExpectation.EXPECTATION_TYPE[] {
          BaseInjectExpectation.EXPECTATION_TYPE.DETECTION
        });

    assertThat(PayloadFingerprint.of(modified)).isEqualTo(PayloadFingerprint.of(original));
  }

  @Test
  @DisplayName("platform order does not matter, the fingerprint is a 64-char hex digest")
  void given_samePlatformsInAnotherOrder_should_keepFingerprint() {
    Command a = command();
    a.setPlatforms(
        new Endpoint.PLATFORM_TYPE[] {
          Endpoint.PLATFORM_TYPE.Linux, Endpoint.PLATFORM_TYPE.Windows
        });
    Command b = command();
    b.setPlatforms(
        new Endpoint.PLATFORM_TYPE[] {
          Endpoint.PLATFORM_TYPE.Windows, Endpoint.PLATFORM_TYPE.Linux
        });

    assertThat(PayloadFingerprint.of(a)).isEqualTo(PayloadFingerprint.of(b)).hasSize(64);
  }
}
