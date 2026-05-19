package io.openaev.rest.log;

import static io.openaev.utils.log.LogUtils.*;
import static java.util.logging.Level.*;

import io.openaev.aop.AccessControl;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.rest.log.form.LogDetailsInput;
import io.openaev.service.LogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LogApi extends RestBehavior {

  // private static final Logger logger = LoggerFactory.getLogger(LogApi.class);

  private final LogService logService;

  public LogApi(LogService logService) {
    this.logService = logService;
  }

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
  @AccessControl(skipRBAC = true)
  public ResponseEntity<String> logDetails(
      @Parameter(
              description = "Details of the log message, including level, message, and stacktrace.",
              required = true)
          @Valid
          @RequestBody
          LogDetailsInput logDetailsInput) {
    String level = logDetailsInput.getLevel();
    String message = buildLogMessage(logDetailsInput, level);

    logService.logMessage(message, level, LogService.AuditLogType.GENERIC, null);

    /*if (WARNING.getName().equals(level)) {
      logger.warn(buildLogMessage(logDetailsInput, level));
    } else if (INFO.getName().equals(level)) {
      logger.info(buildLogMessage(logDetailsInput, level));
    } else if (SEVERE.getName().equals(level)) {
      logger.error(buildLogMessage(logDetailsInput, level));
    } else {
      String invalidLevel = "Invalid level: " + level;
      logger.error(invalidLevel);
      return new ResponseEntity<>(invalidLevel, HttpStatus.BAD_REQUEST);
    }*/

    if (!WARNING.getName().equals(level)
        && !INFO.getName().equals(level)
        && !SEVERE.getName().equals(level)) {
      String invalidLevel = "Invalid level: " + level;
      return new ResponseEntity<>(invalidLevel, HttpStatus.BAD_REQUEST);
    }

    return new ResponseEntity<>("Log message processed successfully", HttpStatus.OK);
  }
}
