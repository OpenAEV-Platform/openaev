package io.openaev.api.payload;

import io.openaev.aop.RBAC;
import io.openaev.database.model.Action;
import io.openaev.database.model.Payload;
import io.openaev.database.model.ResourceType;
import io.openaev.database.repository.PayloadRepository;
import io.openaev.jsonapi.ZipJsonApi;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.rest.payload.PayloadApi;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(PayloadApi.PAYLOAD_URI)
@RequiredArgsConstructor
public class PayloadApiExporter extends RestBehavior {

  private final PayloadRepository payloadRepository;
  private final ZipJsonApi<Payload> zipJsonApi;

  @Operation(
      description = "Exports a payload in JSON:API format, optionally including related entities.")
  @GetMapping(value = "/{payloadId}/export", produces = "application/zip")
  @Transactional(readOnly = true)
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.PAYLOAD)
  public ResponseEntity<byte[]> export(@PathVariable @NotBlank final String payloadId)
      throws IOException {
    Payload payload =
        payloadRepository.findById(payloadId).orElseThrow(ElementNotFoundException::new);
    return zipJsonApi.handleExport(payload);
  }
}
