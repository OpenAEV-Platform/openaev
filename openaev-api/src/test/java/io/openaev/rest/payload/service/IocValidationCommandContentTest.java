package io.openaev.rest.payload.service;

import static io.openaev.rest.payload.service.PayloadService.IOC_VALIDATION_FILE_NAME_KEY;
import static io.openaev.rest.payload.service.PayloadService.IOC_VALIDATION_RUN_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.Command;
import io.openaev.database.model.IocValidationTestKind;
import io.openaev.database.model.PayloadArgument;
import io.openaev.database.model.PayloadPrerequisite;
import io.openaev.database.model.PrimitiveType;
import io.openaev.database.model.Tenant;
import io.openaev.utils.command.CommandArgumentBinder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("IOC validation benign command content")
class IocValidationCommandContentTest {

  private static final String RUN = "#{" + IOC_VALIDATION_RUN_KEY + "}";
  private static final String FILE_NAME = "#{" + IOC_VALIDATION_FILE_NAME_KEY + "}";

  @Test
  @DisplayName("the HTTP HEAD test always goes through the egress proxy, whatever NO_PROXY says")
  void given_httpHeadOnUnix_should_forceTheProxy() {
    String content =
        PayloadService.iocValidationCommandContent(IocValidationTestKind.HTTP_HEAD, false);
    assertThat(content).contains("--noproxy ''").contains("--proxy ");
    assertThat(content.indexOf("--noproxy ''")).isLessThan(content.indexOf("--proxy "));
  }

  @Test
  @DisplayName("the Windows HTTP HEAD test passes the egress proxy explicitly")
  void given_httpHeadOnWindows_should_passTheProxy() {
    String content =
        PayloadService.iocValidationCommandContent(IocValidationTestKind.HTTP_HEAD, true);
    assertThat(content).contains("-Proxy ");
  }

  @Test
  @DisplayName(
      "the file drop run and file name have no default: an inject without its own is refused")
  void given_fileDropArguments_should_haveNoDefaultRun() {
    assertThat(PayloadService.iocValidationArguments(IocValidationTestKind.FILE_DROP))
        .extracting(PayloadArgument::getKey, PayloadArgument::getDefaultValue)
        .containsExactly(
            tuple(IOC_VALIDATION_FILE_NAME_KEY, ""), tuple(IOC_VALIDATION_RUN_KEY, ""));
  }

  private static Command fileDropPayload(String executor, String tenantId) {
    Command payload = new Command();
    payload.setId(
        PayloadService.iocValidationPayloadId(IocValidationTestKind.FILE_DROP, executor, tenantId));
    payload.setTenant(new Tenant(tenantId));
    payload.setExecutor(executor);
    payload.setArguments(
        new ArrayList<>(PayloadService.iocValidationArguments(IocValidationTestKind.FILE_DROP)));
    return payload;
  }

