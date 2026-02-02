package io.openaev.database.model;

import static java.time.Instant.now;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.openaev.database.audit.ModelBaseListener;
import io.openaev.helper.MonoIdSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Setter
@Entity
@Table(name = "challenges_flags")
@EntityListeners(ModelBaseListener.class)
public class ChallengeFlag implements Base {

  public enum FLAG_TYPE {
    VALUE,
    VALUE_CASE,
    REGEXP,
  }

  @Id
  @Column(name = "flag_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @JsonProperty("flag_id")
  private String id;

  @Getter
  @Column(name = "flag_created_at")
  @JsonProperty("flag_created_at")
  private Instant createdAt = now();

  @Getter
  @Column(name = "flag_updated_at")
  @JsonProperty("flag_updated_at")
  private Instant updatedAt = now();

  @Getter
  @Column(name = "flag_type")
  @JsonProperty("flag_type")
  @Enumerated(EnumType.STRING)
  private FLAG_TYPE type;

  @Getter
  @Column(name = "flag_value")
  @JsonProperty("flag_value")
  private String value;

  @Getter
  @ManyToOne(fetch = FetchType.LAZY)
  @JsonSerialize(using = MonoIdSerializer.class)
  @JoinColumn(name = "flag_challenge")
  @JsonProperty("flag_challenge")
  @Schema(type = "string")
  private Challenge challenge;

  @Override
  public String getId() {
    return id;
  }

  @Override
  public boolean isUserHasAccess(User user) {
    if (challenge == null) {
      return user.isAdmin();
    }
    return challenge.isUserHasAccess(user);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || !Base.class.isAssignableFrom(o.getClass())) return false;
    Base base = (Base) o;
    return id.equals(base.getId());
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }
}
