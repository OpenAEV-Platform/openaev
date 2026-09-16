package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.openaev.helper.MonoIdDeserializerHelper;
import io.openaev.helper.MonoIdSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Setter
@Getter
@Entity
@Table(
    name = "inject_authorisations",
    uniqueConstraints =
        @UniqueConstraint(name = "uk_inject_authorisations_inject_id", columnNames = "inject_id"))
public class InjectAuthorisation {

  @Id
  @Column(name = "inject_authorisation_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @JsonProperty("inject_authorisation_id")
  private String id;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "inject_id", nullable = false, unique = true)
  @JsonSerialize(using = MonoIdSerializer.class)
  @JsonDeserialize(using = MonoIdDeserializerHelper.class)
  @JsonProperty("inject_id")
  @Schema(implementation = String.class)
  private Inject inject;

  @Column(name = "inject_authorisation_code", nullable = false)
  @JsonIgnore
  private String code;

  @Column(name = "inject_authorisation_issued_at")
  @JsonProperty("inject_authorisation_issued_at")
  private Instant issuedAt;
}
