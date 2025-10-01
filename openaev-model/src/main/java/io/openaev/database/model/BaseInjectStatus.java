package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.annotation.Queryable;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Setter
@Getter
@MappedSuperclass
public abstract class BaseInjectStatus implements Base {

  @Id
  @Column(name = "status_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @JsonProperty("status_id")
  private String id;

  @Column(name = "status_name")
  @JsonProperty("status_name")
  @Enumerated(EnumType.STRING)
  @NotNull
  private ExecutionStatus name;

  @Column(name = "tracking_sent_date")
  @JsonProperty("tracking_sent_date")
  private Instant trackingSentDate; // To Queue / processing engine

  @Column(name = "tracking_end_date")
  @JsonProperty("tracking_end_date")
  private Instant trackingEndDate; // Done task from injector

  @Queryable(searchable = true, path = "inject.title")
  @OneToOne
  @JoinColumn(name = "status_inject")
  @JsonIgnore
  protected Inject inject;

  @Override
  public boolean isUserHasAccess(User user) {
    return this.inject.isUserHasAccess(user);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || !Base.class.isAssignableFrom(o.getClass())) {
      return false;
    }
    Base base = (Base) o;
    return id.equals(base.getId());
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }
}
