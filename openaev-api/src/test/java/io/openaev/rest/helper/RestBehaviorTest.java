package io.openaev.rest.helper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import io.openaev.config.TenantFilteringException;
import io.openaev.database.model.Filters.FilterOperator;
import io.openaev.helper.ObjectMapperHelper;
import io.openaev.rest.exception.ChainingException;
import io.openaev.rest.exception.ChainingOperationNotSupportedException;
import io.openaev.rest.payload.output_parser.OutputParserInput;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springdoc.api.ErrorMessage;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.ExceptionHandlerMethodResolver;

@DisplayName("RestBehavior exception mapping")
class RestBehaviorTest {

  @Test
  @DisplayName("a tenant-filtering refusal maps to 500 with a clear code")
  void tenantFilteringRefusalMapsToClear500() {
    ResponseEntity<ErrorMessage> response =
        new RestBehavior().handleTenantFilteringException(new TenantFilteringException("refused"));

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    assertEquals("TENANT_FILTERING_REFUSED", response.getBody().getMessage());
  }

  @Test
  @DisplayName("the handler is resolved even when the refusal is wrapped (Hibernate wraps it)")
  void handlerResolvedThroughWrappedCause() {
    // Uses the resolver Spring MVC itself uses, so this proves the cause-chain resolution rather
    // than assuming it: a TenantFilteringException nested under a wrapper still selects the
    // handler.
    Method resolved =
        new ExceptionHandlerMethodResolver(RestBehavior.class)
            .resolveMethodByThrowable(
                new RuntimeException(
                    "wrapped by the persistence layer", new TenantFilteringException("refused")));

    assertEquals("handleTenantFilteringException", resolved.getName());
  }

  @Nested
  @DisplayName("ChainingOperationNotSupportedException handling")
  class ChainingOperationNotSupportedHandling {

    @Test
    @DisplayName("a refused chaining operation returns a 400 carrying its business message")
    void given_refusedChainingOperation_should_return400WithMessage() {
      // GIVEN - the product decision refusal (pausing a chained simulation)
      ChainingOperationNotSupportedException ex =
          new ChainingOperationNotSupportedException(
              "Pausing a chained simulation is not allowed yet, please contact support");

      // WHEN
      ResponseEntity<ErrorMessage> response =
          new RestBehavior().handleChainingOperationNotSupportedException(ex);

      // THEN
      assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
      assertNotNull(response.getBody());
      assertEquals(
          "Pausing a chained simulation is not allowed yet, please contact support",
          response.getBody().getMessage());
    }

    @Test
    @DisplayName("the handler is registered for ChainingOperationNotSupportedException")
    void given_chainingOperationNotSupported_should_resolveCorrectHandler() {
      // GIVEN
      ExceptionHandlerMethodResolver resolver =
          new ExceptionHandlerMethodResolver(RestBehavior.class);

      // WHEN
      Method resolved =
          resolver.resolveMethodByThrowable(new ChainingOperationNotSupportedException("refused"));

      // THEN
      assertNotNull(resolved);
      assertEquals("handleChainingOperationNotSupportedException", resolved.getName());
    }

    @Test
    @DisplayName("the checked ChainingException stays unmapped (internal engine failures keep 500)")
    void given_chainingException_should_notResolveAnyHandler() {
      // GIVEN - the generic internal chaining wrapper, deliberately left unhandled
      ExceptionHandlerMethodResolver resolver =
          new ExceptionHandlerMethodResolver(RestBehavior.class);

      // WHEN
      Method resolved = resolver.resolveMethodByThrowable(new ChainingException("engine failure"));

      // THEN
      assertNull(resolved);
    }
  }

  @Nested
  @DisplayName("HttpMessageNotReadableException handling")
  class HttpMessageNotReadableHandling {

    @Test
    @DisplayName("plain deserialization failure returns structured 400 with generic message")
    void given_plainDeserializationFailure_should_return400WithGenericMessage() {
      // GIVEN
      HttpMessageNotReadableException ex =
          new HttpMessageNotReadableException("JSON parse error", (Throwable) null, null);

      // WHEN
      ResponseEntity<ErrorMessage> response = new RestBehavior().handleHttpMessageNotReadable(ex);

      // THEN
      assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
      assertNotNull(response.getBody());
      assertEquals("Malformed or unreadable request body", response.getBody().getMessage());
    }

