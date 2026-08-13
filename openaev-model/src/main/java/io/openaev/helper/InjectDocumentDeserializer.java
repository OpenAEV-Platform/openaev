package io.openaev.helper;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.database.model.Document;
import io.openaev.database.model.InjectDocument;
import jakarta.persistence.EntityManager;
import java.io.IOException;

/**
 * Deserializes one element of an {@code inject_documents} array back into an {@link InjectDocument}
 * link.
 *
 * <p>{@link MonoIdDeserializerHelper} cannot be used here: {@link InjectDocument} has a composite
 * primary key ({@code InjectDocumentId}), so it can never be resolved through {@code
 * EntityManager#getReference} with a single raw id - and the serialized form is not a scalar id in
 * the first place. {@link MultiModelSerializer} writes the full link object (as produced when an
 * inject is serialized into a chaining step's data), so the scalar-only helper returned {@code
 * null} without consuming the object's tokens and then misread the following field name as an id,
 * failing the whole step execution.
 *
 * <p>Two element shapes are accepted:
 *
 * <ul>
 *   <li>an object carrying {@code document_id} - the entity serialization ({@code inject_id},
 *       {@code document_id}, {@code document_attached}, {@code document_name}) or the action
 *       drawer's {@code {document_id, document_attached}} input shape; {@code document_attached}
 *       defaults to {@code true};
 *   <li>a plain string, treated as the document id.
 * </ul>
 *
 * <p>The {@link Document} side is resolved against the injected {@link EntityManager} through a
 * JPQL query, so Hibernate's {@code tenantFilter} applies when enabled; a document that no longer
 * exists - or belongs to another tenant - yields {@code null} so the attachment degrades to "no
 * attachment" instead of failing the step. Without an EntityManager a stub carrying only the id is
 * returned. The owning {@link io.openaev.database.model.Inject} cannot be known
 * mid-deserialization, so the {@code inject} side is left null - consumers must re-parent the link
 * (and prune {@code null} elements) before persisting.
 */
public class InjectDocumentDeserializer extends JsonDeserializer<InjectDocument> {

  @Override
  public InjectDocument deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
    String documentId = null;
    boolean attached = true;

    if (p.currentToken() == JsonToken.START_OBJECT) {
      JsonNode node = p.readValueAsTree();
      JsonNode idNode = node.get("document_id");
      if (idNode != null && idNode.isTextual()) {
        documentId = idNode.asText();
      }
      attached = node.path("document_attached").asBoolean(true);
    } else if (p.currentToken() == JsonToken.VALUE_STRING) {
      documentId = p.getValueAsString();
    } else {
      p.skipChildren();
      return null;
    }

    if (documentId == null || documentId.isBlank()) {
      return null;
    }

    EntityManager em =
        (EntityManager) ctxt.findInjectableValue(EntityManager.class.getName(), null, null);

    Document document;
    if (em != null) {
      // A JPQL query - unlike EntityManager#find by primary key - goes through Hibernate's
      // enabled filters, so Document's tenantFilter applies and a stale or crafted step
      // referencing another tenant's document id degrades like a deleted document.
      document =
          em.createQuery("select d from Document d where d.id = :id", Document.class)
              .setParameter("id", documentId)
              .getResultStream()
              .findFirst()
              .orElse(null);
      if (document == null) {
        return null;
      }
    } else {
      document = new Document();
      document.setId(documentId);
    }

    InjectDocument injectDocument = new InjectDocument();
    injectDocument.setDocument(document);
    injectDocument.setAttached(attached);
    injectDocument.getCompositeId().setDocumentId(documentId);
    return injectDocument;
  }
}
