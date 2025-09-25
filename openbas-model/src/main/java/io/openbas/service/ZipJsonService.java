package io.openbas.service;

import static io.openbas.utils.reflection.CollectionUtils.isCollection;
import static io.openbas.utils.reflection.CollectionUtils.toCollection;
import static io.openbas.utils.reflection.FieldUtils.getAllFields;
import static io.openbas.utils.reflection.FieldUtils.getField;
import static io.openbas.utils.reflection.RelationUtils.isRelation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import io.openbas.database.model.Base;
import io.openbas.database.model.Document;
import io.openbas.database.repository.DocumentRepository;
import io.openbas.jsonapi.*;
import jakarta.annotation.Resource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import lombok.*;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ZipJsonService<T extends Base> {

  public static final String IMPORTED_OBJECT_NAME_SUFFIX = " (Import)";

  private static final String META_ENTRY = "meta.json";

  @Resource private ObjectMapper mapper = new ObjectMapper();
  private final GenericJsonApiImporter<T> importer;
  private final GenericJsonApiExporter exporter;
  private final DocumentRepository documentRepository;
  private final FileService fileService;

  public byte[] handleExportResource(
      T entity, Map<String, byte[]> extras, JsonApiDocument<ResourceObject> resource)
      throws IOException {
    if (extras == null) {
      extras = new HashMap<>();
    }

    for (Field field : getAllFields(entity.getClass())) {
      if (!isRelation(field)) {
        continue;
      }

      Object value = getField(entity, field);
      if (value == null) {
        continue;
      }

      if (isCollection(field)) {
        Collection<?> col = toCollection(value);
        for (Object item : col) {
          if (item instanceof Document doc) {
            addDocumentToExtras(doc, extras);
          }
        }
      } else if (value instanceof Document doc) {
        addDocumentToExtras(doc, extras);
      }
    }

    return this.writeZip(resource, extras);
  }

  public JsonApiDocument<ResourceObject> handleImport(
      byte[] fileBytes, String nameAttributeKey, IncludeOptions includeOptions) throws IOException {
    ParsedZip parsed = this.readZip(fileBytes);
    JsonApiDocument<ResourceObject> doc = parsed.getDocument();

    if (doc.data() != null && doc.data().attributes() != null) {
      Object current = doc.data().attributes().get(nameAttributeKey);
      if (current instanceof String s) {
        doc.data().attributes().put(nameAttributeKey, s + IMPORTED_OBJECT_NAME_SUFFIX);
      }
    }

    importer.handleImportDocument(doc, parsed.extras);
    T persisted = importer.handleImportEntity(doc, includeOptions);

    return exporter.handleExport(persisted, includeOptions);
  }

  private void addDocumentToExtras(Document doc, Map<String, byte[]> out) {
    Document resolved =
        documentRepository.findById(doc.getId()).orElseThrow(IllegalArgumentException::new);

    Optional<InputStream> docStream = fileService.getFile(resolved);
    if (docStream.isPresent()) {
      try {
        byte[] bytes = docStream.get().readAllBytes();
        out.put(resolved.getTarget(), bytes);
      } catch (IOException e) {
        throw new IllegalArgumentException("Failed to read file");
      }
    }
  }

  public byte[] writeZip(JsonApiDocument<ResourceObject> document, Map<String, byte[]> extras)
      throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ZipOutputStream zos = new ZipOutputStream(bytes)) {
      String rootType = document.data() != null ? document.data().type() : "document";
      String entryName = rootType + ".json";

      // document.json
      zos.putNextEntry(new ZipEntry(entryName));
      ObjectWriter writer = mapper.writerWithDefaultPrettyPrinter();
      zos.write(writer.writeValueAsBytes(document));
      zos.closeEntry();

      // meta.json (schema versioning)
      Map<String, Object> meta = Map.of("schema", Map.of("kind", "jsonapi", "version", 1));
      zos.putNextEntry(new ZipEntry(META_ENTRY));
      zos.write(mapper.writeValueAsBytes(meta));
      zos.closeEntry();

      if (extras != null) {
        for (var e : extras.entrySet()) {
          if (e.getKey() == null || e.getKey().isBlank()) {
            continue;
          }
          zos.putNextEntry(new ZipEntry(e.getKey()));
          zos.write(e.getValue());
          zos.closeEntry();
        }
      }
    }
    return bytes.toByteArray();
  }

  private ParsedZip readZip(byte[] bytes) throws IOException {
    JsonApiDocument<ResourceObject> doc = null;
    Map<String, byte[]> extras = new HashMap<>();

    try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(bytes))) {
      ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null) {
        byte[] content = readAll(zis);
        if (entry.getName().endsWith(".json") && !META_ENTRY.equals(entry.getName())) {
          try {
            doc =
                mapper.readValue(
                    content,
                    mapper
                        .getTypeFactory()
                        .constructParametricType(JsonApiDocument.class, ResourceObject.class));
          } catch (Exception e) {
            throw new IllegalArgumentException(
                "Invalid JSONAPI document in ZIP: " + entry.getName(), e);
          }
        } else if (!META_ENTRY.equals(entry.getName())) {
          extras.put(entry.getName(), content);
        }
        zis.closeEntry();
      }
    }

    if (doc == null) {
      throw new IllegalArgumentException("ZIP must contain a json file");
    }
    return new ParsedZip(doc, extras);
  }

  private static byte[] readAll(InputStream in) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    in.transferTo(bytes);
    return bytes.toByteArray();
  }

  @Getter
  @AllArgsConstructor
  @NoArgsConstructor(access = AccessLevel.PRIVATE)
  public static class ParsedZip {

    JsonApiDocument<ResourceObject> document;
    Map<String, byte[]> extras;
  }
}
