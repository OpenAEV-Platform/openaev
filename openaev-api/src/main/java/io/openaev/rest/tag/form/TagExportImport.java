package io.openaev.rest.tag.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class TagExportImport {

  public TagExportImport() {}

  public TagExportImport(String name, String color) {
    this.name = name;
    this.color = color;
  }

  @JsonProperty("tag_name")
  private String name;

  @JsonProperty("tag_color")
  private String color;
}
