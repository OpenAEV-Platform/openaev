package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Setter
@Entity
@Table(name = "indexing_status")
public class IndexingStatus {

  @Id
  @Getter
  @Column(name = "indexing_status_type")
  @JsonProperty("indexing_status_type")
  @NotBlank
  private String type;

  @Getter
  @Column(name = "indexing_status_indexing_date")
  @JsonProperty("indexing_status_indexing_date")
  private Instant lastIndexing;
}
