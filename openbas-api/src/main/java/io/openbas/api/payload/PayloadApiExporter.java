package io.openbas.api.payload;

import io.openbas.aop.RBAC;
import io.openbas.database.model.Action;
import io.openbas.database.model.Payload;
import io.openbas.database.model.ResourceType;
import io.openbas.database.repository.PayloadRepository;
import io.openbas.jsonapi.ZipJsonApi;
import io.openbas.rest.exception.ElementNotFoundException;
import io.openbas.rest.helper.RestBehavior;
import io.openbas.rest.payload.PayloadApi;
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
