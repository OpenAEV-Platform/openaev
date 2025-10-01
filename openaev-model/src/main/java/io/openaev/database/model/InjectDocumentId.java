package io.openaev.database.model;

import jakarta.persistence.Embeddable;
import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class InjectDocumentId implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  private String injectId;
  private String documentId;

  public InjectDocumentId() {
    // Default constructor
  }

  public String getInjectId() {
    return injectId;
  }

  public void setInjectId(String injectId) {
    this.injectId = injectId;
  }

  public String getDocumentId() {
    return documentId;
  }

  public void setDocumentId(String documentId) {
    this.documentId = documentId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    InjectDocumentId that = (InjectDocumentId) o;
    return injectId.equals(that.injectId) && documentId.equals(that.documentId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(injectId, documentId);
  }
}
