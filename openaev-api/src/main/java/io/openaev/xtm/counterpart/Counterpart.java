package io.openaev.xtm.counterpart;

import java.util.List;

/**
 * An object of another XTM platform that matches an object of this one.
 *
 * @param key the normalised key it matched on
 * @param product the product that holds it (opencti, opencrq)
 * @param entityId the id of the object in that product
 * @param entityType the type of the object in that product (Attack-Pattern, System...)
 * @param name the display name of the object in that product
 * @param url the deep link that opens the object in that product
 * @param facts the key facts that product gives about it (risk scenarios, annual loss), never null
 */
public record Counterpart(
    String key,
    String product,
    String entityId,
    String entityType,
    String name,
    String url,
    List<Fact> facts) {

  /** One key fact: a stable key the screen translates, and its value as text. */
  public record Fact(String key, String value) {}

  public Counterpart {
    facts = facts == null ? List.of() : List.copyOf(facts);
  }

  public Counterpart(
      String key, String product, String entityId, String entityType, String name, String url) {
    this(key, product, entityId, entityType, name, url, List.of());
  }
}
