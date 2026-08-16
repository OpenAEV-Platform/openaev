package io.openaev.rest.helper;

import static io.openaev.config.OpenAEVAnonymous.ANONYMOUS;
import static io.openaev.config.SessionHelper.currentUser;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import io.openaev.aop.audit_log.AuditLogFailureException;
import io.openaev.aop.lock.LockAcquisitionException;
import io.openaev.config.TenantFilteringException;
import io.openaev.database.model.User;
import io.openaev.database.repository.UserRepository;
import io.openaev.ee.EnterpriseEditionException;
import io.openaev.rest.exception.*;
import io.openaev.security.error.AuthenticationError;
import io.openaev.stix.parsing.ParsingException;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.annotation.Resource;
import jakarta.persistence.EntityNotFoundException;
import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springdoc.api.ErrorMessage;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.reactive.function.UnsupportedMediaTypeException;

@RestControllerAdvice
@Slf4j
public class RestBehavior {

  @Resource protected ObjectMapper mapper;

  // Build the mapping between json specific name and the actual database field name
  private Map<String, String> buildJsonMappingFields(MethodArgumentNotValidException ex) {
    Class<?> inputClass = Objects.requireNonNull(ex.getBindingResult().getTarget()).getClass();
    return buildJsonMappingFields(inputClass);
  }

  // Internal-name -> JSON-name mapping for a bound input class, shared by the two validation
  // handlers. The merge function keeps a duplicate internal name from failing the collector with an
  // IllegalStateException inside the handler (which would turn the 400 into a 500).
  private Map<String, String> buildJsonMappingFields(Class<?> inputClass) {
    JavaType javaType = mapper.getTypeFactory().constructType(inputClass);
    BeanDescription beanDescription = mapper.getSerializationConfig().introspect(javaType);
    return beanDescription.findProperties().stream()
        .collect(
            Collectors.toMap(
                BeanPropertyDefinition::getInternalName,
                BeanPropertyDefinition::getName,
                (existing, ignored) -> existing));
  }

  // -- 400 BAD_REQUEST --

  private static final int MAX_REJECTED_VALUE_LENGTH = 100;
  private static final int MAX_UNREADABLE_DETAIL_LENGTH = 300;

  // Bound the caller-supplied rejected value echoed back in the 400 body / logs
  private static String abbreviateRejectedValue(Object value) {
    String rendered = String.valueOf(value);
    return rendered.length() <= MAX_REJECTED_VALUE_LENGTH
        ? rendered
        : rendered.substring(0, MAX_REJECTED_VALUE_LENGTH) + "...";
  }

  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorMessage> handleHttpMessageNotReadable(
      HttpMessageNotReadableException ex) {
    String detail = unreadableBodyDetail(ex);
    log.warn("HttpMessageNotReadableException: {}", detail, ex);
    return new ResponseEntity<>(new ErrorMessage(detail), HttpStatus.BAD_REQUEST);
  }

  private static String unreadableBodyDetail(HttpMessageNotReadableException ex) {
    InvalidFormatException ife = findDeepestCause(ex, InvalidFormatException.class);
    if (ife != null) {
      return invalidFormatDetail(ife);
    }
    // @JsonCreator methods throw IllegalArgumentException; Jackson wraps that in
    // ValueInstantiationException / JsonMappingException. Surface the creator message so clients
    // get an actionable 400 instead of the generic "Malformed or unreadable request body".
    IllegalArgumentException iae = findDeepestCause(ex, IllegalArgumentException.class);
    if (iae != null && iae.getMessage() != null && !iae.getMessage().isBlank()) {
      return abbreviateMessage(iae.getMessage());
    }
    return "Malformed or unreadable request body";
  }

  private static String invalidFormatDetail(InvalidFormatException ife) {
    // Render array indices attached to their parent segment: filters[0].operator
    StringBuilder pathBuilder = new StringBuilder();
    for (var ref : ife.getPath()) {
      if (ref.getFieldName() != null) {
        if (pathBuilder.length() > 0) {
          pathBuilder.append('.');
        }
        pathBuilder.append(ref.getFieldName());
      } else {
        pathBuilder.append('[').append(ref.getIndex()).append(']');
      }
    }
    return "Invalid value '%s' for field '%s'"
        .formatted(abbreviateRejectedValue(ife.getValue()), pathBuilder.toString());
  }

