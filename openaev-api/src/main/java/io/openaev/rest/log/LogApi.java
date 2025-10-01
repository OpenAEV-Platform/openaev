package io.openaev.rest.log;

import static io.openaev.utils.LogUtils.*;
import static java.util.logging.Level.*;

import io.openaev.aop.RBAC;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.rest.log.form.LogDetailsInput;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LogApi extends RestBehavior {

  private static final Logger logger = LoggerFactory.getLogger(LogApi.class);

  @PostMapping("/api/logs")
  @Operation(
      hidden = true,
      summary = "Log message details",
      description =
          "This endpoint allows you to log messages with different severity levels (INFO, WARN, SEVERE).",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "Log message processed successfully",
            content = @Content(mediaType = "application/json")),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid level",
            content = @Content(mediaType = "application/json"))
      })
  @RBAC(skipRBAC = true)
  public ResponseEntity<String> logDetails(
      @Parameter(
              description = "Details of the log message, including level, message, and stacktrace.",
              required = true)
          @Valid
          @RequestBody
          LogDetailsInput logDetailsInput) {
    String level = logDetailsInput.getLevel();

    if (WARNING.getName().equals(level)) {
      logger.warn(buildLogMessage(logDetailsInput, level));
    } else if (INFO.getName().equals(level)) {
      logger.info(buildLogMessage(logDetailsInput, level));
    } else if (SEVERE.getName().equals(level)) {
      logger.error(buildLogMessage(logDetailsInput, level));
    } else {
      String invalidLevel = "Invalid level: " + level;
      logger.error(invalidLevel);
      return new ResponseEntity<>(invalidLevel, HttpStatus.BAD_REQUEST);
    }

    return new ResponseEntity<>("Log message processed successfully", HttpStatus.OK);
  }
}
