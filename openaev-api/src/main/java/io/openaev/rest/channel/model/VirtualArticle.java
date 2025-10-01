package io.openaev.rest.channel.model;

import java.time.Instant;
import java.util.Objects;

public record VirtualArticle(Instant date, String id) {
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    VirtualArticle that = (VirtualArticle) o;
    return id.equals(that.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }
}
