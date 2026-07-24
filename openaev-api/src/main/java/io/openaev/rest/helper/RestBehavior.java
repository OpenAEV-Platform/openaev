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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.function.UnsupportedMediaTypeException;

@RestControllerAdvice
@Slf4j
public class RestBehavior {

  @Resource protected ObjectMapper mapper;

  // Build the mapping between json specific name and the actual database field name
  private Map<String, String> buildJsonMappingFields(MethodArgumentNotValidException ex) {
    Class<?> inputClass = Objects.requireNonNull(ex.getBindingResult().getTarget()).getClass();
    JavaType javaType = mapper.getTypeFactory().constructType(inputClass);
    BeanDescription beanDescription = mapper.getSerializationConfig().introspect(javaType);
    return beanDescription.findProperties().stream()
        .collect(
            Collectors.toMap(
                BeanPropertyDefinition::getInternalName, BeanPropertyDefinition::getName));
  }

  // -- 400 BAD_REQUEST --

  private static final int MAX_REJECTED_VALUE_LENGTH = 100;

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
    String detail;
    if (ex.getCause() instanceof InvalidFormatException ife) {
      String path =
          ife.getPath().stream()
              .map(
                  ref ->
                      ref.getFieldName() != null ? ref.getFieldName() : "[" + ref.getIndex() + "]")
              .collect(Collectors.joining("."));
      detail =
          "Invalid value '%s' for field '%s'"
              .formatted(abbreviateRejectedValue(ife.getValue()), path);
    } else {
      detail = "Malformed or unreadable request body";
    }
    log.warn("HttpMessageNotReadableException: {}", detail);
    return new ResponseEntity<>(new ErrorMessage(detail), HttpStatus.BAD_REQUEST);
  }

  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ValidationErrorBag handleValidationExceptions(MethodArgumentNotValidException ex) {
    Map<String, String> jsonFieldsMapping = buildJsonMappingFields(ex);
    ValidationErrorBag bag = new ValidationErrorBag();
    ValidationError errors = new ValidationError();
    Map<String, ValidationContent> errorsBag = new HashMap<>();
    ex.getBindingResult()
        .getAllErrors()
        .forEach(
            (error) -> {
              String fieldName = ((FieldError) error).getField();
              String errorMessage = error.getDefaultMessage();
              errorsBag.put(jsonFieldsMapping.get(fieldName), new ValidationContent(errorMessage));
            });
    errors.setChildren(errorsBag);
    bag.setErrors(errors);
    return bag;
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
}
