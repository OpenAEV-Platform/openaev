package io.openaev.utils.fixtures;

import io.openaev.database.model.Document;
import io.openaev.utils.fixtures.files.BaseFile;
import java.util.UUID;

public class DocumentFixture {

  public static final String DOCUMENT_NAME = "A document";

  public static Document getDocumentJpeg() {
    Document document = createDocumentWithName(DOCUMENT_NAME);
    document.setType("image/jpeg");
    return document;
  }

  public static Document getDocument(BaseFile<?> file) {
    Document document = createDocumentWithDefaultName();
    document.setType(file.getMimeType());
    document.setTarget(file.getFileName());
    return document;
  }

  private static Document createDocumentWithDefaultName() {
    return createDocumentWithName(null);
  }

  private static Document createDocumentWithName(String name) {
    String new_name = name == null ? "document-%s".formatted(UUID.randomUUID()) : name;
    Document document = new Document();
    document.setName(new_name);
    return document;
  }
}
