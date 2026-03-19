package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.openaev.helper.MonoIdSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
@Table(name = "injects_documents")
public class InjectDocument {

  @EmbeddedId @JsonIgnore private InjectDocumentId compositeId = new InjectDocumentId();

  @ManyToOne(fetch = FetchType.EAGER)
  @MapsId("injectId")
  @JoinColumn(name = "inject_id")
  @JsonProperty("inject_id")
  @JsonSerialize(using = MonoIdSerializer.class)
  @Schema(type = "string")
  private Inject inject;

  @ManyToOne(fetch = FetchType.EAGER)
  @MapsId("documentId")
  @JoinColumn(name = "document_id")
  @JsonProperty("document_id")
  @JsonSerialize(using = MonoIdSerializer.class)
  @Schema(type = "string")
  private Document document;

  @Column(name = "document_attached")
  @JsonProperty("document_attached")
  private boolean attached = true;

  @JsonProperty("document_name")
  public String getDocumentName() {
    return this.document.getName();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    InjectDocument that = (InjectDocument) o;
    return compositeId.equals(that.compositeId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(compositeId);
  }
}