    @Test
    @DisplayName("handler is registered and resolved for HttpMessageNotReadableException")
    void given_httpMessageNotReadableException_should_resolveCorrectHandler() {
      // GIVEN
      ExceptionHandlerMethodResolver resolver =
          new ExceptionHandlerMethodResolver(RestBehavior.class);

      // WHEN
      Method resolved =
          resolver.resolveMethodByThrowable(
              new HttpMessageNotReadableException("JSON parse error", (Throwable) null, null));

      // THEN
      assertNotNull(resolved);
      assertEquals("handleHttpMessageNotReadable", resolved.getName());
    }

    @Test
    @DisplayName("InvalidFormatException cause yields a 400 naming the offending field and value")
    void given_invalidFormatCause_should_nameFieldAndValue() {
      // GIVEN - the failure shape produced when an enum value cannot be deserialized (#6927)
      InvalidFormatException ife =
          new InvalidFormatException(
              (JsonParser) null, "Cannot deserialize", "WRONG_OP", FilterOperator.class);
      ife.prependPath(new JsonMappingException.Reference(null, "operator"));
      HttpMessageNotReadableException ex =
          new HttpMessageNotReadableException("JSON parse error", ife, null);

      // WHEN
      ResponseEntity<ErrorMessage> response = new RestBehavior().handleHttpMessageNotReadable(ex);

      // THEN
      assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
      assertNotNull(response.getBody());
      assertEquals(
          "Invalid value 'WRONG_OP' for field 'operator'", response.getBody().getMessage());
    }

    @Test
    @DisplayName("array indices are attached to their parent segment in the field path")
    void given_nestedArrayPath_should_renderIndexAttachedToParent() {
      // GIVEN - an invalid enum nested inside a filter list: filters[0].operator
      InvalidFormatException ife =
          new InvalidFormatException(
              (JsonParser) null, "Cannot deserialize", "WRONG_OP", FilterOperator.class);
      ife.prependPath(new JsonMappingException.Reference(null, "operator"));
      ife.prependPath(new JsonMappingException.Reference(null, 0));
      ife.prependPath(new JsonMappingException.Reference(null, "filters"));
      HttpMessageNotReadableException ex =
          new HttpMessageNotReadableException("JSON parse error", ife, null);

      // WHEN
      ResponseEntity<ErrorMessage> response = new RestBehavior().handleHttpMessageNotReadable(ex);

      // THEN
      assertNotNull(response.getBody());
      assertEquals(
          "Invalid value 'WRONG_OP' for field 'filters[0].operator'",
          response.getBody().getMessage());
    }

    @Test
    @DisplayName("oversized rejected value is truncated in the 400 message")
    void given_oversizedRejectedValue_should_truncateInMessage() {
      // GIVEN - a caller-supplied value far beyond the 100-char echo bound
      String hugeValue = "X".repeat(500);
      InvalidFormatException ife =
          new InvalidFormatException(
              (JsonParser) null, "Cannot deserialize", hugeValue, FilterOperator.class);
      ife.prependPath(new JsonMappingException.Reference(null, "operator"));
      HttpMessageNotReadableException ex =
          new HttpMessageNotReadableException("JSON parse error", ife, null);

      // WHEN
      ResponseEntity<ErrorMessage> response = new RestBehavior().handleHttpMessageNotReadable(ex);

      // THEN
      assertNotNull(response.getBody());
      String message = response.getBody().getMessage();
      assertTrue(message.contains("X".repeat(100) + "..."));
      assertTrue(message.length() < 200);
    }

