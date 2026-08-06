package io.openaev.ocsf.parser.generator.emission.render;

public class RenderFactory {
  public <T> Render<T> getRender(T obj) {
    if (obj instanceof Integer) return (Render<T>) new IntegerRender();
    if (obj instanceof Boolean) return (Render<T>) new BooleanRender();
    if (obj instanceof Float) return (Render<T>) new FloatRender();
    if (obj instanceof Double) return (Render<T>) new DoubleRender();
    if (obj instanceof Character) return (Render<T>) new CharacterRender();
    if (obj instanceof String) return (Render<T>) new StringRender();
    throw new UnsupportedOperationException("Unsupported type");
  }
}
