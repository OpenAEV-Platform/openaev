package io.openaev.xtmhub;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * A news feed item as provisioned by XTM Hub; {@code type} is kept raw as the Hub sends it, and
 * {@code metadata} holds the other details it sends as key/value pairs (all but the URL path).
 */
public record ProvisionedNewsFeedItem(
    String id,
    String title,
    String type,
    List<String> tags,
    String urlPath,
    Map<String, String> metadata,
    Instant creationDate,
    boolean deleted) {}