  private static String abbreviateMessage(String message) {
    return message.length() <= MAX_UNREADABLE_DETAIL_LENGTH
        ? message
        : message.substring(0, MAX_UNREADABLE_DETAIL_LENGTH) + "...";
  }

  // Returns the deepest matching cause: outer wrappers (Spring, Jackson) restate the original
  // message with framework noise, so the root cause carries the actionable text. The
  // identity-based visited set guards against cyclic cause chains.
  private static <T extends Throwable> T findDeepestCause(Throwable throwable, Class<T> type) {
    T deepest = null;
    Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
    Throwable current = throwable;
    while (current != null && visited.add(current)) {
      if (type.isInstance(current)) {
        deepest = type.cast(current);
      }
      current = current.getCause();
    }
    return deepest;
  }

  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ValidationErrorBag handleValidationExceptions(MethodArgumentNotValidException ex) {
    Map<String, String> jsonFieldsMapping = buildJsonMappingFields(ex);
    ValidationErrorBag bag = new ValidationErrorBag();
    ValidationError errors = new ValidationError();
    Map<String, ValidationContent> errorsBag = new HashMap<>();
    List<String> summaryParts = new ArrayList<>();
    ex.getBindingResult()
        .getAllErrors()
        .forEach(
            (error) -> {
              // Class-level constraints (cross-field validators) surface as ObjectError, not
              // FieldError: an unconditional cast would turn this 400 into a 500. Label those
              // with the validated object's name instead. The raw-name fallback also keeps the
              // children key non-null for nested/indexed field paths absent from the flat JSON
              // mapping - Jackson refuses to serialize a null map key, which was another 500.
              String rawName =
                  error instanceof FieldError fieldError
                      ? fieldError.getField()
                      : error.getObjectName();
              String label = jsonFieldsMapping.getOrDefault(rawName, rawName);
              String errorMessage = error.getDefaultMessage();
              // getDefaultMessage() is nullable and ValidationContent wraps it in List.of(),
              // which rejects null - degrade to a generic reason instead of a 500.
              errorsBag.put(
                  label,
                  new ValidationContent(errorMessage == null ? "Invalid value" : errorMessage));
              summaryParts.add(summaryPart(label, errorMessage));
            });
    errors.setChildren(errorsBag);
    bag.setErrors(errors);
    // Replace the generic "Validation Failed" top-level message with a concise
    // summary of the per-field reasons. Those reasons otherwise live only in
    // errors.children, so any client that reads just "message" - the frontend
    // toast, a plain API caller, an AI agent's tool wrapper - gets an opaque 400
    // and cannot self-correct (the exact "why did the arsenal fork bad-request"
    // pain). The per-field children bag is left unchanged for structured clients.
    bag.setMessage(summarizeValidationErrors(summaryParts));
    return bag;
  }

  private static final int MAX_VALIDATION_SUMMARY_LENGTH = 300;
  private static final int MAX_VALIDATION_SUMMARY_PARTS = 8;

  // One "label: reason" summary entry, degrading to whichever half is present so a
  // constraint without a default message never renders as "field: null".
  private static String summaryPart(String label, String message) {
    boolean hasLabel = label != null && !label.isBlank();
    boolean hasMessage = message != null && !message.isBlank();
    if (hasLabel && hasMessage) {
      return label + ": " + message;
    }
    if (hasLabel) {
      return label;
    }
    return hasMessage ? message : null;
  }

  // Collapse control characters and whitespace runs to single spaces: the summary must
  // stay a single line whatever a constraint message contains, so it is safe to log and
  // to render in a toast.
  private static String sanitizeSummaryText(String text) {
    return text.replaceAll("[\\p{Cntrl}\\s]+", " ").trim();
  }

