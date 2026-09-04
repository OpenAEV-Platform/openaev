package io.openaev.rest.log;

import static io.openaev.utils.log.LogUtils.*;
import static java.util.logging.Level.*;

import io.openaev.aop.AccessControl;
import io.openaev.context.TxCtx;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.rest.log.form.LogDetailsInput;
import io.openaev.utils.log.LogUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.util.logging.Level;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
public class LogApi extends RestBehavior {

  @PostMapping("/api/logs")
  @Transactional
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
      TxCtx ctx,
      @Parameter(
              description = "Details of the log message, including level, message, and stacktrace.",
              required = true)
          @Valid
          @RequestBody
          LogDetailsInput logDetailsInput) {
    String level = logDetailsInput.getLevel();
    String message = buildLogMessage(logDetailsInput, level);
    Level resolvedLevel = LogUtils.getLogLevel(level);

    if (resolvedLevel == WARNING || resolvedLevel == INFO || resolvedLevel == SEVERE) {
      LogUtils.log(log, message, resolvedLevel);
      return new ResponseEntity<>("Log message processed successfully", HttpStatus.OK);
    }

    message = "Invalid level: " + level;
    LogUtils.log(log, message, SEVERE);
    return new ResponseEntity<>(message, HttpStatus.BAD_REQUEST);
  }
}
