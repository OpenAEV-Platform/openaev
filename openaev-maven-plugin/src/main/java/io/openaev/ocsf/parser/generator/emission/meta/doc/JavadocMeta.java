package io.openaev.ocsf.parser.generator.emission.meta.doc;

import io.openaev.ocsf.parser.generator.emission.Emitter;

public class JavadocMeta implements Emitter {
  private final String text;

  public JavadocMeta(String text) {
    this.text = text;
  }

  @Override
  public String emit() {
    return "/**\n* " + text + "\n*/";
  }
}
