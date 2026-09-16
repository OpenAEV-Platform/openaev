package io.openaev.rest.session.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.config.SessionManager;
import io.openaev.database.raw.RawUserIdentity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SessionOutput {

  @JsonProperty("session_id")
  @Schema(description = "Identifier of the session")
  private String sessionId;

  @JsonProperty("session_user_id")
  @Schema(description = "Identifier of the user owning the session")
  private String userId;

  @JsonProperty("session_user_name")
  @Schema(description = "Display name of the user owning the session, or their email")
  private String userName;

  @JsonProperty("session_created_at")
  @Schema(description = "Session creation time")
  private Instant createdAt;

  @JsonProperty("session_last_access_at")
  @Schema(description = "Last time the session was used")
  private Instant lastAccessAt;

  @JsonProperty("session_expires_at")
  @Schema(description = "Time at which the session expires if it stays idle")
  private Instant expiresAt;

  public static SessionOutput from(SessionManager.SessionInfo info, RawUserIdentity owner) {
    return SessionOutput.builder()
        .sessionId(info.sessionId())
        .userId(info.userId())
        .userName(owner == null ? null : owner.getUser_name())
        .createdAt(info.createdAt())
        .lastAccessAt(info.lastAccessAt())
        .expiresAt(info.expiresAt())
        .build();
  }
}
