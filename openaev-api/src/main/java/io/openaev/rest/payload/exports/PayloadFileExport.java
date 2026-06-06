package io.openaev.rest.payload.exports;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.database.model.ArgumentType;
import io.openaev.database.model.Document;
import io.openaev.database.model.Payload;
import io.openaev.database.repository.DocumentRepository;
import io.openaev.export.FileExportBase;
import io.openaev.context.ExecState;
import java.util.List;
import java.util.Optional;
import lombok.Getter;

@Getter
@JsonInclude(NON_NULL)
public class PayloadFileExport extends FileExportBase {
  @JsonIgnore protected final ExecState state;
  @JsonIgnore protected final DocumentRepository documentRepository;

  @JsonProperty("payload_information")
  private Payload payload;

  // TODO chunk 2 of 4458
  //  @JsonProperty("payload_tags")
  //  private List<Tag> getTags() {
  //    List<Tag> allTags = new ArrayList<>(this.payload.getTags().stream().toList());
  //    if (this.payload.getAttachedDocument().isPresent()) {
  //
  // allTags.addAll(this.payload.getAttachedDocument().orElseThrow().getTags().stream().toList());
  //    }
  //    return allTags;
  //  }

  @JsonProperty("payload_document")
  private Document getDocument() {
    return this.payload.getAttachedDocument().orElse(null);
  }

  @JsonProperty("payload_arguments_documents")
  private List<Document> getArgumentsDocuments() {
    return this.payload.getArguments().stream()
        .filter(payloadArgument -> ArgumentType.Document == payloadArgument.getType())
        .map(
            payloadArgument ->
                this.documentRepository
                    .forOp(this.state)
                    .findById(payloadArgument.getDefaultValue()))
        .filter(Optional::isPresent).map(Optional::get)
        .toList();
  }

  // TODO chunk 2 of 4458
  //  @JsonProperty("payload_attack_patterns")
  //  private List<AttackPattern> getAttackPatterns() {
  //    return this.payload.getAttackPatterns().stream().toList();
  //  }

  private PayloadFileExport(
      ExecState state, Payload payload, ObjectMapper objectMapper, DocumentRepository documentRepository) {
    super(objectMapper, null, null);
    this.state = state;
    this.payload = payload;
    this.documentRepository = documentRepository;
  }

  public static PayloadFileExport fromPayload(
      ExecState state, Payload payload, ObjectMapper objectMapper, DocumentRepository documentRepository) {
    return new PayloadFileExport(state, payload, objectMapper, documentRepository);
  }
}
