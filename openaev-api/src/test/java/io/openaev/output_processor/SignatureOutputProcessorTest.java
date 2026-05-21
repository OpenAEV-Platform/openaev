package io.openaev.output_processor;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.database.model.ContractOutputTechnicalType;
import io.openaev.database.model.ContractOutputType;
import io.openaev.rest.inject.service.ContractOutputContext;
import io.openaev.rest.inject.service.ExecutionProcessingContext;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SignatureOutputProcessorTest {

  private final SignatureOutputProcessor processor = new SignatureOutputProcessor();
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  @DisplayName("Should expose Signature type")
  void shouldExposeSignatureType() {
    assertEquals(ContractOutputType.ExpectationSignature, processor.getType());
  }

  @Test
  @DisplayName("Should expose Object technical type")
  void shouldExposeObjectTechnicalType() {
    assertEquals(ContractOutputTechnicalType.Object, processor.getTechnicalType());
  }

  @Test
  @DisplayName("Should expose empty fields")
  void shouldExposeEmptyFields() {
    assertEquals(List.of(), processor.getFields());
  }

  @Test
  @DisplayName("Should validate non-null json node and reject null")
  void shouldValidateNonNullJsonNodeAndRejectNull() throws Exception {
    assertTrue(processor.validate(objectMapper.readTree("{}")));
    assertFalse(processor.validate(null));
  }

  @Test
  @DisplayName("Should process signature output as no-op")
  void shouldProcessSignatureOutputAsNoOp() {
    ExecutionProcessingContext executionProcessingContext = mock(ExecutionProcessingContext.class);
    ContractOutputContext contractOutputContext = mock(ContractOutputContext.class);

    assertDoesNotThrow(
        () ->
            processor.process(
                executionProcessingContext, contractOutputContext, objectMapper.readTree("{}")));
  }
}
