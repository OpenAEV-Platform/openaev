package io.openaev.database.raw;

import io.openaev.database.model.Document;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Tag;
import java.util.List;
import lombok.Data;

@Data
public class RawPaginationDocument {

  String document_id;
  String document_name;
  String document_description;
  List<String> document_exercises;
  List<String> document_scenarios;
  String document_type;
  List<String> document_tags;
  boolean document_can_be_deleted = true;

  public RawPaginationDocument(final Document document) {
    this.document_id = document.getId();
    this.document_name = document.getName();
    this.document_description = document.getDescription();
    this.document_exercises = document.getExercises().stream().map(Exercise::getId).toList();
    this.document_scenarios = document.getScenarios().stream().map(Scenario::getId).toList();
    this.document_type = document.getType();
    this.document_tags = document.getTags().stream().map(Tag::getId).toList();
  }
}
