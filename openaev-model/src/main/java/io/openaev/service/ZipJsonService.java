package io.openaev.service;

import static io.openaev.utils.reflection.CollectionUtils.isCollection;
import static io.openaev.utils.reflection.CollectionUtils.toCollection;
import static io.openaev.utils.reflection.FieldUtils.getAllFields;
import static io.openaev.utils.reflection.FieldUtils.getField;
import static io.openaev.utils.reflection.RelationUtils.isRelation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import io.openaev.database.model.Base;
import io.openaev.database.model.Document;
import io.openaev.database.repository.DocumentRepository;
import io.openaev.jsonapi.*;
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
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import lombok.*;
import org.springframework.stereotype.Service;

/**
 * Service for exporting and importing entities as ZIP archives containing JSON API documents.
 *
 * <p>This service handles the serialization of entities with their related documents (files) into
 * ZIP archives for export, and the reverse process for import. The archive format includes:
 *
 * <ul>
 *   <li>A JSON API document ({@code <type>.json}) containing the entity and relationships
 *   <li>A metadata file ({@code meta.json}) with schema versioning information
 *   <li>Any associated document files referenced by the entity
 * </ul>
 *
 * <p>This is used for scenario/exercise import/export functionality.
 *
 * @param <T> the entity type being exported/imported
 * @see GenericJsonApiExporter
 * @see GenericJsonApiImporter
 */
@Service
@RequiredArgsConstructor
public class ZipJsonService<T extends Base> {

  /** Filename for the metadata entry in ZIP archives. */
  private static final String META_ENTRY = "meta.json";

  @Resource private ObjectMapper mapper = new ObjectMapper();
  private final GenericJsonApiImporter<T> importer;
  private final GenericJsonApiExporter exporter;
  private final DocumentRepository documentRepository;
  private final FileService fileService;

  /**
   * Exports an entity to a ZIP archive with all related documents.
   *
   * <p>This method scans the entity for Document relationships, retrieves their file content, and
   * packages everything into a ZIP archive along with the JSON API representation.
   *
   * @param entity the entity to export
   * @param extras additional files to include in the archive (may be null)
   * @param resource the JSON API document representation of the entity
   * @return the ZIP archive as a byte array
   * @throws IOException if writing the archive fails
   */
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

  /**
   * Imports an entity from a ZIP archive.
   *
   * <p>This method parses the ZIP archive, extracts the JSON API document and associated files,
   * persists the entity, and returns the resulting document representation.
   *
   * @param fileBytes the ZIP archive content
   * @param nameAttributeKey the attribute key for the entity name (for suffix appending)
   * @param includeOptions options controlling which relationships to include
   * @param sanityCheck function to validate/modify the entity before persistence
   * @param suffix suffix to append to the entity name (e.g., " (copy)")
   * @return the JSON API document for the imported entity
   * @throws IOException if reading the archive fails
   */
  public JsonApiDocument<ResourceObject> handleImport(
      byte[] fileBytes,
      String nameAttributeKey,
      IncludeOptions includeOptions,
      Function<T, T> sanityCheck,
      String suffix)
      throws IOException {
    ParsedZip parsed = this.readZip(fileBytes);
    JsonApiDocument<ResourceObject> doc = parsed.getDocument();

    if (doc.data() != null && doc.data().attributes() != null) {
      Object current = doc.data().attributes().get(nameAttributeKey);
      if (current instanceof String s) {
        doc.data().attributes().put(nameAttributeKey, s + suffix);
      }
    }

    importer.handleImportDocument(doc, parsed.extras);
    T persisted = importer.handleImportEntity(doc, includeOptions, sanityCheck);

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

  /**
   * Writes a JSON API document and extras to a ZIP archive.
   *
   * @param document the JSON API document to include
   * @param extras additional files to include, keyed by path
   * @return the ZIP archive as a byte array
   * @throws IOException if writing fails
   */
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

  /** Container for parsed ZIP archive contents. */
  @Getter
  @AllArgsConstructor
  @NoArgsConstructor(access = AccessLevel.PRIVATE)
  public static class ParsedZip {
    /** The JSON API document from the archive. */
    JsonApiDocument<ResourceObject> document;

    /** Additional files from the archive, keyed by path. */
    Map<String, byte[]> extras;
  }
}
