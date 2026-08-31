package io.openaev.ocsf.parser.generator.emission.meta.enums;

import io.openaev.ocsf.parser.generator.emission.Emitter;
import io.openaev.ocsf.parser.generator.emission.render.Render;
import io.openaev.ocsf.parser.generator.emission.render.RenderFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Getter;

public class OptionMeta implements Emitter {
  @Getter private final String name;
  private final RenderFactory renderFactory = new RenderFactory();
  private final List<Render<?>> args = new ArrayList<>();

  public OptionMeta(String name) {
    this.name = name;
  }

  public OptionMeta withValue(Object arg) {
    args.add(renderFactory.getRender(arg));
    return this;
  }

  @Override
  public String emit() {
    StringBuilder sb = new StringBuilder(name.toUpperCase());

    if (!args.isEmpty()) {
      sb.append("(")
          .append(args.stream().map(Render::render).collect(Collectors.joining(",")))
          .append(")");
    }

    return sb.toString();
  }
}
