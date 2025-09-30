package io.openbas.jsonapi;

import static java.time.format.DateTimeFormatter.ofPattern;

import io.openbas.database.model.Base;
import io.openbas.service.ZipJsonService;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.function.Function;
import lombok.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
@RequiredArgsConstructor
public class ZipJsonApi<T extends Base> {

  public static final DateTimeFormatter FORMATTER = ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");

  private final GenericJsonApiExporter exporter;
  private final ZipJsonService<T> zipJsonService;

  // -- EXPORT --

  public ResponseEntity<byte[]> handleExport(T entity) throws IOException {
    return handleExport(entity, null, null);
  }

  public ResponseEntity<byte[]> handleExport(
      T entity, Map<String, byte[]> extras, IncludeOptions includeOptions) throws IOException {

    JsonApiDocument<ResourceObject> resource = exporter.handleExport(entity, includeOptions);
    byte[] zipBytes = this.zipJsonService.handleExportResource(entity, extras, resource);

    String filename =
        resource.data().type()
            + "-"
            + entity.getId()
            + "-"
            + ZonedDateTime.now().format(FORMATTER)
            + ".zip";

    HttpHeaders headers = new HttpHeaders();
    headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename);

    return ResponseEntity.ok()
        .headers(headers)
        .contentLength(zipBytes.length)
        .contentType(MediaType.parseMediaType("application/zip"))
        .body(zipBytes);
  }

  // -- IMPORT --

  public ResponseEntity<JsonApiDocument<ResourceObject>> handleImport(
      MultipartFile file, String nameAttributeKey) throws IOException {
    return handleImport(file, nameAttributeKey, null, null);
  }

  public ResponseEntity<JsonApiDocument<ResourceObject>> handleImport(
      MultipartFile file,
      String nameAttributeKey,
      IncludeOptions includeOptions,
      Function<T, T> sanityCheck)
      throws IOException {
    return ResponseEntity.ok(
        this.zipJsonService.handleImport(
            file.getBytes(), nameAttributeKey, includeOptions, sanityCheck));
  }
}
