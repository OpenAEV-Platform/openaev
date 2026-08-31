package io.openaev.ocsf.parser.generator.emission.render;

public class RenderFactory {
  public <T> Render<T> getRender(T obj) {
    if (obj instanceof Integer) return (Render<T>) new IntegerRender((Integer) obj);
    if (obj instanceof Boolean) return (Render<T>) new BooleanRender((Boolean) obj);
    if (obj instanceof Float) return (Render<T>) new FloatRender((Float) obj);
    if (obj instanceof Double) return (Render<T>) new DoubleRender((Double) obj);
    if (obj instanceof Character) return (Render<T>) new CharacterRender((Character) obj);
    if (obj instanceof String) return (Render<T>) new StringRender((String) obj);
    throw new UnsupportedOperationException("Unsupported type %s".formatted(obj));
  }
}
