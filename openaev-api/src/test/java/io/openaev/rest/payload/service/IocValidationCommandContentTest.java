package io.openaev.rest.payload.service;

import static io.openaev.rest.payload.service.PayloadService.IOC_VALIDATION_FILE_NAME_KEY;
import static io.openaev.rest.payload.service.PayloadService.IOC_VALIDATION_RUN_KEY;
import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.database.model.IocValidationTestKind;
import io.openaev.utils.command.CommandArgumentBinder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@DisplayName("IOC validation benign command content")
class IocValidationCommandContentTest {

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
  @DisplayName("the Unix file drop writes the surrogate in a directory owned by the run")
  void given_fileDropOnUnix_should_writeInsideTheRunDirectory() {
    String content =
        PayloadService.iocValidationCommandContent(IocValidationTestKind.FILE_DROP, false);
    assertThat(content)
        .contains("openaev-ioc-validation-\"#{" + IOC_VALIDATION_RUN_KEY + "}")
        .contains("mkdir -p -m 700 \"$OAEV_IOC_DIR\"")
        .contains("> \"$OAEV_IOC_DIR/\"" + FILE_NAME)
        .doesNotContain("\"${TMPDIR:-/tmp}/\"" + FILE_NAME);
  }

  @Test
  @DisplayName("the Windows file drop writes the surrogate in a directory owned by the run")
  void given_fileDropOnWindows_should_writeInsideTheRunDirectory() {
    String content =
        PayloadService.iocValidationCommandContent(IocValidationTestKind.FILE_DROP, true);
    assertThat(content)
        .contains("('openaev-ioc-validation-' + #{" + IOC_VALIDATION_RUN_KEY + "})")
        .contains("[System.IO.Directory]::CreateDirectory($oaevIocDir)")
        .contains("Set-Content -LiteralPath (Join-Path $oaevIocDir " + FILE_NAME + ")")
        .doesNotContain("GetTempPath()) " + FILE_NAME);
  }

  @Test
  @DisplayName("the Unix cleanup removes the surrogate and only an empty run directory")
  void given_fileDropCleanupOnUnix_should_removeOnlyWhatTheRunCreated() {
    String cleanup =
        PayloadService.iocValidationCleanupCommand(IocValidationTestKind.FILE_DROP, false);
    assertThat(cleanup)
        .contains("rm -f \"$OAEV_IOC_DIR/\"" + FILE_NAME)
        .contains("rmdir \"$OAEV_IOC_DIR\"")
        .doesNotContain("rm -rf")
        .doesNotContain("\"${TMPDIR:-/tmp}/\"" + FILE_NAME);
  }

  @Test
  @DisplayName("the Windows cleanup removes the surrogate and only an empty run directory")
  void given_fileDropCleanupOnWindows_should_removeOnlyWhatTheRunCreated() {
    String cleanup =
        PayloadService.iocValidationCleanupCommand(IocValidationTestKind.FILE_DROP, true);
    assertThat(cleanup)
        .contains("Remove-Item -LiteralPath (Join-Path $oaevIocDir " + FILE_NAME + ")")
        .contains("[System.IO.Directory]::Delete($oaevIocDir)")
        .doesNotContain("-Recurse")
        .doesNotContain("GetTempPath()) " + FILE_NAME);
  }

  @ParameterizedTest
  @EnumSource(
      value = IocValidationTestKind.class,
      names = {"NETWORK_TRAFFIC", "HTTP_HEAD", "LOG_INJECTION"})
  @DisplayName("the tests that leave nothing behind have no cleanup")
  void given_testWithoutArtifact_should_haveNoCleanup(IocValidationTestKind kind) {
    assertThat(PayloadService.iocValidationCleanupCommand(kind, false)).isNull();
    assertThat(PayloadService.iocValidationCleanupCommand(kind, true)).isNull();
  }

  @Test
  @DisplayName("the bound Unix file drop keeps the run directory and the file name as values")
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
        .contains("OAEV_IOC_DIR=\"${TMPDIR:-/tmp}/openaev-ioc-validation-\"\"$OAEV_ARG_")
        .doesNotContain("#{");
  }
}
