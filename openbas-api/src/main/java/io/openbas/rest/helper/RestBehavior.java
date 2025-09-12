package io.openbas.rest.helper;

import static io.openbas.config.OpenBASAnonymous.ANONYMOUS;
import static io.openbas.config.SessionHelper.currentUser;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import io.openbas.aop.lock.LockAcquisitionException;
import io.openbas.config.OpenBASPrincipal;
import io.openbas.database.model.Organization;
import io.openbas.database.model.User;
import io.openbas.database.repository.UserRepository;
import io.openbas.rest.exception.*;
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
        new ValidationErrorBag(HttpStatus.UNAUTHORIZED.value(), "AUTHENTIFICATION_FAILED");
    ValidationError errors = new ValidationError();
    Map<String, ValidationContent> errorsBag = new HashMap<>();
    errorsBag.put("username", new ValidationContent("Invalid user or password"));
    errors.setChildren(errorsBag);
    bag.setErrors(errors);
    return bag;
  }

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
    log.warn(String.format("ElementNotFoundException: %s", ex.getMessage()), ex);
    return new ResponseEntity<>(message, HttpStatus.NOT_FOUND);
  }

  @ExceptionHandler(UnsupportedMediaTypeException.class)
  public ResponseEntity<ErrorMessage> handleUnsupportedMediaTypeException(
      UnsupportedMediaTypeException ex) {
    ErrorMessage message = new ErrorMessage(ex.getMessage());
    log.warn(String.format("UnsupportedMediaTypeException: " + ex.getMessage()), ex);
    return new ResponseEntity<>(message, HttpStatus.UNSUPPORTED_MEDIA_TYPE);
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

  // --- Open channel access
  public User impersonateUser(UserRepository userRepository, Optional<String> userId) {
    if (ANONYMOUS.equals(currentUser().getId())) {
      if (userId.isEmpty()) {
        throw new UnsupportedOperationException(
            "User must be logged or dynamic player is required");
      }
      return userRepository
          .findById(userId.get())
          .orElseThrow(() -> new ElementNotFoundException("User not found"));
    }
    return userRepository
        .findById(currentUser().getId())
        .orElseThrow(() -> new ElementNotFoundException("Current user not found"));
  }

  public void checkUserAccess(UserRepository userRepository, String userId) {
    User askedUser = userRepository.findById(userId).orElseThrow();
    if (askedUser.getOrganization() != null) {
      OpenBASPrincipal currentUser = currentUser();
      if (!currentUser.isAdmin()) {
        User local =
            userRepository
                .findById(currentUser.getId())
                .orElseThrow(() -> new ElementNotFoundException("Current user not found"));
        List<String> localOrganizationIds =
            local.getGroups().stream()
                .flatMap(group -> group.getOrganizations().stream())
                .map(Organization::getId)
                .toList();
        if (!localOrganizationIds.contains(askedUser.getOrganization().getId())) {
          throw new UnsupportedOperationException("User is restricted");
        }
      }
    }
  }

  public void checkOrganizationAccess(UserRepository userRepository, String organizationId) {
    if (organizationId != null) {
      OpenBASPrincipal currentUser = currentUser();
      if (!currentUser.isAdmin()) {
        User local =
            userRepository
                .findById(currentUser.getId())
                .orElseThrow(() -> new ElementNotFoundException("Current user not found"));
        List<String> localOrganizationIds =
            local.getGroups().stream()
                .flatMap(group -> group.getOrganizations().stream())
                .map(Organization::getId)
                .toList();
        if (!localOrganizationIds.contains(organizationId)) {
          throw new UnsupportedOperationException("User is restricted");
        }
      }
    }
  }

  protected void validateUUID(final String id) throws InputValidationException {
    try {
      UUID.fromString(id);
    } catch (IllegalArgumentException e) {
      throw new InputValidationException("id", "The ID is not a valid UUID: " + id);
    }
  }
}
