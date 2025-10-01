package io.openaev.database.model;

import static io.openaev.database.model.Comcheck.COMCHECK_STATUS.EXPIRED;
import static java.util.Optional.ofNullable;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.openaev.database.audit.ModelBaseListener;
import io.openaev.helper.MonoIdDeserializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "comchecks_statuses")
@EntityListeners(ModelBaseListener.class)
public class ComcheckStatus implements Base {

  public enum CHECK_STATUS {
    RUNNING,
    SUCCESS,
    FAILURE
  }

  @Id
  @Column(name = "status_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @JsonProperty("comcheckstatus_id")
  private String id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "status_user")
  @JsonSerialize(using = MonoIdDeserializer.class)
  @JsonProperty("comcheckstatus_user")
  @Schema(type = "string")
  private User user;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "status_comcheck")
  @JsonSerialize(using = MonoIdDeserializer.class)
  @JsonProperty("comcheckstatus_comcheck")
  @Schema(type = "string")
  private Comcheck comcheck;

  @Column(name = "status_sent_date")
  @JsonProperty("comcheckstatus_sent_date")
  private Instant lastSent;

  @Column(name = "status_receive_date")
  @JsonProperty("comcheckstatus_receive_date")
  private Instant receiveDate;

  @Column(name = "status_sent_retry")
  @JsonProperty("comcheckstatus_sent_retry")
  private int sentNumber = 0;

  public ComcheckStatus() {
    // Default constructor
  }

  public ComcheckStatus(User user) {
    this.user = user;
  }

  // region transient
  @JsonProperty("comcheckstatus_state")
  public CHECK_STATUS getState() {
    return getReceiveDate()
        .map(receive -> CHECK_STATUS.SUCCESS)
        .orElseGet(
            () ->
                EXPIRED.equals(getComcheck().getState())
                    ? CHECK_STATUS.FAILURE
                    : CHECK_STATUS.RUNNING);
  }

  // endregion

  @Override
  public String getId() {
    return id;
  }

  @Override
  public boolean isUserHasAccess(User user) {
    return comcheck.isUserHasAccess(user);
  }

  public void setId(String id) {
    this.id = id;
  }

  public User getUser() {
    return user;
  }

  public void setUser(User user) {
    this.user = user;
  }

  public Comcheck getComcheck() {
    return comcheck;
  }

  public void setComcheck(Comcheck comcheck) {
    this.comcheck = comcheck;
  }

  public Optional<Instant> getLastSent() {
    return ofNullable(lastSent);
  }

  public void setLastSent(Instant lastSent) {
    this.lastSent = lastSent;
  }

  public int getSentNumber() {
    return sentNumber;
  }

  public void setSentNumber(int sentNumber) {
    this.sentNumber = sentNumber;
  }

  public Optional<Instant> getReceiveDate() {
    return ofNullable(receiveDate);
  }

  public void setReceiveDate(Instant receiveDate) {
    this.receiveDate = receiveDate;
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
