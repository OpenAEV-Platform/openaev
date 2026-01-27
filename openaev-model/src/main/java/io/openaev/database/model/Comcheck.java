package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.openaev.database.audit.ModelBaseListener;
import io.openaev.helper.MonoIdSerializer;
import io.openaev.helper.MultiIdListSerializer;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Setter
@Getter
@Entity
@Table(name = "comchecks")
@EntityListeners(ModelBaseListener.class)
public class Comcheck implements Base {

  public enum COMCHECK_STATUS {
    RUNNING,
    EXPIRED,
    FINISHED
  }

  @Id
  @Column(name = "comcheck_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @JsonProperty("comcheck_id")
  @NotBlank
  private String id;

  @Column(name = "comcheck_name")
  @JsonProperty("comcheck_name")
  private String name;

  @Column(name = "comcheck_start_date")
  @JsonProperty("comcheck_start_date")
  @NotNull
  private Instant start;

  @Column(name = "comcheck_end_date")
  @JsonProperty("comcheck_end_date")
  @NotNull
  private Instant end;

  @Column(name = "comcheck_state")
  @JsonProperty("comcheck_state")
  @Enumerated(EnumType.STRING)
  private COMCHECK_STATUS state = COMCHECK_STATUS.RUNNING;

  @Column(name = "comcheck_subject")
  @JsonProperty("comcheck_subject")
  private String subject;

  @Column(name = "comcheck_message")
  @JsonProperty("comcheck_message")
  private String message;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "comcheck_exercise")
  @JsonSerialize(using = MonoIdSerializer.class)
  @JsonProperty("comcheck_exercise")
  @Schema(type = "string")
  private Exercise exercise;

  // CascadeType.ALL is required here because comcheck statuses are embedded
  @ArraySchema(schema = @Schema(type = "string"))
  @OneToMany(mappedBy = "comcheck", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
  @JsonSerialize(using = MultiIdListSerializer.class)
  @JsonProperty("comcheck_statuses")
  private List<ComcheckStatus> comcheckStatus = new ArrayList<>();

  // region transient
  @JsonProperty("comcheck_users_number")
  public long getUsersNumber() {
    return getComcheckStatus().size(); // One status for each user.
  }

  // endregion

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