  // Fold the per-field validation reasons into one human-readable top-level
  // message. Bounded in count and length so the response stays small and
  // log-safe; falls back to the generic message when nothing usable was
  // collected (so an empty binding result is never surfaced as a blank reason).
  private static String summarizeValidationErrors(List<String> parts) {
    // Deduplicated and sorted: bean validation reports violations in no guaranteed order,
    // so sorting keeps the message stable for identical payloads.
    List<String> usable =
        parts.stream()
            .filter(Objects::nonNull)
            .map(RestBehavior::sanitizeSummaryText)
            .filter(p -> !p.isBlank())
            .distinct()
            .sorted()
            .toList();
    if (usable.isEmpty()) {
      return "Validation Failed";
    }
    String joined =
        usable.stream().limit(MAX_VALIDATION_SUMMARY_PARTS).collect(Collectors.joining("; "));
    if (usable.size() > MAX_VALIDATION_SUMMARY_PARTS) {
      joined += " (+" + (usable.size() - MAX_VALIDATION_SUMMARY_PARTS) + " more)";
    }
    // Truncate INCLUDING the ellipsis so the advertised bound is a real bound.
    return joined.length() <= MAX_VALIDATION_SUMMARY_LENGTH
        ? joined
        : joined.substring(0, MAX_VALIDATION_SUMMARY_LENGTH - 3) + "...";
  }

  // Spring 6.2 routes controller method validation through HandlerMethodValidationException, NOT
  // MethodArgumentNotValidException, whenever a handler mixes a method-level constraint with a
  // cascaded body - e.g. updateAction's `@NotBlank @PathVariable actionId` alongside a
  // `@Valid @RequestBody`. RestBehavior does not extend ResponseEntityExceptionHandler, so with no
  // handler for this class the class-level default applies and the body's "message" is empty
  // (unless server.error.include-message=always, which this codebase deliberately does not set) -
  // the opaque "Bad request" a plain API caller or an AI agent's tool wrapper cannot self-correct
  // from (the exact "why did the arsenal fork bad-request" pain). Mirror the
  // MethodArgumentNotValidException handler: per-field children plus a concise summarized message.
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ExceptionHandler(HandlerMethodValidationException.class)
  public ValidationErrorBag handleHandlerMethodValidationException(
      HandlerMethodValidationException ex) {
    ValidationErrorBag bag = new ValidationErrorBag();
    ValidationError errors = new ValidationError();
    Map<String, ValidationContent> errorsBag = new HashMap<>();
    List<String> summaryParts = new ArrayList<>();
    for (ParameterValidationResult result : ex.getParameterValidationResults()) {
      // A cascaded @Valid bean argument reports FieldError/ObjectError, whose label needs the JSON
      // property name; a direct value constraint (@PathVariable / @RequestParam) reports a plain
      // resolvable, whose best label is the parameter name.
      Map<String, String> jsonFieldsMapping = jsonMappingForArgument(result.getArgument());
      String valueParameterLabel = parameterLabel(result);
      for (MessageSourceResolvable error : result.getResolvableErrors()) {
        String rawName =
            error instanceof FieldError fieldError
                ? fieldError.getField()
                : error instanceof ObjectError objectError
                    ? objectError.getObjectName()
                    : valueParameterLabel;
        String label = jsonFieldsMapping.getOrDefault(rawName, rawName);
        String errorMessage = error.getDefaultMessage();
        // getDefaultMessage() is nullable and ValidationContent wraps it in List.of(), which
        // rejects null - degrade to a generic reason instead of a 500, as the sibling handler does.
        errorsBag.put(
            label, new ValidationContent(errorMessage == null ? "Invalid value" : errorMessage));
        summaryParts.add(summaryPart(label, errorMessage));
      }
    }
    errors.setChildren(errorsBag);
    bag.setErrors(errors);
    bag.setMessage(summarizeValidationErrors(summaryParts));
    log.debug("HandlerMethodValidationException: {}", bag.getMessage(), ex);
    return bag;
  }

  // Best-effort internal-name -> JSON-name mapping for a validated bean argument. Empty when the
  // argument is null (a direct value constraint) or introspection fails, so labeling degrades to
  // the raw field/parameter name instead of throwing a second time inside the handler.
  private Map<String, String> jsonMappingForArgument(Object argument) {
    if (argument == null) {
      return Map.of();
    }
    try {
      return buildJsonMappingFields(argument.getClass());
    } catch (RuntimeException ignored) {
      return Map.of();
    }
  }