    @Test
    @DisplayName(
        "deserialization failure body includes 'Malformed' so callers can distinguish from "
            + "validation errors")
    void given_deserializationFailure_should_containDiagnosticKeyword() {
      // GIVEN
      HttpMessageNotReadableException ex =
          new HttpMessageNotReadableException("unexpected token", (Throwable) null, null);

      // WHEN
      ResponseEntity<ErrorMessage> response = new RestBehavior().handleHttpMessageNotReadable(ex);

      // THEN
      assertNotNull(response.getBody());
      assertTrue(response.getBody().getMessage().contains("Malformed"));
    }

    @Test
    @DisplayName(
        "IllegalArgumentException from @JsonCreator (wrapped by Jackson) is returned as the 400"
            + " message")
    void given_jsonCreatorIllegalArgument_should_surfaceCreatorMessage() {
      // GIVEN - Jackson wraps @JsonCreator IAE in JsonMappingException /
      // ValueInstantiationException
      IllegalArgumentException iae =
          new IllegalArgumentException(
              "output_parser_type must be REGEX; finding types like credentials belong in"
                  + " contract_output_element_type, not output_parser_type. Got: credentials");
      JsonMappingException wrapped =
          JsonMappingException.from((JsonParser) null, "Cannot construct instance", iae);
      HttpMessageNotReadableException ex =
          new HttpMessageNotReadableException("JSON parse error", wrapped, null);

      // WHEN
      ResponseEntity<ErrorMessage> response = new RestBehavior().handleHttpMessageNotReadable(ex);

      // THEN
      assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
      assertNotNull(response.getBody());
      assertEquals(iae.getMessage(), response.getBody().getMessage());
    }

    @Test
    @DisplayName(
        "a real Jackson enum @JsonCreator failure surfaces the allowed values in the 400 message")
    void given_realJacksonEnumCreatorFailure_should_surfaceAllowedValues() {
      // GIVEN - the exact server-side failure shape: the application mapper deserializing the
      // real input DTO with an unknown enum value (Jackson wraps the creator
      // IllegalArgumentException in a ValueInstantiationException)
      JacksonException jacksonFailure =
          assertThrows(
              JacksonException.class,
              () ->
                  ObjectMapperHelper.openAEVJsonMapper()
                      .readValue(
                          "{\"output_parser_mode\":\"STDOUT\","
                              + "\"output_parser_type\":\"credentials\"}",
                          OutputParserInput.class));
      HttpMessageNotReadableException ex =
          new HttpMessageNotReadableException("JSON parse error", jacksonFailure, null);

      // WHEN
      ResponseEntity<ErrorMessage> response = new RestBehavior().handleHttpMessageNotReadable(ex);

      // THEN
      assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
      assertNotNull(response.getBody());
      assertTrue(response.getBody().getMessage().contains("output_parser_type must be REGEX"));
    }

    @Test
    @DisplayName("the deepest IllegalArgumentException in the cause chain provides the 400 message")
    void given_nestedIllegalArguments_should_surfaceDeepestMessage() {
      // GIVEN - an intermediate IAE wrapper restating the root creator message with noise
      IllegalArgumentException root =
          new IllegalArgumentException(
              "output_parser_mode must be STDOUT, STDERR, or READ_FILE. Got: pipe");
      IllegalArgumentException outer = new IllegalArgumentException("Cannot deserialize", root);
      HttpMessageNotReadableException ex =
          new HttpMessageNotReadableException("JSON parse error", outer, null);

      // WHEN
      ResponseEntity<ErrorMessage> response = new RestBehavior().handleHttpMessageNotReadable(ex);

      // THEN
      assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
      assertNotNull(response.getBody());
      assertEquals(root.getMessage(), response.getBody().getMessage());
    }

    @Test
    @DisplayName("a cyclic cause chain terminates and falls back to the generic message")
    void given_cyclicCauseChain_should_fallBackToGenericMessage() {
      // GIVEN - two exceptions referencing each other as causes
      RuntimeException first = new RuntimeException("first");
      RuntimeException second = new RuntimeException("second", first);
      first.initCause(second);
      HttpMessageNotReadableException ex =
          new HttpMessageNotReadableException("JSON parse error", first, null);

      // WHEN
      ResponseEntity<ErrorMessage> response = new RestBehavior().handleHttpMessageNotReadable(ex);

      // THEN
      assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
      assertNotNull(response.getBody());
      assertEquals("Malformed or unreadable request body", response.getBody().getMessage());
    }

