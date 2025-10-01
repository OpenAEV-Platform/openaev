package io.openaev.rest.inject.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.Document;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectDocument;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class InjectDocumentInput {

  @JsonProperty("document_id")
  private String documentId;

  @JsonProperty("document_attached")
  private boolean attached = true;

  public InjectDocument toDocument(@NotNull final Document document, @NotNull final Inject inject) {
    InjectDocument injectDocument = new InjectDocument();
    injectDocument.setInject(inject);
    injectDocument.setDocument(document);
    injectDocument.setAttached(this.isAttached());
    return injectDocument;
  }
}
