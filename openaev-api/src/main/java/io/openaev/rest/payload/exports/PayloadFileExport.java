package io.openaev.rest.payload.exports;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.database.model.AttackPattern;
import io.openaev.database.model.Document;
import io.openaev.database.model.Payload;
import io.openaev.database.model.Tag;
import io.openaev.database.repository.DocumentRepository;
import io.openaev.export.FileExportBase;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.Getter;

@Getter
@JsonInclude(NON_NULL)
public class PayloadFileExport extends FileExportBase {
  @JsonIgnore protected final DocumentRepository documentRepository;

  @JsonProperty("payload_information")
  private Payload payload;

  @JsonProperty("payload_tags")
  private List<Tag> getTags() {
    List<Tag> allTags = new ArrayList<>(this.payload.getTags().stream().toList());
    if (this.payload.getAttachedDocument().isPresent()) {
      allTags.addAll(this.payload.getAttachedDocument().orElseThrow().getTags().stream().toList());
    }
    return allTags;
  }

  @JsonProperty("payload_document")
  private Document getDocument() {
    return this.payload.getAttachedDocument().orElse(null);
  }

  @JsonProperty("payload_arguments_documents")
  private List<Document> getArgumentsDocuments() {
    return this.payload.getArguments().stream()
        .filter(payloadArgument -> payloadArgument.getType().equals("document"))
        .map(payloadArgument -> this.documentRepository.findById(payloadArgument.getDefaultValue()))
        .flatMap(Optional::stream)
        .toList();
  }

  @JsonProperty("payload_attack_patterns")
  private List<AttackPattern> getAttackPatterns() {
    return this.payload.getAttackPatterns().stream().toList();
  }

  private PayloadFileExport(
      Payload payload, ObjectMapper objectMapper, DocumentRepository documentRepository) {
    super(objectMapper, null, null);
    this.payload = payload;
    this.documentRepository = documentRepository;
  }

  public static PayloadFileExport fromPayload(
      Payload payload, ObjectMapper objectMapper, DocumentRepository documentRepository) {
    return new PayloadFileExport(payload, objectMapper, documentRepository);
  }
}