    @Test
    @DisplayName("a blank IllegalArgumentException message falls back to the generic message")
    void given_blankIllegalArgumentMessage_should_fallBackToGenericMessage() {
      // GIVEN
      HttpMessageNotReadableException ex =
          new HttpMessageNotReadableException(
              "JSON parse error", new IllegalArgumentException("   "), null);

      // WHEN
      ResponseEntity<ErrorMessage> response = new RestBehavior().handleHttpMessageNotReadable(ex);

      // THEN
      assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
      assertNotNull(response.getBody());
      assertEquals("Malformed or unreadable request body", response.getBody().getMessage());
    }
  }

  @Nested
  @DisplayName("MethodArgumentNotValidException handling (bean-validation 400 summary)")
  class MethodArgumentNotValidHandling {

    /** Input shape exercising the internal-name -> JSON-name mapping. */
    static class SampleValidationInput {
      @JsonProperty("sample_name")
      public String name;

      @JsonProperty("sample_description")
      public String description;
    }

    @SuppressWarnings("unused")
    private void sampleEndpoint(SampleValidationInput input) {}

    private RestBehavior behavior() {
      RestBehavior behavior = new RestBehavior();
      behavior.mapper = ObjectMapperHelper.openAEVJsonMapper();
      return behavior;
    }

    private BeanPropertyBindingResult emptyBinding() {
      return new BeanPropertyBindingResult(new SampleValidationInput(), "input");
    }

    private MethodArgumentNotValidException exceptionFor(BeanPropertyBindingResult binding)
        throws NoSuchMethodException {
      MethodParameter parameter =
          new MethodParameter(
              MethodArgumentNotValidHandling.class.getDeclaredMethod(
                  "sampleEndpoint", SampleValidationInput.class),
              0);
      return new MethodArgumentNotValidException(parameter, binding);
    }

    @Test
    @DisplayName(
        "field reasons are folded into the top-level message using JSON names, deterministically")
    void given_fieldErrors_should_summarizeReasonsInTopLevelMessage() throws NoSuchMethodException {
      // GIVEN - errors added out of alphabetical order to prove the summary is deterministic
      BeanPropertyBindingResult binding = emptyBinding();
      binding.addError(new FieldError("input", "name", "must not be blank"));
      binding.addError(new FieldError("input", "description", "size must be between 1 and 255"));

      // WHEN
      ValidationErrorBag bag = behavior().handleValidationExceptions(exceptionFor(binding));

      // THEN
      assertEquals(
          "sample_description: size must be between 1 and 255; sample_name: must not be blank",
          bag.getMessage());
      assertTrue(bag.getErrors().getChildren().containsKey("sample_name"));
      assertTrue(bag.getErrors().getChildren().containsKey("sample_description"));
    }

    @Test
    @DisplayName("a class-level ObjectError is labeled by object name instead of throwing")
    void given_objectError_should_labelByObjectNameInsteadOfClassCast()
        throws NoSuchMethodException {
      // GIVEN - a cross-field validator reports on the object, not a field
      BeanPropertyBindingResult binding = emptyBinding();
      binding.addError(new ObjectError("input", "start date must precede end date"));

      // WHEN - must not throw ClassCastException (which would turn the 400 into a 500)
      ValidationErrorBag bag = behavior().handleValidationExceptions(exceptionFor(binding));

      // THEN
      assertEquals("input: start date must precede end date", bag.getMessage());
      assertTrue(bag.getErrors().getChildren().containsKey("input"));
    }

