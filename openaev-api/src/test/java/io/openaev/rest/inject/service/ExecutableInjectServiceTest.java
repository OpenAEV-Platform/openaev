package io.openaev.rest.inject.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.Document;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.PayloadArgument;
import io.openaev.database.model.PrimitiveType;
import io.openaev.rest.document.DocumentService;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ExecutableInjectService tests")
class ExecutableInjectServiceTest {

  private final ExecutableInjectService service =
      new ExecutableInjectService(null, null, null, null, null, null);

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
            command,
            List.of(payloadArgument("message", "fallback")),
            List.of(),
            injectContent,
            "tenant-1");

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
            injectContent,
            "tenant-1");

    // Assert
    assertThat(result).isEqualTo("nxc smb 10.10.10.10 -u  --shares");
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
            String.class,
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
                    "plain-text",
                    "tenant-1"))
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
            command,
            List.of(payloadArgument("mode", "")),
            List.of(modeField),
            injectContent,
            "tenant-1");

    // Assert
    assertThat(result).isEqualTo("tool run ");
  }

  @Test
  @DisplayName("Should point a document argument at the last component of the stored name")
  void given_documentArgumentWithStoredPath_should_resolveToLastComponent() {
    // Arrange: a document stored before the upload sanitization existed
    Document legacyDocument = new Document();
    legacyDocument.setName("../../ba03_direct.txt");
    DocumentService documentService = mock(DocumentService.class);
    when(documentService.documentInTenant("document-id", "tenant-1")).thenReturn(legacyDocument);
    ExecutableInjectService serviceWithDocuments =
        new ExecutableInjectService(null, documentService, null, null, null, null);

    PayloadArgument fileArgument = payloadArgument("file", "document-id");
    fileArgument.setType(PrimitiveType.Document);

    // Act
    String result =
        serviceWithDocuments.resolveArgumentsForDisplay(
            "cat #{file}",
            List.of(fileArgument),
            List.of(),
            JsonNodeFactory.instance.objectNode(),
            "tenant-1");

    // Assert
    assertThat(result).isEqualTo("cat #{location}/ba03_direct.txt");
  }

  @Test
  @DisplayName("Should refuse a document argument resolving in another tenant")
  void given_documentArgumentInAnotherTenant_should_leaveValueMissing() {
    // Arrange
    DocumentService documentService = mock(DocumentService.class);
    when(documentService.documentInTenant("document-id", "tenant-1"))
        .thenThrow(new io.openaev.rest.exception.ElementNotFoundException("Document not found"));
    ExecutableInjectService serviceWithDocuments =
        new ExecutableInjectService(null, documentService, null, null, null, null);

    PayloadArgument fileArgument = payloadArgument("file", "document-id");
    fileArgument.setType(PrimitiveType.Document);

    // Act
    String result =
        serviceWithDocuments.resolveArgumentsForDisplay(
            "cat #{file}",
            List.of(fileArgument),
            List.of(),
            JsonNodeFactory.instance.objectNode(),
            "tenant-1");

    // Assert: the document does not resolve inside the inject's own tenant, so it is treated
    // like an unknown id, the same as an id that resolves nowhere at all (the raw value is left
    // in place rather than the resolved file name), never a foreign file's name.
    assertThat(result).isEqualTo("cat document-id");
  }

  private static PayloadArgument payloadArgument(String key, String defaultValue) {
    PayloadArgument payloadArgument = new PayloadArgument();
    payloadArgument.setType(PrimitiveType.Text);
    payloadArgument.setKey(key);
    payloadArgument.setDefaultValue(defaultValue);
    return payloadArgument;
  }
}
