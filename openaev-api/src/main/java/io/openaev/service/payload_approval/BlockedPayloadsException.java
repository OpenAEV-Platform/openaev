package io.openaev.service.payload_approval;

import io.openaev.rest.exception.BadRequestException;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Getter;

/** Refusal to use or launch injects whose payload is not approved; answers 400 with the list. */
@Getter
public class BlockedPayloadsException extends BadRequestException {

  /** One payload that blocks the operation, with the reason. */
  public record BlockedPayload(String payloadId, String payloadName, String reason) {

    String describe() {
      return "\"" + payloadName + "\" (" + reason + ")";
    }
  }

  private final transient List<BlockedPayload> blockedPayloads;

  public BlockedPayloadsException(String operation, List<BlockedPayload> blockedPayloads) {
    super(message(operation, blockedPayloads));
    this.blockedPayloads = List.copyOf(blockedPayloads);
  }

  /** The user-facing explanation, also used where the refusal is not an HTTP error. */
  public static String message(String operation, List<BlockedPayload> blockedPayloads) {
    return operation
        + " is blocked: these payloads are not approved: "
        + blockedPayloads.stream().map(BlockedPayload::describe).collect(Collectors.joining(", "))
        + ". A user with \"Approve content\" must approve them first.";
  }
}
