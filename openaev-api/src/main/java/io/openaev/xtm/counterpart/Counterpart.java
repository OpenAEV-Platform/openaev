package io.openaev.xtm.counterpart;

/**
 * An object of another XTM platform that matches an object of this one.
 *
 * @param key the normalised key it matched on
 * @param product the product that holds it (opencti)
 * @param entityId the id of the object in that product
 * @param entityType the type of the object in that product (Attack-Pattern, System...)
 * @param name the display name of the object in that product
 * @param url the deep link that opens the object in that product
 */
public record Counterpart(
    String key, String product, String entityId, String entityType, String name, String url) {}