  // Parameter name for a value-level constraint, degrading to the declaring type when the name is
  // unavailable (sources not compiled with -parameters); never null so the children key stays valid
  // (Jackson refuses to serialize a null map key, which would be a 500).
  private static String parameterLabel(ParameterValidationResult result) {
    MethodParameter parameter = result.getMethodParameter();
    String name = parameter.getParameterName();
    if (name != null && !name.isBlank()) {
      return name;
    }
    Class<?> type = parameter.getParameterType();
    return type != null ? type.getSimpleName() : "parameter";
  }

  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ExceptionHandler(InputValidationException.class)
  public ValidationErrorBag handleInputValidationExceptions(InputValidationException ex) {
    ValidationErrorBag bag = new ValidationErrorBag();
    ValidationError errors = new ValidationError();
    Map<String, ValidationContent> errorsBag = new HashMap<>();
    errorsBag.put(ex.getField(), new ValidationContent(ex.getMessage()));
    errors.setChildren(errorsBag);
    bag.setErrors(errors);
    return bag;
  }

  /**
   * Method to automatically handle a STIX Parsing error as a BAD REQUEST
   *
   * @param ex the STIX parsing exception object
   * @return Validation bag error structure
   */
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ExceptionHandler(ParsingException.class)
  public ValidationErrorBag handleInputValidationExceptions(ParsingException ex) {
    ValidationErrorBag bag = new ValidationErrorBag();
    ValidationError errors = new ValidationError();
    Map<String, ValidationContent> errorsBag = new HashMap<>();
    errorsBag.put("STIX Bundle", new ValidationContent(ex.getMessage()));
    errors.setChildren(errorsBag);
    bag.setErrors(errors);
    return bag;
  }

  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ExceptionHandler(ImportException.class)
  public ValidationErrorBag handleBadRequestExceptions(ImportException ex) {
    ValidationErrorBag bag =
        new ValidationErrorBag(HttpStatus.BAD_REQUEST.value(), ex.getMessage());
    ValidationError errors = new ValidationError();
    Map<String, ValidationContent> errorsBag = new HashMap<>();
    errorsBag.put(ex.getField(), new ValidationContent(ex.getMessage()));
    errors.setChildren(errorsBag);
    bag.setErrors(errors);
    return bag;
  }

  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ExceptionHandler(ResourceInUseException.class)
  public ViolationErrorBag handleResourceInUseExceptions(Exception ex) {
    ViolationErrorBag bag = new ViolationErrorBag();
    bag.setType(ex.getClass().getSimpleName());
    bag.setMessage(ex.getMessage());

    if (ex.getCause() instanceof Exception) {
      bag.setError(ex.getCause().getMessage());
    } else {
      bag.setError("Resource still linked to other components.");
    }
    return bag;
  }

