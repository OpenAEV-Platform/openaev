package io.openaev.rest.inject.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.Command;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.IocValidationTestKind;
import io.openaev.database.model.PayloadArgument;
import io.openaev.database.model.PrimitiveType;
import io.openaev.database.model.Tenant;
import io.openaev.rest.payload.service.PayloadService;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ExecutableInjectService tests")
class ExecutableInjectServiceTest {

  private final ExecutableInjectService service =
      new ExecutableInjectService(null, null, null, null, null, null, null);

  @Test
  @DisplayName("Should preserve reserved implant placeholders when replacing payload arguments")
  void given_reservedPlaceholders_should_preserveThemWhenReplacingPayloadArguments() {
    // Arrange
    ObjectNode injectContent = JsonNodeFactory.instance.objectNode();
    injectContent.put("message", "hello");
    String command =
        "cat #{payload_location}/linpeas.sh && cd #{location} && echo #{message} #{missing}";

    // Act
    String result =
        service.resolveArgumentsForDisplay(
            command, List.of(payloadArgument("message", "fallback")), List.of(), injectContent);

    // Assert
    assertThat(result)
        .isEqualTo("cat #{payload_location}/linpeas.sh && cd #{location} && echo hello ");
  }

  @Test
  @DisplayName("Should keep command structure when optional value is missing")
  void given_optionalMissingArgument_should_keepFlagWithEmptyValue() {
    // Arrange
    ObjectNode injectContent = JsonNodeFactory.instance.objectNode();
    injectContent.put("IP", "10.10.10.10");
    String command = "nxc smb #{IP} -u #{username} --shares";

    ObjectNode usernameField = JsonNodeFactory.instance.objectNode();
    usernameField.put(InjectorContract.CONTRACT_ELEMENT_CONTENT_KEY, "username");
    usernameField.put(InjectorContract.CONTRACT_ELEMENT_CONTENT_MANDATORY, false);
    usernameField.put(InjectorContract.DEFAULT_VALUE_FIELD, "");

    // Act
    String result =
        service.resolveArgumentsForDisplay(
            command,
            List.of(payloadArgument("IP", ""), payloadArgument("username", "")),
            List.of(usernameField),
            injectContent);

    // Assert
    assertThat(result).isEqualTo("nxc smb 10.10.10.10 -u  --shares");
  }

