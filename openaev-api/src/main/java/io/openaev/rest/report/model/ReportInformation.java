package io.openaev.rest.report.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.openaev.database.model.Base;
import io.openaev.helper.MonoIdDeserializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.util.Objects;
import java.util.UUID;
import lombok.Data;
import org.hibernate.annotations.UuidGenerator;

@Data
@Entity
@Table(
    name = "report_informations",
    uniqueConstraints = {
      @UniqueConstraint(columnNames = {"report_id", "report_informations_type"})
    })
public class ReportInformation implements Base {
  @Id
  @Column(name = "report_informations_id")
  @GeneratedValue
  @UuidGenerator
  @NotNull
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "report_id")
  @JsonSerialize(using = MonoIdDeserializer.class)
  @NotNull
  @Schema(type = "string")
  private Report report;

  @Enumerated(EnumType.STRING)
  @JsonProperty("report_informations_type")
  @Column(name = "report_informations_type", nullable = false)
  @NotNull
  private ReportInformationsType reportInformationsType;

  @Column(name = "report_informations_display")
  @JsonProperty("report_informations_display")
  private Boolean reportInformationsDisplay = false;

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  public String getId() {
    return this.id != null ? this.id.toString() : "";
  }

  public void setId(String id) {
    this.id = UUID.fromString(id);
  }
}