  @ExceptionHandler(FileTooBigException.class)
  public ResponseEntity<ErrorMessage> handleFileTooBigException(FileTooBigException ex) {
    ErrorMessage message = new ErrorMessage(ex.getMessage());
    log.warn(String.format("FileTooBigException: %s", ex.getMessage()), ex);
    return new ResponseEntity<>(message, HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler(AlreadyExistingException.class)
  public ResponseEntity<ErrorMessage> handleAlreadyExistingException(AlreadyExistingException ex) {
    ErrorMessage message = new ErrorMessage(ex.getMessage());
    log.warn(String.format("AlreadyExistingException: %s", ex.getMessage()), ex);
    return new ResponseEntity<>(message, HttpStatus.BAD_REQUEST);
  }

  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ExceptionHandler(io.openaev.utils.pagination.InvalidSortPropertyException.class)
  public ResponseEntity<ErrorMessage> handleInvalidSortProperty(
      io.openaev.utils.pagination.InvalidSortPropertyException ex) {
    ErrorMessage message = new ErrorMessage(ex.getMessage());
    return new ResponseEntity<>(message, HttpStatus.BAD_REQUEST);
  }

  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ErrorMessage> handleIllegalArgument(IllegalArgumentException ex) {
    ErrorMessage message = new ErrorMessage(ex.getMessage());
    return new ResponseEntity<>(message, HttpStatus.BAD_REQUEST);
  }

  // A translatable code in "message" and the offending keys in
  // errors.children.message.errors, which the client already knows how to read.
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ExceptionHandler(PrivilegeGrantException.class)
  public ValidationErrorBag handlePrivilegeGrantException(PrivilegeGrantException ex) {
    ValidationErrorBag bag = new ValidationErrorBag(HttpStatus.BAD_REQUEST.value(), ex.getCode());
    ValidationContent content = new ValidationContent();
    content.setErrors(ex.getDetails());
    ValidationError errors = new ValidationError();
    errors.setChildren(Map.of("message", content));
    bag.setErrors(errors);
    return bag;
  }

  // Without this handler the class-level @ResponseStatus lets Spring produce the default /error
  // body, whose "message" field is empty unless server.error.include-message=always - the frontend
  // then shows a generic "Bad request" instead of the actual reason.
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ExceptionHandler(BadRequestException.class)
  public ResponseEntity<ErrorMessage> handleBadRequestException(BadRequestException ex) {
    ErrorMessage message = new ErrorMessage(ex.getMessage());
    // Client error thrown all over the codebase: DEBUG (with the stack) keeps prod logs
    // actionable while still supporting troubleshooting.
    log.debug("BadRequestException: {}", ex.getMessage(), ex);
    return new ResponseEntity<>(message, HttpStatus.BAD_REQUEST);
  }

  // A chaining lifecycle operation refused by product decision (e.g. pausing a chained
  // simulation): a client error, not a platform failure, so it must carry its business message
  // with a 400 instead of the 500 the checked ChainingException produced. ChainingException itself
  // is deliberately NOT mapped here: it also carries internal chaining engine failures, which must
  // keep surfacing as 500.
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ExceptionHandler(ChainingOperationNotSupportedException.class)
  public ResponseEntity<ErrorMessage> handleChainingOperationNotSupportedException(
      ChainingOperationNotSupportedException ex) {
    ErrorMessage message = new ErrorMessage(ex.getMessage());
    log.debug("ChainingOperationNotSupportedException: {}", ex.getMessage(), ex);
    return new ResponseEntity<>(message, HttpStatus.BAD_REQUEST);
  }

  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ExceptionHandler(TenantSelectorRequiredException.class)
  public ResponseEntity<ErrorMessage> handleTenantSelectorRequiredException(
      TenantSelectorRequiredException ex) {
    return new ResponseEntity<>(
        new ErrorMessage("TENANT_SELECTOR_REQUIRED"), HttpStatus.BAD_REQUEST);
  }

  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ExceptionHandler(TenantWriteScopeException.class)
  public ResponseEntity<ErrorMessage> handleTenantWriteScopeException(
      TenantWriteScopeException ex) {
    return new ResponseEntity<>(new ErrorMessage("TENANT_WRITE_SCOPE"), HttpStatus.BAD_REQUEST);
  }

  // -- 401 UNAUTHORIZED --

  @ResponseStatus(HttpStatus.UNAUTHORIZED)
  @ExceptionHandler(AuthenticationException.class)
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized",
            content = @Content(schema = @Schema(implementation = ValidationErrorBag.class))),
      })
  public ValidationErrorBag handleValidationExceptions() {
    ValidationErrorBag bag =
        new ValidationErrorBag(HttpStatus.UNAUTHORIZED.value(), "AUTHENTICATION_FAILED");
    ValidationError errors = new ValidationError();
    Map<String, ValidationContent> errorsBag = new HashMap<>();
    errorsBag.put("username", new ValidationContent("Invalid user or password"));
    errors.setChildren(errorsBag);
    bag.setErrors(errors);
    return bag;
  }

  // -- 403 FORBIDDEN --

  @ResponseStatus(HttpStatus.FORBIDDEN)
  @ExceptionHandler(LicenseRestrictionException.class)
  public ValidationErrorBag handleLicenseError(LicenseRestrictionException ex) {
    ValidationErrorBag bag =
        new ValidationErrorBag(HttpStatus.FORBIDDEN.value(), "LICENSE_RESTRICTION");
    ValidationError errors = new ValidationError();
    Map<String, ValidationContent> errorsBag = new HashMap<>();
    errorsBag.put("message", new ValidationContent(ex.getMessage()));
    errors.setChildren(errorsBag);
    bag.setErrors(errors);
    return bag;
  }

  @ResponseStatus(HttpStatus.FORBIDDEN)
  @ExceptionHandler(EnterpriseEditionException.class)
  public ValidationErrorBag handleEnterpriseEditionException(EnterpriseEditionException ex) {
    ValidationErrorBag bag =
        new ValidationErrorBag(HttpStatus.FORBIDDEN.value(), "LICENSE_RESTRICTION");
    ValidationError errors = new ValidationError();
    Map<String, ValidationContent> errorsBag = new HashMap<>();
    errorsBag.put("message", new ValidationContent(ex.getMessage()));
    errors.setChildren(errorsBag);
    bag.setErrors(errors);
    return bag;
  }

  @ResponseStatus(HttpStatus.FORBIDDEN)
  @ExceptionHandler(TenantAccessDeniedException.class)
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "403",
            description = "User is not a member of the requested tenant",
            content = @Content(schema = @Schema(implementation = ResponseEntity.class))),
      })
  public ResponseEntity<ErrorMessage> handleTenantAccessDeniedException(
      TenantAccessDeniedException ex) {
    log.warn(
        "TENANT_ACCESS_DENIED: {} (user={}, {} {})",
        ex.getMessage(),
        currentUser().getId(),
        requestMethod(),
        requestUri());
    return new ResponseEntity<>(new ErrorMessage("TENANT_ACCESS_DENIED"), HttpStatus.FORBIDDEN);
  }

  // -- 500 INTERNAL_SERVER_ERROR --

  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  @ExceptionHandler(AuditLogFailureException.class)
  public ResponseEntity<ErrorMessage> handleAuditLogFailureException(AuditLogFailureException ex) {
    log.error("[AUDIT] Audit transport failure propagated to response: {}", ex.getMessage());
    return new ResponseEntity<>(
        new ErrorMessage("AUDIT_TRANSPORT_FAILURE"), HttpStatus.INTERNAL_SERVER_ERROR);
  }

  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  @ExceptionHandler(TenantFilteringException.class)
  public ResponseEntity<ErrorMessage> handleTenantFilteringException(TenantFilteringException ex) {
    // The inspector refused a statement it could not guarantee to filter (fail-closed). Spring
    // matches this handler even when Hibernate wraps the exception, since it walks the cause chain.
    log.warn("Tenant isolation refused a statement it cannot filter: {}", ex.getMessage());
    return new ResponseEntity<>(
        new ErrorMessage("TENANT_FILTERING_REFUSED"), HttpStatus.INTERNAL_SERVER_ERROR);
  }

  // -- 404 NOT_FOUND --

  @ResponseStatus(HttpStatus.NOT_FOUND)
  @ExceptionHandler(AccessDeniedException.class)
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "404",
            description = "Resource not found",
            content = @Content(schema = @Schema(implementation = ResponseEntity.class))),
      })
  public ResponseEntity<ErrorMessage> handleAccessDeniedExceptions() {
    // When the user does not have the appropriate access rights, return 404 Not Found.
    // This response indicates that the resource does not exist, preventing any information
    // disclosure
    // about the resource and reducing the risk of brute force attacks by not confirming its
    // existence
    return new ResponseEntity<>(
        new ErrorMessage(HttpStatus.NOT_FOUND.getReasonPhrase()), HttpStatus.NOT_FOUND);
  }

  @ExceptionHandler(ElementNotFoundException.class)
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "404",
            description = "Resource not found",
            content = @Content(schema = @Schema(implementation = ResponseEntity.class))),
      })
  public ResponseEntity<ErrorMessage> handleElementNotFoundException(ElementNotFoundException ex) {
    ErrorMessage message = new ErrorMessage("Element not found: " + ex.getMessage());
    log.warn(String.format("ElementNotFoundException: %s", ex.getMessage()), ex);
    return new ResponseEntity<>(message, HttpStatus.NOT_FOUND);
  }

  @ExceptionHandler(EntityNotFoundException.class)
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "404",
            description = "Resource not found",
            content = @Content(schema = @Schema(implementation = ResponseEntity.class))),
      })
  public ResponseEntity<ErrorMessage> handleEntityNotFoundException(EntityNotFoundException ex) {
    ErrorMessage message = new ErrorMessage("Element not found: " + ex.getMessage());
    log.warn(String.format("EntityNotFoundException: %s", ex.getMessage()), ex);
    return new ResponseEntity<>(message, HttpStatus.NOT_FOUND);
  }

  // -- 409 CONFLICT --

  @ResponseStatus(HttpStatus.CONFLICT)
  @ExceptionHandler({DataIntegrityViolationException.class, LockAcquisitionException.class})
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "409",
            description = "Conflict",
            content = @Content(schema = @Schema(implementation = ViolationErrorBag.class))),
      })
  public ViolationErrorBag handleIntegrityException(Exception e) {
    ViolationErrorBag errorBag = new ViolationErrorBag();
    if (e instanceof DataIntegrityViolationException) {
      errorBag.setType(DataIntegrityViolationException.class.getSimpleName());
      if (e.getCause() instanceof ConstraintViolationException violationException) {
        errorBag.setType(ConstraintViolationException.class.getSimpleName());
        errorBag.setMessage("Error applying constraint " + violationException.getConstraintName());
        errorBag.setError(violationException.getMessage());
      } else {
        errorBag.setMessage("Database integrity violation");
        errorBag.setError(e.getMessage());
      }
    } else if (e instanceof LockAcquisitionException) {
      errorBag.setType(LockAcquisitionException.class.getSimpleName());
      errorBag.setMessage("Resource is locked");
      errorBag.setError(e.getMessage());
    }
    return errorBag;
  }

  // -- 415 UNSUPPORTED_MEDIA_TYPE --

  @ExceptionHandler(UnsupportedMediaTypeException.class)
  public ResponseEntity<ErrorMessage> handleUnsupportedMediaTypeException(
      UnsupportedMediaTypeException ex) {
    ErrorMessage message = new ErrorMessage(ex.getMessage());
    log.warn(String.format("UnsupportedMediaTypeException: %s", ex.getMessage()), ex);
    return new ResponseEntity<>(message, HttpStatus.UNSUPPORTED_MEDIA_TYPE);
  }

  // -- 422 UNPROCESSABLE_ENTITY --

  @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
  @ExceptionHandler(UnprocessableContentException.class)
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "422",
            description = "Unprocessable Content",
            content = @Content(schema = @Schema(implementation = ResponseEntity.class)))
      })
  ResponseEntity<ErrorMessage> handleUnprocessableException(UnprocessableContentException ex) {
    String errorMessage =
        ex.getMessage() != null && !ex.getMessage().isEmpty()
            ? ex.getMessage()
            : HttpStatus.UNPROCESSABLE_ENTITY.getReasonPhrase();
    return new ResponseEntity<>(new ErrorMessage(errorMessage), HttpStatus.UNPROCESSABLE_ENTITY);
  }

  @ResponseStatus(HttpStatus.UNAUTHORIZED)
  @ExceptionHandler(AuthenticationError.class)
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized",
            content = @Content(schema = @Schema(implementation = ResponseEntity.class)))
      })
  ResponseEntity<ErrorMessage> handleAuthenticationError(AuthenticationError ex) {
    String errorMessage =
        ex.getMessage() != null && !ex.getMessage().isEmpty()
            ? ex.getMessage()
            : HttpStatus.UNAUTHORIZED.getReasonPhrase();
    return new ResponseEntity<>(new ErrorMessage(errorMessage), HttpStatus.UNAUTHORIZED);
  }

  // --- Open channel access
  public User impersonateUser(UserRepository userRepository, Optional<String> userId)
      throws AuthenticationError {
    if (ANONYMOUS.equals(currentUser().getId())) {
      if (userId.isEmpty()) {
        throw new AuthenticationError("User must be logged or dynamic player is required");
      }
      return userRepository
          .findById(userId.get())
          .orElseThrow(() -> new ElementNotFoundException("User not found"));
    }
    return userRepository
        .findById(currentUser().getId())
        .orElseThrow(() -> new ElementNotFoundException("Current user not found"));
  }

  protected void validateUUID(final String id) throws InputValidationException {
    try {
      UUID.fromString(id);
    } catch (IllegalArgumentException e) {
      throw new InputValidationException("id", "The ID is not a valid UUID: " + id);
    }
  }

  // -- UTILS --

  /** Current request method, or {@code ?} when called outside a servlet request. */
  private static String requestMethod() {
    return RequestContextHolder.getRequestAttributes()
            instanceof ServletRequestAttributes attributes
        ? attributes.getRequest().getMethod()
        : "?";
  }

  /** Current request URI, or {@code ?} when called outside a servlet request. */
  private static String requestUri() {
    return RequestContextHolder.getRequestAttributes()
            instanceof ServletRequestAttributes attributes
        ? attributes.getRequest().getRequestURI()
        : "?";
  }
}