  @Test
  @DisplayName("the run directory is named on the server after the inject, whatever its content")
  void given_executionContent_should_bindTheRunToTheInject() {
    Command payload = fileDropPayload(PayloadService.IOC_VALIDATION_POSIX_EXECUTOR, "tenant-a");
    ObjectNode content = JsonNodeFactory.instance.objectNode();
    content.put(IOC_VALIDATION_RUN_KEY, "0123456789abcdef0123456789abcdef");
    content.put(IOC_VALIDATION_FILE_NAME_KEY, "invoice.pdf");

    String run =
        PayloadService.iocValidationExecutionContent(content, payload, "inject-a")
            .get(IOC_VALIDATION_RUN_KEY)
            .asText();
    // Another inject whose content was given the same run
    String copied =
        PayloadService.iocValidationExecutionContent(content, payload, "inject-b")
            .get(IOC_VALIDATION_RUN_KEY)
            .asText();

    assertThat(run).matches("[0-9a-f]{32}").isNotEqualTo(copied);
    assertThat(
            PayloadService.iocValidationExecutionContent(content, payload, "inject-a")
                .get(IOC_VALIDATION_RUN_KEY)
                .asText())
        .isEqualTo(run);
    assertThat(content.get(IOC_VALIDATION_RUN_KEY).asText())
        .isEqualTo("0123456789abcdef0123456789abcdef");
    Command windows = fileDropPayload(PayloadService.IOC_VALIDATION_WINDOWS_EXECUTOR, "tenant-a");
    assertThat(
            PayloadService.iocValidationExecutionContent(content, windows, "inject-a")
                .get(IOC_VALIDATION_RUN_KEY)
                .asText())
        .isEqualTo(run);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "../../escape",
        "0123456789ABCDEF0123456789ABCDEF",
        "0123456789abcdef0123456789abcde",
        "0123456789abcdef0123456789abcdef0",
        "0123456789abcdef0123456789abcdef\n",
        "0123456789abcdef\u00000123456789abcdef",
        "\t0123456789abcdef0123456789abcdef\r"
      })
  @DisplayName(
      "a malformed run stays invalid after the binder sanitization: the endpoint refuses it")
  void given_malformedRun_should_stayInvalidOnceBound(String seed) {
    Command payload = fileDropPayload(PayloadService.IOC_VALIDATION_POSIX_EXECUTOR, "tenant-a");
    ObjectNode content = JsonNodeFactory.instance.objectNode();
    content.put(IOC_VALIDATION_RUN_KEY, seed);

    String run =
        PayloadService.iocValidationExecutionContent(content, payload, "inject-a")
            .get(IOC_VALIDATION_RUN_KEY)
            .asText();

    assertThat(run).isEqualTo(PayloadService.IOC_VALIDATION_INVALID_RUN);
    CommandArgumentBinder binder = CommandArgumentBinder.forExecutor("sh");
    binder.bind(IOC_VALIDATION_RUN_KEY, run);
    assertThat(binder.render(RUN)).doesNotContainPattern("[0-9a-f]{32}");
    assertThat(content.get(IOC_VALIDATION_RUN_KEY).asText()).isEqualTo(seed);
  }

  @Test
  @DisplayName("only a file drop payload running the current template may be executed")
  void given_fileDropPayload_should_recogniseTheCurrentTemplate() {
    Command current = currentFileDrop();
    assertThat(PayloadService.isCurrentIocValidationFileDropTemplate(current)).isTrue();

    Command legacyCommand = currentFileDrop();
    legacyCommand.setContent("printf 'x' > \"${TMPDIR:-/tmp}/\"" + FILE_NAME + "; true");
    assertThat(PayloadService.isCurrentIocValidationFileDropTemplate(legacyCommand)).isFalse();

    // Executors are editable and decide how the command and the cleanup are bound and run
    Command otherExecutor = currentFileDrop();
    otherExecutor.setExecutor("bash");
    assertThat(PayloadService.isCurrentIocValidationFileDropTemplate(otherExecutor)).isFalse();
    Command withoutCleanupExecutor = currentFileDrop();
    withoutCleanupExecutor.setCleanupExecutor(null);
    assertThat(PayloadService.isCurrentIocValidationFileDropTemplate(withoutCleanupExecutor))
        .isFalse();

    // A prerequisite runs before the command, an elevation runs it with more rights
    Command withPrerequisite = currentFileDrop();
    PayloadPrerequisite prerequisite = new PayloadPrerequisite();
    prerequisite.setExecutor(PayloadService.IOC_VALIDATION_POSIX_EXECUTOR);
    prerequisite.setGetCommand("curl -s https://downloads.example.com/tool");
    withPrerequisite.setPrerequisites(new ArrayList<>(List.of(prerequisite)));
    assertThat(PayloadService.isCurrentIocValidationFileDropTemplate(withPrerequisite)).isFalse();
    Command elevated = currentFileDrop();
    elevated.setElevationRequired(true);
    assertThat(PayloadService.isCurrentIocValidationFileDropTemplate(elevated)).isFalse();

    // An argument type decides how its value is resolved
    Command otherType = currentFileDrop();
    otherType.getArguments().stream()
        .filter(argument -> IOC_VALIDATION_RUN_KEY.equals(argument.getKey()))
        .forEach(argument -> argument.setType(PrimitiveType.TargetedAsset));
    assertThat(PayloadService.isCurrentIocValidationFileDropTemplate(otherType)).isFalse();

    Command withoutRun = currentFileDrop();
    withoutRun.setArguments(
        new ArrayList<>(
            PayloadService.iocValidationArguments(IocValidationTestKind.FILE_DROP).stream()
                .filter(argument -> !IOC_VALIDATION_RUN_KEY.equals(argument.getKey()))
                .toList()));
    assertThat(PayloadService.isCurrentIocValidationFileDropTemplate(withoutRun)).isFalse();

    // A run default set through the payload update API would be shared by every inject without a
    // run
    Command editedDefault = currentFileDrop();
    editedDefault.getArguments().stream()
        .filter(argument -> IOC_VALIDATION_RUN_KEY.equals(argument.getKey()))
        .forEach(argument -> argument.setDefaultValue("0123456789abcdef0123456789abcdef"));
    assertThat(PayloadService.isCurrentIocValidationFileDropTemplate(editedDefault)).isFalse();
  }

  @Test
  @DisplayName(
      "an inject without a run keeps an empty run: it is refused before dispatch as a missing"
          + " mandatory input")
  void given_missingRun_should_stayEmpty() {
    Command payload = fileDropPayload(PayloadService.IOC_VALIDATION_POSIX_EXECUTOR, "tenant-a");
    ObjectNode content = JsonNodeFactory.instance.objectNode();
    content.put(IOC_VALIDATION_FILE_NAME_KEY, "invoice.pdf");
    ObjectNode emptyRun = content.deepCopy();
    emptyRun.put(IOC_VALIDATION_RUN_KEY, "");

    assertThat(
            PayloadService.iocValidationExecutionContent(content, payload, "inject-a")
                .get(IOC_VALIDATION_RUN_KEY)
                .asText())
        .isEmpty();
    assertThat(content.has(IOC_VALIDATION_RUN_KEY)).isFalse();
    assertThat(
            PayloadService.iocValidationExecutionContent(emptyRun, payload, "inject-a")
                .get(IOC_VALIDATION_RUN_KEY)
                .asText())
        .isEmpty();
    assertThat(
            PayloadService.iocValidationExecutionContent(null, payload, "inject-a")
                .get(IOC_VALIDATION_RUN_KEY)
                .asText())
        .isEmpty();
  }

  private static Command currentFileDrop() {
    Command current = fileDropPayload(PayloadService.IOC_VALIDATION_POSIX_EXECUTOR, "tenant-a");
    current.setCleanupExecutor(PayloadService.IOC_VALIDATION_POSIX_EXECUTOR);
    current.setContent(
        PayloadService.iocValidationCommandContent(IocValidationTestKind.FILE_DROP, false));
    current.setCleanupCommand(
        PayloadService.iocValidationCleanupCommand(IocValidationTestKind.FILE_DROP, false));
    return current;
  }

  @Test
  @DisplayName("a user payload with an argument named like the run keeps its value")
  void given_userPayloadWithRunArgument_should_keepTheContent() {
    Command payload = fileDropPayload(PayloadService.IOC_VALIDATION_POSIX_EXECUTOR, "tenant-a");
    payload.setId("4b3f8e52-2f1b-4c36-9a4e-1d2c3b4a5f60");
    ObjectNode content = JsonNodeFactory.instance.objectNode();
    content.put(IOC_VALIDATION_RUN_KEY, "0123456789abcdef0123456789abcdef");

    assertThat(PayloadService.iocValidationExecutionContent(content, payload, "inject-a"))
        .isSameAs(content);
    // The file drop payload of another tenant is not this tenant's singleton
    Command otherTenant = fileDropPayload(PayloadService.IOC_VALIDATION_POSIX_EXECUTOR, "tenant-b");
    otherTenant.setTenant(new Tenant("tenant-a"));
    assertThat(PayloadService.iocValidationExecutionContent(content, otherTenant, "inject-a"))
        .isSameAs(content);
    assertThat(PayloadService.isIocValidationFileDropPayload(null)).isFalse();
  }

  @Test
  @DisplayName("the other payloads are executed with their inject content unchanged")
  void given_payloadWithoutRun_should_keepTheContent() {
    Command payload = new Command();
    payload.setArguments(
        new ArrayList<>(PayloadService.iocValidationArguments(IocValidationTestKind.HTTP_HEAD)));
    ObjectNode content = JsonNodeFactory.instance.objectNode();
    content.put(IOC_VALIDATION_RUN_KEY, "../../escape");

    assertThat(PayloadService.iocValidationExecutionContent(content, payload, "inject-a"))
        .isSameAs(content);
  }

  @Test
  @DisplayName(
      "the Unix file drop checks the run and the file name, then writes in the run directory")
  void given_fileDropOnUnix_should_checkThenWriteInsideTheRunDirectory() {
    String content =
        PayloadService.iocValidationCommandContent(IocValidationTestKind.FILE_DROP, false);
    assertThat(content)
        .contains("OAEV_IOC_RUN=" + RUN)
        .contains("OAEV_IOC_FILE=" + FILE_NAME)
        .contains("*[!0123456789abcdef]*")
        .contains("\"${#OAEV_IOC_RUN}\" -ne 32")
        .contains("mkdir -p -m 700 \"$OAEV_IOC_DIR\"")
        .contains("> \"$OAEV_IOC_DIR/$OAEV_IOC_FILE\"")
        .doesNotContain("\"${TMPDIR:-/tmp}/\"" + FILE_NAME);
    assertThat(content.indexOf("exit 1")).isLessThan(content.indexOf("mkdir"));
  }

  @Test
  @DisplayName(
      "the Windows file drop checks the run and the file name, then writes in the run directory")
  void given_fileDropOnWindows_should_checkThenWriteInsideTheRunDirectory() {
    String content =
        PayloadService.iocValidationCommandContent(IocValidationTestKind.FILE_DROP, true);
    assertThat(content)
        .contains("$oaevIocRun = " + RUN)
        .contains("$oaevIocFile = " + FILE_NAME)
        .contains("$oaevIocRun -cnotmatch '^[0-9a-f]{32}$'")
        .contains("$oaevIocFile -notmatch '^[^\\\\/:*?\"<>|]+$'")
        .contains("('openaev-ioc-validation-' + $oaevIocRun)")
        .contains("Set-Content -LiteralPath (Join-Path $oaevIocDir $oaevIocFile)")
        .doesNotContain("GetTempPath()) " + FILE_NAME);
    assertThat(content.indexOf("throw")).isLessThan(content.indexOf("CreateDirectory"));
  }

  @Test
  @DisplayName("the Unix cleanup checks its arguments and removes only what the run created")
  void given_fileDropCleanupOnUnix_should_removeOnlyWhatTheRunCreated() {
    String cleanup =
        PayloadService.iocValidationCleanupCommand(IocValidationTestKind.FILE_DROP, false);
    assertThat(cleanup)
        .contains("rm -f \"$OAEV_IOC_DIR/$OAEV_IOC_FILE\"")
        .contains("rmdir \"$OAEV_IOC_DIR\"")
        .doesNotContain("rm -rf");
    assertThat(cleanup.indexOf("exit 1")).isLessThan(cleanup.indexOf("rm -f"));
  }

  @Test
  @DisplayName("the Windows cleanup checks its arguments and removes only what the run created")
  void given_fileDropCleanupOnWindows_should_removeOnlyWhatTheRunCreated() {
    String cleanup =
        PayloadService.iocValidationCleanupCommand(IocValidationTestKind.FILE_DROP, true);
    assertThat(cleanup)
        .contains("Remove-Item -LiteralPath (Join-Path $oaevIocDir $oaevIocFile)")
        .contains("[System.IO.Directory]::Delete($oaevIocDir)")
        .doesNotContain("-Recurse");
    assertThat(cleanup.indexOf("throw")).isLessThan(cleanup.indexOf("Remove-Item"));
  }

  @ParameterizedTest
  @EnumSource(
      value = IocValidationTestKind.class,
      names = {"NETWORK_TRAFFIC", "HTTP_HEAD", "LOG_INJECTION"})
  @DisplayName("only the file drop defines a cleanup")
  void given_testOtherThanFileDrop_should_defineNoCleanup(IocValidationTestKind kind) {
    assertThat(PayloadService.iocValidationCleanupCommand(kind, false)).isNull();
    assertThat(PayloadService.iocValidationCleanupCommand(kind, true)).isNull();
  }

  @Test
  @DisplayName("the bound Unix file drop keeps the run and the file name as values")
  void given_boundFileDropOnUnix_should_referenceTheBoundValues() {
    CommandArgumentBinder binder = CommandArgumentBinder.forExecutor("sh");
    binder.bind(IOC_VALIDATION_FILE_NAME_KEY, "invoice; rm -rf ~.pdf");
    binder.bind(IOC_VALIDATION_RUN_KEY, "0123abcd");

    String rendered =
        binder.render(
            PayloadService.iocValidationCommandContent(IocValidationTestKind.FILE_DROP, false));

    assertThat(rendered)
        .contains("='invoice; rm -rf ~.pdf'")
        .contains("='0123abcd'")
        .contains("OAEV_IOC_RUN=\"$OAEV_ARG_")
        .contains("OAEV_IOC_FILE=\"$OAEV_ARG_")
        .doesNotContain("#{");
  }

  /** Runs the rendered commands with {@code /bin/sh}, on the systems that have it (CI). */
  @Nested
  @DisplayName("executed by a POSIX shell")
  class PosixExecution {

    private static final String VALID_RUN = "0123456789abcdef0123456789abcdef";

    @TempDir Path tmp;

    private int execute(String template, String run, String fileName) throws Exception {
      assumeTrue(Files.isExecutable(Path.of("/bin/sh")), "requires /bin/sh");
      CommandArgumentBinder binder = CommandArgumentBinder.forExecutor("sh");
      binder.bind(IOC_VALIDATION_FILE_NAME_KEY, fileName);
      binder.bind(IOC_VALIDATION_RUN_KEY, run);
      ProcessBuilder builder = new ProcessBuilder("/bin/sh", "-c", binder.render(template));
      builder.environment().put("TMPDIR", tmp.toString());
      // The assertions read the file system, never the output: discarding it lets the timed wait
      // bound the test.
      builder.redirectErrorStream(true);
      builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
      Process process = builder.start();
      boolean finished = process.waitFor(30, TimeUnit.SECONDS);
      if (!finished) {
        process.destroyForcibly();
      }
      assertThat(finished).isTrue();
      return process.exitValue();
    }

    private static String drop() {
      return PayloadService.iocValidationCommandContent(IocValidationTestKind.FILE_DROP, false);
    }

    private static String cleanup() {
      return PayloadService.iocValidationCleanupCommand(IocValidationTestKind.FILE_DROP, false);
    }

    @Test
    @DisplayName("writes the surrogate in the run directory and cleans both up")
    void given_validArguments_should_writeThenCleanUp() throws Exception {
      Path runDirectory = tmp.resolve("openaev-ioc-validation-" + VALID_RUN);

      assertThat(execute(drop(), VALID_RUN, "invoice.pdf")).isZero();
      assertThat(runDirectory.resolve("invoice.pdf")).exists();

      assertThat(execute(cleanup(), VALID_RUN, "invoice.pdf")).isZero();
      assertThat(runDirectory).doesNotExist();
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
          "../../escape",
          "manual",
          "0123456789ABCDEF0123456789ABCDEF",
          "",
          PayloadService.IOC_VALIDATION_INVALID_RUN
        })
    @DisplayName("refuses a run that is not 32 lowercase hexadecimal characters and writes nothing")
    void given_invalidRun_should_failWithoutWriting(String invalidRun) throws Exception {
      assertThat(execute(drop(), invalidRun, "invoice.pdf")).isNotZero();
      try (Stream<Path> written = Files.list(tmp)) {
        assertThat(written).isEmpty();
      }
    }

    @ParameterizedTest
    @ValueSource(strings = {"../outside.txt", "..", ".", "nested/invoice.pdf", ""})
    @DisplayName("refuses a file name that is not a plain name and writes nothing")
    void given_invalidFileName_should_failWithoutWriting(String invalidFileName) throws Exception {
      assertThat(execute(drop(), VALID_RUN, invalidFileName)).isNotZero();
      try (Stream<Path> written = Files.list(tmp)) {
        assertThat(written).isEmpty();
      }
    }

    @Test
    @DisplayName("never removes a file outside the run directory at cleanup")
    void given_craftedCleanup_should_keepOtherFiles() throws Exception {
      Path other = Files.writeString(tmp.resolve("keep.txt"), "not ours");
      Path runDirectory =
          Files.createDirectories(tmp.resolve("openaev-ioc-validation-" + VALID_RUN));

      assertThat(execute(cleanup(), VALID_RUN, "../keep.txt")).isNotZero();
      assertThat(execute(cleanup(), "../" + VALID_RUN, "keep.txt")).isNotZero();
      assertThat(other).exists();
      assertThat(runDirectory).exists();
    }
  }
}