    @Test
    @DisplayName("a nested field path absent from the JSON mapping keeps a non-null children key")
    void given_unmappedNestedFieldPath_should_fallBackToRawFieldName()
        throws NoSuchMethodException {
      // GIVEN - @Valid nested/indexed paths are absent from the flat JSON mapping; a null map
      // key would make Jackson fail to serialize the 400 body
      BeanPropertyBindingResult binding = emptyBinding();
      binding.addError(new FieldError("input", "items[0].name", "must not be blank"));

      // WHEN
      ValidationErrorBag bag = behavior().handleValidationExceptions(exceptionFor(binding));

      // THEN
      assertEquals("items[0].name: must not be blank", bag.getMessage());
      assertTrue(bag.getErrors().getChildren().containsKey("items[0].name"));
      assertFalse(bag.getErrors().getChildren().containsKey(null));
    }

    @Test
    @DisplayName("a constraint without a default message degrades to the field label alone")
    void given_nullDefaultMessage_should_useLabelWithoutNullText() throws NoSuchMethodException {
      // GIVEN
      BeanPropertyBindingResult binding = emptyBinding();
      binding.addError(new FieldError("input", "name", null));

      // WHEN - must not throw (ValidationContent wraps the message in List.of, which
      // rejects null)
      ValidationErrorBag bag = behavior().handleValidationExceptions(exceptionFor(binding));

      // THEN
      assertEquals("sample_name", bag.getMessage());
      assertEquals(
          List.of("Invalid value"), bag.getErrors().getChildren().get("sample_name").getErrors());
    }

    @Test
    @DisplayName("the summary is bounded in part count with an explicit overflow indicator")
    void given_manyErrors_should_boundPartCountAndReportOverflow() throws NoSuchMethodException {
      // GIVEN - 12 distinct reasons, 4 beyond the 8-part bound
      BeanPropertyBindingResult binding = emptyBinding();
      for (int i = 0; i < 12; i++) {
        binding.addError(new FieldError("input", "items[" + i + "].name", "must not be blank"));
      }

      // WHEN
      ValidationErrorBag bag = behavior().handleValidationExceptions(exceptionFor(binding));

      // THEN
      assertTrue(bag.getMessage().endsWith("(+4 more)"));
    }

    @Test
    @DisplayName("the summary never exceeds the advertised length bound, ellipsis included")
    void given_oversizedReason_should_truncateWithinBound() throws NoSuchMethodException {
      // GIVEN - a reason far beyond the 300-char summary bound
      BeanPropertyBindingResult binding = emptyBinding();
      binding.addError(new FieldError("input", "name", "X".repeat(500)));

      // WHEN
      ValidationErrorBag bag = behavior().handleValidationExceptions(exceptionFor(binding));

      // THEN
      assertEquals(300, bag.getMessage().length());
      assertTrue(bag.getMessage().endsWith("..."));
    }

    @Test
    @DisplayName("an empty binding result falls back to the generic message")
    void given_noErrors_should_fallBackToGenericMessage() throws NoSuchMethodException {
      // GIVEN / WHEN
      ValidationErrorBag bag = behavior().handleValidationExceptions(exceptionFor(emptyBinding()));

      // THEN
      assertEquals("Validation Failed", bag.getMessage());
    }

    @Test
    @DisplayName("control characters in constraint messages are flattened to a single line")
    void given_multilineMessage_should_flattenToSingleLine() throws NoSuchMethodException {
      // GIVEN
      BeanPropertyBindingResult binding = emptyBinding();
      binding.addError(new FieldError("input", "name", "line one\nline two\ttab"));

      // WHEN
      ValidationErrorBag bag = behavior().handleValidationExceptions(exceptionFor(binding));

      // THEN
      assertEquals("sample_name: line one line two tab", bag.getMessage());
    }

    @Test
    @DisplayName("identical reasons reported by several constraints are deduplicated")
    void given_duplicateReasons_should_deduplicate() throws NoSuchMethodException {
      // GIVEN - e.g. @NotBlank and @Size both rejecting the same field with the same text
      BeanPropertyBindingResult binding = emptyBinding();
      binding.addError(new FieldError("input", "name", "must not be blank"));
      binding.addError(new FieldError("input", "name", "must not be blank"));

      // WHEN
      ValidationErrorBag bag = behavior().handleValidationExceptions(exceptionFor(binding));

      // THEN
      assertEquals("sample_name: must not be blank", bag.getMessage());
    }
  }
}
