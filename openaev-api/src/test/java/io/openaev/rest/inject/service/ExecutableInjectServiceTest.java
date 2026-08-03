package io.openaev.rest.inject.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.PrimitiveType;
import io.openaev.database.model.PayloadArgument;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ExecutableInjectService tests")
class ExecutableInjectServiceTest {

  private final ExecutableInjectService service =
      new ExecutableInjectService(null, null, null, null, null);

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
        service.replaceArgumentsByValue(
            command, List.of(payloadArgument("message", "fallback")), List.of(), injectContent);

    // Assert
    assertThat(result)
        .isEqualTo("cat #{payload_location}/linpeas.sh && cd #{location} && echo hello ");
  }

  private static PayloadArgument payloadArgument(String key, String defaultValue) {
    PayloadArgument payloadArgument = new PayloadArgument();
    payloadArgument.setType(PrimitiveType.Text);
    payloadArgument.setKey(key);
    payloadArgument.setDefaultValue(defaultValue);
    return payloadArgument;
  }
}
