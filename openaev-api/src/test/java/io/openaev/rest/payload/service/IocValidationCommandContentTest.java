package io.openaev.rest.payload.service;

import static io.openaev.database.model.BaseInjectExpectation.EXPECTATION_TYPE.DETECTION;
import static io.openaev.database.model.BaseInjectExpectation.EXPECTATION_TYPE.PREVENTION;
import static io.openaev.rest.payload.service.PayloadService.IOC_VALIDATION_FILE_NAME_KEY;
import static io.openaev.rest.payload.service.PayloadService.IOC_VALIDATION_HOST_KEY;
import static io.openaev.rest.payload.service.PayloadService.IOC_VALIDATION_PORT_KEY;
import static io.openaev.rest.payload.service.PayloadService.IOC_VALIDATION_PROXY_KEY;
import static io.openaev.rest.payload.service.PayloadService.IOC_VALIDATION_RUN_KEY;
import static io.openaev.rest.payload.service.PayloadService.IOC_VALIDATION_URL_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.BaseInjectExpectation.EXPECTATION_TYPE;
import io.openaev.database.model.Command;
import io.openaev.database.model.DnsResolution;
import io.openaev.database.model.Endpoint.PLATFORM_TYPE;
import io.openaev.database.model.IocValidationTestKind;
import io.openaev.database.model.Payload;
import io.openaev.database.model.PayloadArgument;
import io.openaev.database.model.PayloadPrerequisite;
import io.openaev.database.model.PrimitiveType;
import io.openaev.database.model.SecurityPlatform;
import io.openaev.database.model.Tenant;
import io.openaev.utils.command.CommandArgumentBinder;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
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
  @DisplayName(
      "the log injection test fails when the system log is unavailable, with no fallback file")
  void given_logInjection_should_failWithoutSystemLog() {
    String posix =
        PayloadService.iocValidationCommandContent(IocValidationTestKind.LOG_INJECTION, false);
    String windows =
        PayloadService.iocValidationCommandContent(IocValidationTestKind.LOG_INJECTION, true);
    assertThat(posix)
        .contains("logger -t openaev-ioc-validation -- \"$OAEV_IOC_MESSAGE\" || {")
        .contains(PayloadService.IOC_VALIDATION_NO_SYSTEM_LOG)
        .endsWith("exit 1; }")
        .doesNotContain("; true")
        .doesNotContain(">>")
        .doesNotContain("TMPDIR")
        .doesNotContain("/tmp");
    assertThat(windows)
        .endsWith("catch { throw '" + PayloadService.IOC_VALIDATION_NO_SYSTEM_LOG + "' }")
        .doesNotContain("Add-Content")
        .doesNotContain("GetTempPath");
  }

  @ParameterizedTest
  @EnumSource(
      value = IocValidationTestKind.class,
      names = {"DNS_RESOLUTION"},
      mode = EnumSource.Mode.EXCLUDE)
  @DisplayName("every IOC validation command singleton is executable only as its current template")
  void given_commandSingleton_should_beCurrentOnlyAsItsTemplate(IocValidationTestKind kind) {
    for (String executor :
        List.of(
            PayloadService.IOC_VALIDATION_POSIX_EXECUTOR,
            PayloadService.IOC_VALIDATION_WINDOWS_EXECUTOR)) {
      Command singleton = new Command();
      singleton.setTenant(new Tenant("tenant-a"));
      singleton.setId(PayloadService.iocValidationPayloadId(kind, executor, "tenant-a"));
      PayloadService.applyIocValidationCommandTemplate(singleton, kind, executor);
      assertThat(PayloadService.isIocValidationPayload(singleton)).isTrue();
      assertThat(PayloadService.isCurrentIocValidationTemplate(singleton)).isTrue();

      singleton.setContent(singleton.getContent() + "; curl https://example.org");
      assertThat(PayloadService.isCurrentIocValidationTemplate(singleton)).isFalse();

      // The template of another tenant's identity is not this payload's template
      singleton.setId(PayloadService.iocValidationPayloadId(kind, executor, "tenant-b"));
      assertThat(PayloadService.isIocValidationPayload(singleton)).isFalse();
    }
  }

  @Test
  @DisplayName("the IOC validation DNS singleton is executable only as its current template")
  void given_dnsSingleton_should_beCurrentOnlyAsItsTemplate() {
    DnsResolution singleton = new DnsResolution();
    singleton.setTenant(new Tenant("tenant-a"));
    singleton.setId(
        PayloadService.iocValidationPayloadId(
            IocValidationTestKind.DNS_RESOLUTION, "dns", "tenant-a"));
    PayloadService.applyIocValidationDnsTemplate(singleton);
    assertThat(PayloadService.isCurrentIocValidationTemplate(singleton)).isTrue();

    singleton.setHostname("example.org");
    assertThat(PayloadService.isCurrentIocValidationTemplate(singleton)).isFalse();
  }

  @Test
  @DisplayName("an edited IOC validation DNS payload is no longer the template, and is restored")
  void given_editedDnsPayload_should_beRestoredToTheTemplate() {
    DnsResolution payload = new DnsResolution();
    PayloadService.applyIocValidationDnsTemplate(payload);
    assertThat(PayloadService.isIocValidationDnsTemplate(payload)).isTrue();

    payload.setHostname("example.org");
    assertThat(PayloadService.isIocValidationDnsTemplate(payload)).isFalse();
    PayloadService.applyIocValidationDnsTemplate(payload);

    payload.setArguments(new ArrayList<>());
    assertThat(PayloadService.isIocValidationDnsTemplate(payload)).isFalse();
    PayloadService.applyIocValidationDnsTemplate(payload);

    payload.setExpectations(new EXPECTATION_TYPE[] {DETECTION});
    assertThat(PayloadService.isIocValidationDnsTemplate(payload)).isFalse();
    PayloadService.applyIocValidationDnsTemplate(payload);

    assertThat(PayloadService.isIocValidationDnsTemplate(payload)).isTrue();
    assertThat(payload.getHostname())
        .isEqualTo(PayloadService.DYNAMIC_DNS_RESOLUTION_HOSTNAME_VARIABLE);
  }

  @Test
  @DisplayName("the HTTP HEAD test always goes through the egress proxy, whatever NO_PROXY says")
  void given_httpHeadOnUnix_should_forceTheProxy() {
    String content =
        PayloadService.iocValidationCommandContent(IocValidationTestKind.HTTP_HEAD, false);
    assertThat(content).contains("--noproxy ''").contains("--proxy ");
    assertThat(content.indexOf("--noproxy ''")).isLessThan(content.indexOf("--proxy "));
  }

  @Test
  @DisplayName(
      "the Unix network tests accept a refused connection but fail without a tool to attempt it")
  void given_networkTestsOnUnix_should_failWithoutTool() {
    String tcp =
        PayloadService.iocValidationCommandContent(IocValidationTestKind.NETWORK_TRAFFIC, false);
    String http =
        PayloadService.iocValidationCommandContent(IocValidationTestKind.HTTP_HEAD, false);
    assertThat(tcp)
        .contains("elif command -v bash >/dev/null 2>&1; then")
        .contains(PayloadService.IOC_VALIDATION_NO_TCP_TOOL)
        .endsWith("exit 1; fi");
    assertThat(http)
        .startsWith("if command -v curl >/dev/null 2>&1; then")
        .contains(PayloadService.IOC_VALIDATION_NO_HTTP_TOOL)
        .endsWith("exit 1; fi");
    assertThat(tcp)
        .startsWith("if command -v nc >/dev/null 2>&1 && nc -h 2>&1 | grep -q -e ' -z'; then")
        .contains("elif command -v nc >/dev/null 2>&1; then nc -w 5 ")
        .contains(" </dev/null; true;");
    assertThat(tcp.split("; true;", -1)).hasSize(4);
    assertThat(http.split("; true;", -1)).hasSize(2);
  }

  @Test
  @DisplayName("the Windows HTTP HEAD test passes the egress proxy explicitly")
  void given_httpHeadOnWindows_should_passTheProxy() {
    String content =
        PayloadService.iocValidationCommandContent(IocValidationTestKind.HTTP_HEAD, true);
    assertThat(content).contains("-Proxy ");
  }

  @Test
  @DisplayName("the HTTP HEAD test never follows a redirect to a URL that was not checked")
  void given_httpHead_should_notFollowRedirects() {
    String windows =
        PayloadService.iocValidationCommandContent(IocValidationTestKind.HTTP_HEAD, true);
    String unix =
        PayloadService.iocValidationCommandContent(IocValidationTestKind.HTTP_HEAD, false);
    assertThat(windows).contains("Invoke-WebRequest ").contains(" -MaximumRedirection 0 ");
    assertThat(unix).contains(" curl -sS -I ").doesNotContain(" -L", "--location", "--max-redirs");
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

  @ParameterizedTest
  @ValueSource(
      strings = {"invo\nice.pdf", "invoice\r.pdf", "\u0000invoice.pdf", "invoice.pdf\u2028"})
  @DisplayName(
      "a file name the binder would strip a character from is replaced with one the endpoint refuses")
  void given_fileNameWithStrippedCharacter_should_stayInvalidOnceBound(String fileName) {
    Command payload = fileDropPayload(PayloadService.IOC_VALIDATION_WINDOWS_EXECUTOR, "tenant-a");
    ObjectNode content = JsonNodeFactory.instance.objectNode();
    content.put(IOC_VALIDATION_RUN_KEY, "0123456789abcdef0123456789abcdef");
    content.put(IOC_VALIDATION_FILE_NAME_KEY, fileName);

    ObjectNode bound = PayloadService.iocValidationExecutionContent(content, payload, "inject-a");

    assertThat(bound.get(IOC_VALIDATION_FILE_NAME_KEY).asText())
        .isEqualTo(PayloadService.IOC_VALIDATION_INVALID_FILE_NAME);
    assertThat(content.get(IOC_VALIDATION_FILE_NAME_KEY).asText()).isEqualTo(fileName);
    // A plain name and one holding a tab, which the binder keeps, are left for the endpoint check
    content.put(IOC_VALIDATION_FILE_NAME_KEY, "invoice.pdf");
    assertThat(
            PayloadService.iocValidationExecutionContent(content, payload, "inject-a")
                .get(IOC_VALIDATION_FILE_NAME_KEY)
                .asText())
        .isEqualTo("invoice.pdf");
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

    // The results are evaluated from the prevention and detection expectations
    Command withoutPrevention = currentFileDrop();
    withoutPrevention.setExpectations(new EXPECTATION_TYPE[] {DETECTION, DETECTION});
    assertThat(PayloadService.isCurrentIocValidationFileDropTemplate(withoutPrevention)).isFalse();
    Command withoutExpectations = currentFileDrop();
    withoutExpectations.setExpectations(null);
    assertThat(PayloadService.isCurrentIocValidationFileDropTemplate(withoutExpectations))
        .isFalse();
    // Expected security platforms restrict which collectors can satisfy the expectations
    Command restrictedPlatforms = currentFileDrop();
    restrictedPlatforms.setExpectedSecurityPlatforms(
        new HashMap<>(Map.of(DETECTION, List.of(SecurityPlatform.SECURITY_PLATFORM_TYPE.EDR))));
    assertThat(PayloadService.isCurrentIocValidationFileDropTemplate(restrictedPlatforms))
        .isFalse();
    // The endpoint platforms decide which agents run the command: sh never targets Windows
    Command otherPlatforms = currentFileDrop();
    otherPlatforms.setPlatforms(new PLATFORM_TYPE[] {PLATFORM_TYPE.Windows});
    assertThat(PayloadService.isCurrentIocValidationFileDropTemplate(otherPlatforms)).isFalse();
    Command withoutMacOs = currentFileDrop();
    withoutMacOs.setPlatforms(new PLATFORM_TYPE[] {PLATFORM_TYPE.Linux});
    assertThat(PayloadService.isCurrentIocValidationFileDropTemplate(withoutMacOs)).isFalse();
    // The execution architecture also decides which agents run the command
    Command arm64Only = currentFileDrop();
    arm64Only.setExecutionArch(Payload.PAYLOAD_EXECUTION_ARCH.arm64);
    assertThat(PayloadService.isCurrentIocValidationFileDropTemplate(arm64Only)).isFalse();
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
    current.setExpectations(new EXPECTATION_TYPE[] {DETECTION, PREVENTION});
    current.setPlatforms(new PLATFORM_TYPE[] {PLATFORM_TYPE.MacOS, PLATFORM_TYPE.Linux});
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
        .contains("mkdir -m 700 \"$OAEV_IOC_DIR\"")
        .contains("[ \"$(pwd -P)\" = \"$OAEV_IOC_DIR\" ]")
        .contains("[ -L \"./$OAEV_IOC_FILE\" ]")
        .contains("> \"./$OAEV_IOC_FILE\"")
        // noclobber: the surrogate is created, never written over an existing file
        .contains("set -C")
        // in a run directory only the runner can add entries to, and only when nothing is there
        .contains("[ ! -O . ] || ! chmod 700 .")
        .contains("if [ -e \"./$OAEV_IOC_FILE\" ] || ! ( set -C;")
        .contains(PayloadService.IOC_VALIDATION_FAILED_FILE_DROP)
        .doesNotContain("; true")
        .doesNotContain("mkdir -p")
        .doesNotContain("\"${TMPDIR:-/tmp}/\"" + FILE_NAME);
    assertThat(content.indexOf("exit 1")).isLessThan(content.indexOf("mkdir"));
    assertThat(content.indexOf("pwd -P")).isLessThan(content.indexOf("printf"));
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
        .contains("$oaevIocFile -notmatch '^[^\\\\/:*?\"<>|\\x00-\\x1f]+$'")
        .contains("$oaevIocFile -match '[. ]$'")
        .contains("$oaevIocFile -match '" + PayloadService.WINDOWS_RESERVED_FILE_NAME + "'")
        .contains("('openaev-ioc-validation-' + $oaevIocRun)")
        .contains("[System.IO.FileAttributes]::ReparsePoint")
        .contains(PayloadService.WINDOWS_OWNER_TEST)
        .contains(
            "if ((Test-OaevLink $oaevIocDir) -or (Test-OaevLink $oaevIocPath)"
                + " -or -not (Test-OaevOwned $oaevIocDir)) { throw")
        // Created as a new file: never an overwrite, never through a link at the surrogate path
        .contains("[System.IO.FileMode]::CreateNew")
        .contains(PayloadService.IOC_VALIDATION_FAILED_FILE_DROP)
        .doesNotContain("Set-Content")
        .doesNotContain("GetTempPath()) " + FILE_NAME);
    assertThat(content.indexOf("throw")).isLessThan(content.indexOf("CreateDirectory"));
    // A link, or a run directory another account owns, is refused right before the creation,
    // after the directory exists
    assertThat(content.indexOf("-or -not (Test-OaevOwned $oaevIocDir)) { throw"))
        .isGreaterThan(content.indexOf("CreateDirectory"))
        .isLessThan(content.indexOf("CreateNew"));
    // and again once the surrogate is open, before anything is written to it
    int recheck =
        content.indexOf(
            "$oaevIocMoved = (Test-OaevLink $oaevIocDir) -or (Test-OaevLink $oaevIocPath)"
                + " -or -not (Test-OaevOwned $oaevIocDir);");
    assertThat(recheck)
        .isGreaterThan(content.indexOf("CreateNew"))
        .isLessThan(content.indexOf("$oaevIocStream.Write("));
    assertThat(content)
        .endsWith(
            "if ($oaevIocMoved) { throw '"
                + PayloadService.IOC_VALIDATION_UNSAFE_FILE_DROP
                + "' }");
  }

  @Test
  @DisplayName("the Unix cleanup checks its arguments and removes only an empty run directory")
  void given_fileDropCleanupOnUnix_should_removeOnlyAnEmptyRunDirectory() {
    String cleanup =
        PayloadService.iocValidationCleanupCommand(IocValidationTestKind.FILE_DROP, false);
    assertThat(cleanup)
        .contains("*[!0123456789abcdef]*")
        .contains("\"${#OAEV_IOC_RUN}\" -ne 32")
        .contains("rmdir -- \"openaev-ioc-validation-$OAEV_IOC_RUN\"")
        // A shell removes a file by path only, after any check of its bytes: the surrogate stays
        .doesNotContain("rm -f")
        .doesNotContain("rm -rf")
        .doesNotContain("unlink");
    assertThat(cleanup.indexOf("exit 1")).isLessThan(cleanup.indexOf("rmdir"));
  }

  @Test
  @DisplayName("the Windows cleanup checks its arguments and removes only what the run created")
  void given_fileDropCleanupOnWindows_should_removeOnlyWhatTheRunCreated() {
    String cleanup =
        PayloadService.iocValidationCleanupCommand(IocValidationTestKind.FILE_DROP, true);
    // The surrogate is deleted through its handle, never by path; a run directory another account
    // owns, or a run directory or a surrogate path that is a link, is left alone, and only an
    // empty run directory is removed
    String deleteByHandle = "SetFileInformationByHandle($oaevIocHandle, 4";
    String ownRunDirectory =
        "-and -not (Test-OaevLink $oaevIocDir) -and (Test-OaevOwned $oaevIocDir)) {";
    String deleteDirectory = "[System.IO.Directory]::Delete($oaevIocDir)";
    assertThat(cleanup)
        .contains(PayloadService.WINDOWS_OWNER_TEST)
        .contains(ownRunDirectory)
        .contains("if (-not (Test-OaevLink $oaevIocPath))")
        .contains(deleteByHandle)
        .contains(deleteDirectory)
        .doesNotContain("[System.IO.File]::Delete")
        .doesNotContain("Remove-Item")
        .doesNotContain("-Recurse");
    assertThat(cleanup.indexOf("throw")).isLessThan(cleanup.indexOf(deleteByHandle));
    assertThat(cleanup.indexOf(ownRunDirectory))
        .isLessThan(cleanup.indexOf(deleteByHandle))
        .isLessThan(cleanup.indexOf(deleteDirectory));
  }

  @Test
  @DisplayName(
      "the Windows cleanup checks and deletes the surrogate through one handle no one can rename"
          + " it through")
  void given_fileDropCleanupOnWindows_should_checkAndDeleteTheOpenSurrogate() {
    String cleanup =
        PayloadService.iocValidationCleanupCommand(IocValidationTestKind.FILE_DROP, true);
    int open = cleanup.indexOf("[OaevIoc.Native]::CreateFileW($oaevIocPath");
    int recheck =
        cleanup.indexOf(
            "(Test-OaevLink $oaevIocDir) -or (Test-OaevLink $oaevIocPath)"
                + " -or -not (Test-OaevOwned $oaevIocDir))");
    int read = cleanup.indexOf("$oaevIocStream.Read($oaevIocRead");
    int delete = cleanup.indexOf("SetFileInformationByHandle($oaevIocHandle, 4");
    int close = cleanup.indexOf("$oaevIocStream.Dispose()");

    assertThat(open).isPositive();
    assertThat(recheck).isGreaterThan(open);
    assertThat(read).isGreaterThan(recheck);
    assertThat(delete).isGreaterThan(read);
    assertThat(close).isGreaterThan(delete);
    // GENERIC_READ | DELETE rights, FILE_SHARE_READ only (no rename, write nor delete by others),
    // OPEN_EXISTING, FILE_FLAG_OPEN_REPARSE_POINT
    assertThat(cleanup)
        .contains(
            "CreateFileW($oaevIocPath, [uint32]2147549184, [uint32]1, [IntPtr]::Zero, [uint32]3,"
                + " [uint32]2097152,")
        .contains("$oaevIocStream.Length -eq $oaevIocBytes.Length")
        .doesNotContain("FileShare]::Delete")
        .doesNotContain("ReadAllText")
        .doesNotContain("StreamReader");
  }

  // PowerShell -match is case-insensitive
  private static boolean isWindowsReservedFileName(String fileName) {
    return Pattern.compile(
            PayloadService.WINDOWS_RESERVED_FILE_NAME,
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
        .matcher(fileName)
        .find();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "CON",
        "con.txt",
        "NUL .log",
        "aux",
        "PRN.tar.gz",
        "COM1",
        "lpt9.dll",
        "COM\u00b9.txt",
        "CONIN$",
        "CONOUT$.txt"
      })
  @DisplayName("a reserved Windows device name is refused, with or without an extension")
  void given_reservedWindowsDeviceName_should_beRefused(String fileName) {
    assertThat(isWindowsReservedFileName(fileName)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"invoice.pdf", "CONSOLE.txt", "COM10", "LPT", "my-con.txt", "NULL.dll"})
  @DisplayName("a file name that only starts like a reserved Windows device name is accepted")
  void given_fileNameLikeAReservedWindowsDeviceName_should_beAccepted(String fileName) {
    assertThat(isWindowsReservedFileName(fileName)).isFalse();
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

    // Before any setup: the link fixtures need a POSIX file system as well
    @BeforeEach
    void requirePosixShell() {
      assumeTrue(Files.isExecutable(Path.of("/bin/sh")), "requires /bin/sh");
    }

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

    @Test
    @DisplayName("fails the network tests on an endpoint without nc, bash nor curl")
    void given_noNetworkTool_should_fail() throws Exception {
      for (IocValidationTestKind kind :
          List.of(IocValidationTestKind.NETWORK_TRAFFIC, IocValidationTestKind.HTTP_HEAD)) {
        CommandArgumentBinder binder = CommandArgumentBinder.forExecutor("sh");
        binder.bind(IOC_VALIDATION_HOST_KEY, "127.0.0.1");
        binder.bind(IOC_VALIDATION_PORT_KEY, "9");
        binder.bind(IOC_VALIDATION_URL_KEY, "http://127.0.0.1:9/");
        binder.bind(IOC_VALIDATION_PROXY_KEY, "http://127.0.0.1:9");
        ProcessBuilder builder =
            new ProcessBuilder(
                "/bin/sh",
                "-c",
                binder.render(PayloadService.iocValidationCommandContent(kind, false)));
        // An empty PATH: command -v finds none of the tools
        builder.environment().put("PATH", Files.createDirectories(tmp.resolve("empty")).toString());
        builder.redirectErrorStream(true);
        builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        Process process = builder.start();
        assertThat(process.waitFor(30, TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).as(kind.name()).isNotZero();
      }
    }

    private static String cleanup() {
      return PayloadService.iocValidationCleanupCommand(IocValidationTestKind.FILE_DROP, false);
    }

    @Test
    @DisplayName("writes the surrogate in the run directory and leaves it in place at cleanup")
    void given_validArguments_should_writeThenKeepTheSurrogate() throws Exception {
      Path surrogate = tmp.resolve("openaev-ioc-validation-" + VALID_RUN).resolve("invoice.pdf");

      assertThat(execute(drop(), VALID_RUN, "invoice.pdf")).isZero();
      String written = Files.readString(surrogate);

      // A shell can only remove a file by path once it checked it: the surrogate stays
      assertThat(execute(cleanup(), VALID_RUN, "invoice.pdf")).isZero();
      assertThat(surrogate).hasContent(written);
    }

    @Test
    @DisplayName("removes the run directory at cleanup once the surrogate is gone")
    void given_quarantinedSurrogate_should_removeTheEmptyRunDirectory() throws Exception {
      Path runDirectory = tmp.resolve("openaev-ioc-validation-" + VALID_RUN);

      assertThat(execute(drop(), VALID_RUN, "invoice.pdf")).isZero();
      // as a security platform quarantining the file does
      Files.delete(runDirectory.resolve("invoice.pdf"));

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
    @ValueSource(
        strings = {
          "../outside.txt",
          "..",
          ".",
          "nested/invoice.pdf",
          "",
          PayloadService.IOC_VALIDATION_INVALID_FILE_NAME
        })
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

    @Test
    @DisplayName("never follows a run directory replaced by a symbolic link, at drop or at cleanup")
    void given_runDirectoryLink_should_neverFollowIt() throws Exception {
      Path outside = Files.createDirectories(tmp.resolve("outside"));
      Path victim = Files.writeString(outside.resolve("invoice.pdf"), "not ours");
      Path runDirectory =
          Files.createSymbolicLink(tmp.resolve("openaev-ioc-validation-" + VALID_RUN), outside);

      assertThat(execute(drop(), VALID_RUN, "invoice.pdf")).isNotZero();
      assertThat(execute(cleanup(), VALID_RUN, "invoice.pdf")).isZero();

      assertThat(victim).hasContent("not ours");
      assertThat(Files.isSymbolicLink(runDirectory)).isTrue();
    }

    @Test
    @DisplayName(
        "never follows a surrogate path replaced by a symbolic link, at drop or at cleanup")
    void given_surrogateLink_should_neverFollowIt() throws Exception {
      Path victim = Files.writeString(tmp.resolve("keep.txt"), "not ours");
      Path runDirectory =
          Files.createDirectories(tmp.resolve("openaev-ioc-validation-" + VALID_RUN));
      Path surrogate = Files.createSymbolicLink(runDirectory.resolve("invoice.pdf"), victim);

      assertThat(execute(drop(), VALID_RUN, "invoice.pdf")).isNotZero();
      assertThat(execute(cleanup(), VALID_RUN, "invoice.pdf")).isZero();

      assertThat(victim).hasContent("not ours");
      assertThat(Files.isSymbolicLink(surrogate)).isTrue();
    }

    @Test
    @DisplayName(
        "never overwrites a file already at the surrogate path, fails, and keeps it at cleanup")
    void given_existingSurrogate_should_failWithoutOverwritingNorRemovingIt() throws Exception {
      Path runDirectory =
          Files.createDirectories(tmp.resolve("openaev-ioc-validation-" + VALID_RUN));
      Path existing = Files.writeString(runDirectory.resolve("invoice.pdf"), "not ours");

      assertThat(execute(drop(), VALID_RUN, "invoice.pdf")).isNotZero();
      assertThat(execute(cleanup(), VALID_RUN, "invoice.pdf")).isZero();

      assertThat(existing).hasContent("not ours");
    }

    @Test
    @DisplayName("never opens a FIFO already at the surrogate path: fails at once, writing nothing")
    void given_fifoAtSurrogatePath_should_failWithoutOpeningIt() throws Exception {
      Path runDirectory =
          Files.createDirectories(tmp.resolve("openaev-ioc-validation-" + VALID_RUN));
      Path fifo = runDirectory.resolve("invoice.pdf");
      boolean created;
      try {
        Process mkfifo =
            new ProcessBuilder("mkfifo", fifo.toString())
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start();
        created = mkfifo.waitFor(30, TimeUnit.SECONDS) && mkfifo.exitValue() == 0;
      } catch (IOException e) {
        created = false;
      }
      assumeTrue(created, "requires mkfifo");

      // A drop opening the FIFO would block without a reader: execute bounds it and fails then
      assertThat(execute(drop(), VALID_RUN, "invoice.pdf")).isNotZero();

      assertThat(Files.exists(fifo, LinkOption.NOFOLLOW_LINKS)).isTrue();
      assertThat(Files.isRegularFile(fifo, LinkOption.NOFOLLOW_LINKS)).isFalse();
    }

    @Test
    @DisplayName("keeps at cleanup a file put in the place of the surrogate")
    void given_replacedSurrogate_should_keepItAtCleanup() throws Exception {
      Path surrogate = tmp.resolve("openaev-ioc-validation-" + VALID_RUN).resolve("invoice.pdf");

      assertThat(execute(drop(), VALID_RUN, "invoice.pdf")).isZero();
      Files.writeString(surrogate, "not ours");
      assertThat(execute(cleanup(), VALID_RUN, "invoice.pdf")).isZero();

      assertThat(surrogate).hasContent("not ours");
    }

    @Test
    @DisplayName("keeps at cleanup a file that only adds trailing newlines to the surrogate")
    void given_surrogateWithTrailingNewlines_should_keepItAtCleanup() throws Exception {
      Path surrogate = tmp.resolve("openaev-ioc-validation-" + VALID_RUN).resolve("invoice.pdf");

      assertThat(execute(drop(), VALID_RUN, "invoice.pdf")).isZero();
      String replacement = Files.readString(surrogate) + "\n\n";
      Files.writeString(surrogate, replacement);
      assertThat(execute(cleanup(), VALID_RUN, "invoice.pdf")).isZero();

      assertThat(Files.readString(surrogate)).isEqualTo(replacement);
    }
  }

  /**
   * Runs the rendered Windows commands with PowerShell, on the systems that have it ({@code pwsh}
   * or Windows PowerShell) and allow creating a symbolic link.
   */
  @Nested
  @DisplayName("executed by PowerShell")
  class PowerShellExecution {

    private static final String VALID_RUN = "fedcba9876543210fedcba9876543210";

    @TempDir Path tmp;

    private String shell;

    // Put first on the PATH of the executed commands when set
    private Path standInDirectory;

    @BeforeEach
    void requirePowerShell() {
      shell =
          Stream.of("pwsh", "powershell")
              .filter(PowerShellExecution::starts)
              .findFirst()
              .orElse(null);
      assumeTrue(shell != null, "requires PowerShell");
    }

    private static boolean starts(String candidate) {
      try {
        Process process =
            new ProcessBuilder(candidate, "-NoProfile", "-NonInteractive", "-Command", "exit 0")
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start();
        return process.waitFor(60, TimeUnit.SECONDS) && process.exitValue() == 0;
      } catch (IOException e) {
        return false;
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return false;
      }
    }

    private Path danglingLink(Path link) throws Exception {
      return directoryLink(link, tmp.resolve("missing-target"));
    }

    private Path directoryLink(Path link, Path target) throws Exception {
      try {
        return Files.createSymbolicLink(link, target);
      } catch (IOException | UnsupportedOperationException e) {
        // Without the symbolic link right, Windows still lets any account create a junction
        assumeTrue(
            System.getProperty("os.name", "").startsWith("Windows"),
            "requires the right to create a symbolic link");
        Process process =
            new ProcessBuilder("cmd", "/c", "mklink", "/J", link.toString(), target.toString())
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start();
        assumeTrue(
            process.waitFor(30, TimeUnit.SECONDS) && process.exitValue() == 0,
            "requires a junction");
        return link;
      }
    }

    private static boolean isDanglingLink(Path path) {
      return Files.exists(path, LinkOption.NOFOLLOW_LINKS)
          && !Files.isRegularFile(path)
          && !Files.isDirectory(path);
    }

    private int execute(String template, String run, String fileName) throws Exception {
      CommandArgumentBinder binder = CommandArgumentBinder.forExecutor("psh");
      binder.bind(IOC_VALIDATION_FILE_NAME_KEY, fileName);
      binder.bind(IOC_VALIDATION_RUN_KEY, run);
      // A script file keeps the command intact (no command-line quoting on Windows)
      Path script =
          Files.writeString(
              Files.createTempFile(tmp, "ioc-validation-", ".ps1"), binder.render(template));
      ProcessBuilder builder =
          new ProcessBuilder(
              shell,
              "-NoProfile",
              "-NonInteractive",
              "-ExecutionPolicy",
              "Bypass",
              "-File",
              script.toString());
      // GetTempPath reads TMPDIR on POSIX systems, TMP then TEMP on Windows
      builder.environment().put("TMPDIR", tmp.toString());
      builder.environment().put("TMP", tmp.toString());
      builder.environment().put("TEMP", tmp.toString());
      if (standInDirectory != null) {
        builder
            .environment()
            .merge(
                "PATH",
                standInDirectory.toString(),
                (path, standIn) -> standIn + File.pathSeparator + path);
      }
      builder.redirectErrorStream(true);
      builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
      Process process = builder.start();
      boolean finished = process.waitFor(60, TimeUnit.SECONDS);
      if (!finished) {
        process.destroyForcibly();
      }
      assertThat(finished).isTrue();
      return process.exitValue();
    }

    private static String drop() {
      return PayloadService.iocValidationCommandContent(IocValidationTestKind.FILE_DROP, true);
    }

    private static String cleanup() {
      return PayloadService.iocValidationCleanupCommand(IocValidationTestKind.FILE_DROP, true);
    }

    @Test
    @DisplayName(
        "keeps at cleanup the surrogate copy behind a run directory swapped for a link after the"
            + " drop")
    void given_runDirectorySwappedForLink_should_keepWhatTheLinkLeadsTo() throws Exception {
      Path runDirectory = tmp.resolve("openaev-ioc-validation-" + VALID_RUN);
      assertThat(execute(drop(), VALID_RUN, "invoice.pdf")).isZero();
      Path outside = Files.createDirectories(tmp.resolve("outside"));
      Path copy = Files.copy(runDirectory.resolve("invoice.pdf"), outside.resolve("invoice.pdf"));
      Files.delete(runDirectory.resolve("invoice.pdf"));
      Files.delete(runDirectory);
      directoryLink(runDirectory, outside);

      assertThat(execute(cleanup(), VALID_RUN, "invoice.pdf")).isZero();

      assertThat(copy).exists();
    }

    @Test
    @DisplayName("writes the surrogate in the run directory and cleans both up")
    void given_validArguments_should_writeThenCleanUp() throws Exception {
      Path runDirectory = tmp.resolve("openaev-ioc-validation-" + VALID_RUN);

      assertThat(execute(drop(), VALID_RUN, "invoice.pdf")).isZero();
      assertThat(runDirectory.resolve("invoice.pdf")).exists();

      requireWindows();
      assertThat(execute(cleanup(), VALID_RUN, "invoice.pdf")).isZero();
      assertThat(runDirectory).doesNotExist();
    }

    @Test
    @DisplayName(
        "keeps at cleanup a surrogate another process holds open, so it can neither change nor"
            + " rename it once checked")
    void given_surrogateHeldOpenForWriting_should_keepItAtCleanup() throws Exception {
      requireWindows();
      Path surrogate = tmp.resolve("openaev-ioc-validation-" + VALID_RUN).resolve("invoice.pdf");
      assertThat(execute(drop(), VALID_RUN, "invoice.pdf")).isZero();
      byte[] dropped = Files.readAllBytes(surrogate);

      try (var writer = Files.newOutputStream(surrogate, StandardOpenOption.APPEND)) {
        assertThat(execute(cleanup(), VALID_RUN, "invoice.pdf")).isZero();
        assertThat(surrogate).hasBinaryContent(dropped);
      }
      assertThat(execute(cleanup(), VALID_RUN, "invoice.pdf")).isZero();
      assertThat(surrogate).doesNotExist();
    }

    /**
     * A run directory another account owns. On Windows it is given to the local service account,
     * which takes the restore privilege; off Windows the owner is compared with {@code id -u}, so a
     * stand-in printing another user id makes the directory foreign.
     */
    private Path foreignRunDirectory() throws Exception {
      Path runDirectory =
          Files.createDirectories(tmp.resolve("openaev-ioc-validation-" + VALID_RUN));
      if (System.getProperty("os.name", "").startsWith("Windows")) {
        Process process =
            new ProcessBuilder("icacls", runDirectory.toString(), "/setowner", "*S-1-5-19")
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start();
        assumeTrue(
            process.waitFor(30, TimeUnit.SECONDS) && process.exitValue() == 0,
            "requires the right to give a directory another owner");
      } else {
        int uid = (int) Files.getAttribute(runDirectory, "unix:uid");
        standInDirectory = Files.createDirectories(tmp.resolve("stand-in"));
        Path id =
            Files.writeString(
                standInDirectory.resolve("id"), "#!/bin/sh\necho " + (uid + 1) + "\n");
        assumeTrue(id.toFile().setExecutable(true), "requires an executable stand-in for id");
      }
      return runDirectory;
    }

    @Test
    @DisplayName(
        "refuses a run directory another account owns, writing nothing in it, and leaves it and"
            + " its content at cleanup")
    void given_foreignRunDirectory_should_failWithoutWritingNorRemovingIt() throws Exception {
      Path runDirectory = foreignRunDirectory();
      Path surrogate = runDirectory.resolve("invoice.pdf");

      assertThat(execute(drop(), VALID_RUN, "invoice.pdf")).isNotZero();
      assertThat(surrogate).doesNotExist();

      String surrogateText =
          PayloadService.IOC_VALIDATION_SURROGATE_TEXT + " " + VALID_RUN + System.lineSeparator();
      Files.writeString(surrogate, surrogateText);
      assertThat(execute(cleanup(), VALID_RUN, "invoice.pdf")).isZero();

      assertThat(surrogate).hasContent(surrogateText);
    }

    // The cleanup deletes the surrogate through a Windows file handle: elsewhere it removes nothing
    private static void requireWindows() {
      assumeTrue(
          System.getProperty("os.name", "").startsWith("Windows"),
          "deletes through a Windows file handle");
    }

    @Test
    @DisplayName(
        "refuses a file name holding a line break, as the server binds it, writing nothing")
    void given_fileNameWithLineBreak_should_failWithoutWriting() throws Exception {
      ObjectNode content = JsonNodeFactory.instance.objectNode();
      content.put(IOC_VALIDATION_RUN_KEY, VALID_RUN);
      content.put(IOC_VALIDATION_FILE_NAME_KEY, "invo\nice.pdf");
      Command payload = fileDropPayload(PayloadService.IOC_VALIDATION_WINDOWS_EXECUTOR, "tenant-a");
      String fileName =
          PayloadService.iocValidationExecutionContent(content, payload, "inject-a")
              .get(IOC_VALIDATION_FILE_NAME_KEY)
              .asText();

      assertThat(execute(drop(), VALID_RUN, fileName)).isNotZero();

      assertThat(tmp.resolve("openaev-ioc-validation-" + VALID_RUN)).doesNotExist();
    }

    @Test
    @DisplayName("refuses a dangling link as run directory and leaves it in place at cleanup")
    void given_danglingRunDirectoryLink_should_neitherFollowNorDeleteIt() throws Exception {
      Path runDirectory = danglingLink(tmp.resolve("openaev-ioc-validation-" + VALID_RUN));

      assertThat(execute(drop(), VALID_RUN, "invoice.pdf")).isNotZero();
      assertThat(execute(cleanup(), VALID_RUN, "invoice.pdf")).isZero();

      assertThat(tmp.resolve("missing-target")).doesNotExist();
      assertThat(isDanglingLink(runDirectory)).isTrue();
    }

    @Test
    @DisplayName("refuses a dangling link as surrogate path and leaves it in place at cleanup")
    void given_danglingSurrogateLink_should_neitherFollowNorDeleteIt() throws Exception {
      Path runDirectory =
          Files.createDirectories(tmp.resolve("openaev-ioc-validation-" + VALID_RUN));
      Path surrogate = danglingLink(runDirectory.resolve("invoice.pdf"));

      assertThat(execute(drop(), VALID_RUN, "invoice.pdf")).isNotZero();
      assertThat(execute(cleanup(), VALID_RUN, "invoice.pdf")).isZero();

      assertThat(tmp.resolve("missing-target")).doesNotExist();
      assertThat(isDanglingLink(surrogate)).isTrue();
    }

    @Test
    @DisplayName(
        "never overwrites a file already at the surrogate path, fails, and keeps it at cleanup")
    void given_existingSurrogate_should_failWithoutOverwritingNorRemovingIt() throws Exception {
      Path runDirectory =
          Files.createDirectories(tmp.resolve("openaev-ioc-validation-" + VALID_RUN));
      Path existing = Files.writeString(runDirectory.resolve("invoice.pdf"), "not ours");

      assertThat(execute(drop(), VALID_RUN, "invoice.pdf")).isNotZero();
      assertThat(execute(cleanup(), VALID_RUN, "invoice.pdf")).isZero();

      assertThat(existing).hasContent("not ours");
    }

    @Test
    @DisplayName("removes at cleanup only the surrogate of its run, not a file put in its place")
    void given_replacedSurrogate_should_keepItAtCleanup() throws Exception {
      Path surrogate = tmp.resolve("openaev-ioc-validation-" + VALID_RUN).resolve("invoice.pdf");

      assertThat(execute(drop(), VALID_RUN, "invoice.pdf")).isZero();
      Files.writeString(surrogate, "not ours");
      assertThat(execute(cleanup(), VALID_RUN, "invoice.pdf")).isZero();

      assertThat(surrogate).hasContent("not ours");
    }

    @Test
    @DisplayName("keeps at cleanup a file that only adds trailing whitespace to the surrogate")
    void given_surrogateWithTrailingWhitespace_should_keepItAtCleanup() throws Exception {
      Path surrogate = tmp.resolve("openaev-ioc-validation-" + VALID_RUN).resolve("invoice.pdf");

      assertThat(execute(drop(), VALID_RUN, "invoice.pdf")).isZero();
      String replacement = Files.readString(surrogate).stripTrailing() + " \t\r\n";
      Files.writeString(surrogate, replacement);
      assertThat(execute(cleanup(), VALID_RUN, "invoice.pdf")).isZero();

      assertThat(Files.readString(surrogate)).isEqualTo(replacement);
    }

    @Test
    @DisplayName("keeps at cleanup a file that adds a byte-order mark before the surrogate")
    void given_surrogateWithByteOrderMark_should_keepItAtCleanup() throws Exception {
      Path surrogate = tmp.resolve("openaev-ioc-validation-" + VALID_RUN).resolve("invoice.pdf");

      assertThat(execute(drop(), VALID_RUN, "invoice.pdf")).isZero();
      byte[] dropped = Files.readAllBytes(surrogate);
      byte[] replacement = new byte[dropped.length + 3];
      replacement[0] = (byte) 0xEF;
      replacement[1] = (byte) 0xBB;
      replacement[2] = (byte) 0xBF;
      System.arraycopy(dropped, 0, replacement, 3, dropped.length);
      Files.write(surrogate, replacement);
      assertThat(execute(cleanup(), VALID_RUN, "invoice.pdf")).isZero();

      assertThat(surrogate).hasBinaryContent(replacement);
    }
  }
}
