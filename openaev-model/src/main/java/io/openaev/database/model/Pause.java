package io.openaev.database.model;

import static lombok.AccessLevel.NONE;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.openaev.helper.MonoIdSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Getter
@Setter
@Entity
@Table(name = "pauses")
public class Pause implements Base {

  @Id
  @Column(name = "pause_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @JsonProperty("pause_id")
  private String id;

  @Column(name = "pause_date")
  @JsonProperty("pause_date")
  private Instant date;

  @Getter(NONE)
  @Column(name = "pause_duration")
  @JsonProperty("pause_duration")
  private Long duration = 0L;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "pause_exercise")
  @JsonSerialize(using = MonoIdSerializer.class)
  @JsonProperty("pause_exercise")
  @Schema(type = "string")
  private Exercise exercise;

  public Optional<Long> getDuration() {
    return Optional.ofNullable(duration);
  }

  @Override
  public boolean isUserHasAccess(User user) {
    if (exercise == null) {
      return user.isAdmin();
    }
    return exercise.isUserHasAccess(user);
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
