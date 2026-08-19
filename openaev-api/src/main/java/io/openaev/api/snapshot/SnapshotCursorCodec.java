package io.openaev.api.snapshot;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.rest.exception.BadRequestException;
import java.time.Instant;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Encodes and decodes the opaque, URL-safe base64 cursor exchanged with bulk snapshot export
 * clients. Every decode failure throws {@link BadRequestException} (→ 400) with a generic message:
 * the decoded payload is never echoed back, a machine client does not need the detail and a human
 * debugging one has the logs.
 */
@Component
@RequiredArgsConstructor
public class SnapshotCursorCodec {

  private static final int CURRENT_VERSION = 1;

  private final ObjectMapper objectMapper;

  /** The decoded cursor payload. {@code v} is the wire format version. */
  public record SnapshotCursor(int v, String tenant, Instant ts, String id) {}

  public String encode(SnapshotCursor cursor) {
    try {
      byte[] json = objectMapper.writeValueAsBytes(cursor);
      return Base64.getUrlEncoder().withoutPadding().encodeToString(json);
    } catch (Exception e) {
      throw new IllegalStateException("Unable to encode snapshot cursor", e);
    }
  }

  /**
   * Decodes a cursor and validates it belongs to {@code tenantId}.
   *
   * @param cursor the opaque cursor string received from the client
   * @param tenantId the tenant id of the current request path, compared against the cursor
   * @throws BadRequestException on any malformed, unsupported-version or foreign-tenant cursor
   */
  public SnapshotCursor decode(String cursor, String tenantId) {
    byte[] decoded;
    try {
      decoded = Base64.getUrlDecoder().decode(cursor);
    } catch (IllegalArgumentException e) {
      throw new BadRequestException("Malformed cursor");
    }

    SnapshotCursor parsed;
    try {
      parsed = objectMapper.readValue(decoded, SnapshotCursor.class);
    } catch (Exception e) {
      throw new BadRequestException("Malformed cursor");
    }

    if (parsed.v() != CURRENT_VERSION) {
      throw new BadRequestException("Unsupported cursor version");
    }
    if (parsed.tenant() == null || parsed.tenant().isBlank() || !parsed.tenant().equals(tenantId)) {
      throw new BadRequestException("Cursor does not belong to this tenant");
    }
    if (parsed.ts() == null || parsed.id() == null || parsed.id().isBlank()) {
      throw new BadRequestException("Malformed cursor");
    }
    return parsed;
  }
}
