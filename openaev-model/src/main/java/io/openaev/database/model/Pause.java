package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.openaev.helper.MonoIdDeserializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "pauses")
public class Pause implements Base {
  @Id
  @Column(name = "pause_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @JsonProperty("log_id")
  private String id;

  @Column(name = "pause_date")
  @JsonProperty("pause_date")
  private Instant date;

  @Column(name = "pause_duration")
  @JsonProperty("pause_duration")
  private Long duration = 0L;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "pause_exercise")
  @JsonSerialize(using = MonoIdDeserializer.class)
  @JsonProperty("pause_exercise")
  @Schema(type = "string")
  private Exercise exercise;

  @Override
  public String getId() {
    return id;
  }

  @Override
  public boolean isUserHasAccess(User user) {
    return exercise.isUserHasAccess(user);
  }

  public void setId(String id) {
    this.id = id;
  }

  public Instant getDate() {
    return date;
  }

  public void setDate(Instant date) {
    this.date = date;
  }

  public Optional<Long> getDuration() {
    return Optional.ofNullable(duration);
  }

  public void setDuration(Long duration) {
    this.duration = duration;
  }

  public Exercise getExercise() {
    return exercise;
  }

  public void setExercise(Exercise exercise) {
    this.exercise = exercise;
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
