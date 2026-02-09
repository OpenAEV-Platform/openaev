package io.openaev.database.model;

import static java.time.Instant.now;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import io.openaev.database.audit.ModelBaseListener;
import io.openaev.helper.MonoIdSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UuidGenerator;

@Data
@Entity
@Table(name = "user_events")
@EntityListeners(ModelBaseListener.class)
public class UserEvent implements Base {

  @Id
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @Column(name = "user_event_id", nullable = false)
  @NotBlank
  @JsonProperty("user_event_id")
  @Schema(description = "User event ID")
  private String id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id")
  @JsonProperty("user_id")
  @JsonSerialize(using = MonoIdSerializer.class)
  private User user;

  @Enumerated(EnumType.STRING)
  @Column(name = "user_event_type", nullable = false)
  @NotNull
  private UserEventType type;

  @Column(name = "user_event_payload", columnDefinition = "jsonb")
  @Type(JsonType.class)
  private JsonNode payload;

  // -- AUDIT --

  @CreationTimestamp
  @Column(name = "user_event_created_at")
  @NotNull
  @JsonProperty("user_event_created_at")
  private Instant createdAt = now();
}
