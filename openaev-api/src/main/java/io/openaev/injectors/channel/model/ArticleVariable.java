package io.openaev.injectors.channel.model;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class ArticleVariable {

  private String id;
  private String name;
  private String uri;

  public ArticleVariable(String id, String name, String uri) {
    this.id = id;
    this.name = name;
    this.uri = uri;
  }
}