  @Test
  @DisplayName("Should refuse an IOC validation file drop payload of an earlier version")
  void given_outdatedIocValidationFileDrop_should_refuseExecution() {
    // Arrange: the singleton as an earlier version created it, writing directly in the temp dir
    Command legacy = new Command();
    legacy.setTenant(new Tenant("tenant-a"));
    legacy.setExecutor(PayloadService.IOC_VALIDATION_POSIX_EXECUTOR);
    legacy.setId(
        PayloadService.iocValidationPayloadId(
            IocValidationTestKind.FILE_DROP,
            PayloadService.IOC_VALIDATION_POSIX_EXECUTOR,
            "tenant-a"));
    legacy.setContent(
        "printf 'OpenAEV IOC validation benign surrogate\\n' > \"${TMPDIR:-/tmp}/\"#{"
            + PayloadService.IOC_VALIDATION_FILE_NAME_KEY
            + "}; true");
    legacy.setArguments(List.of(payloadArgument(PayloadService.IOC_VALIDATION_FILE_NAME_KEY, "")));
    // A user payload is never concerned, whatever its content
    Command userPayload = new Command();
    userPayload.setTenant(new Tenant("tenant-a"));
    userPayload.setId("4b3f8e52-2f1b-4c36-9a4e-1d2c3b4a5f60");
    userPayload.setContent(legacy.getContent());

    // Act / Assert
    assertThatThrownBy(() -> ExecutableInjectService.refuseOutdatedIocValidationFileDrop(legacy))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(PayloadService.IOC_VALIDATION_OUTDATED_FILE_DROP);
    assertThatCode(() -> ExecutableInjectService.refuseOutdatedIocValidationFileDrop(userPayload))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName(
      "Should refuse an IOC validation file drop without its own run, whatever the defaults")
  void given_iocValidationFileDropWithoutRun_should_refuseExecution() {
    // Arrange
    Command fileDrop = new Command();
    fileDrop.setTenant(new Tenant("tenant-a"));
    fileDrop.setId(
        PayloadService.iocValidationPayloadId(
            IocValidationTestKind.FILE_DROP,
            PayloadService.IOC_VALIDATION_POSIX_EXECUTOR,
            "tenant-a"));
    ObjectNode withoutRun = JsonNodeFactory.instance.objectNode();
    withoutRun.put(PayloadService.IOC_VALIDATION_RUN_KEY, "");
    ObjectNode withRun = JsonNodeFactory.instance.objectNode();
    withRun.put(PayloadService.IOC_VALIDATION_RUN_KEY, "0123456789abcdef0123456789abcdef");
    ObjectNode withoutFileName = withRun.deepCopy();
    withRun.put(PayloadService.IOC_VALIDATION_FILE_NAME_KEY, "invoice.pdf");
    Command userPayload = new Command();
    userPayload.setTenant(new Tenant("tenant-a"));
    userPayload.setId("4b3f8e52-2f1b-4c36-9a4e-1d2c3b4a5f60");

    // Act / Assert
    assertThatThrownBy(
            () ->
                ExecutableInjectService.refuseIocValidationFileDropWithoutRun(fileDrop, withoutRun))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Missing mandatory input 'ioc_validation_run' for inject execution");
    assertThatThrownBy(
            () -> ExecutableInjectService.refuseIocValidationFileDropWithoutRun(fileDrop, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                ExecutableInjectService.refuseIocValidationFileDropWithoutRun(
                    fileDrop, withoutFileName))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Missing mandatory input 'ioc_validation_file_name' for inject execution");
    assertThatCode(
            () -> ExecutableInjectService.refuseIocValidationFileDropWithoutRun(fileDrop, withRun))
        .doesNotThrowAnyException();
    assertThatCode(
            () ->
                ExecutableInjectService.refuseIocValidationFileDropWithoutRun(
                    userPayload, withoutRun))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("Should block execution when mandatory value is missing")
  void given_mandatoryMissingArgument_should_blockExecution() throws Exception {
    // Arrange
    Method method =
        ExecutableInjectService.class.getDeclaredMethod(
            "processAndEncodeCommand",
            String.class,
            String.class,
            List.class,
            ObjectNode.class,
            List.class,
            String.class);
    method.setAccessible(true);

    ObjectNode injectContent = JsonNodeFactory.instance.objectNode();
    ObjectNode usernameField = JsonNodeFactory.instance.objectNode();
    usernameField.put(InjectorContract.CONTRACT_ELEMENT_CONTENT_KEY, "username");
    usernameField.put(InjectorContract.CONTRACT_ELEMENT_CONTENT_MANDATORY, true);
    usernameField.put(InjectorContract.DEFAULT_VALUE_FIELD, "");

    // Act / Assert
    assertThatThrownBy(
            () ->
                method.invoke(
                    service,
                    "nxc smb -u #{username}",
                    "sh",
                    List.of(payloadArgument("username", "")),
                    injectContent,
                    List.of(usernameField),
                    "plain-text"))
        .isInstanceOf(InvocationTargetException.class)
        .hasCauseInstanceOf(IllegalArgumentException.class)
        .hasRootCauseMessage("Missing mandatory input 'username' for inject execution");
  }

  @Test
  @DisplayName("Should keep positional placeholder as empty value when optional input is missing")
  void given_optionalMissingPositional_should_keepEmptyValue() {
    // Arrange
    ObjectNode injectContent = JsonNodeFactory.instance.objectNode();
    String command = "tool run #{mode}";

    ObjectNode modeField = JsonNodeFactory.instance.objectNode();
    modeField.put(InjectorContract.CONTRACT_ELEMENT_CONTENT_KEY, "mode");
    modeField.put(InjectorContract.CONTRACT_ELEMENT_CONTENT_MANDATORY, false);
    modeField.put(InjectorContract.DEFAULT_VALUE_FIELD, "");

    // Act
    String result =
        service.resolveArgumentsForDisplay(
            command, List.of(payloadArgument("mode", "")), List.of(modeField), injectContent);

    // Assert
    assertThat(result).isEqualTo("tool run ");
  }

  private static PayloadArgument payloadArgument(String key, String defaultValue) {
    PayloadArgument payloadArgument = new PayloadArgument();
    payloadArgument.setType(PrimitiveType.Text);
    payloadArgument.setKey(key);
    payloadArgument.setDefaultValue(defaultValue);
    return payloadArgument;
  }
}
