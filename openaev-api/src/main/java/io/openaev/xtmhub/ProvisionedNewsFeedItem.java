package io.openaev.xtmhub;

import java.time.Instant;
import java.util.List;

/** A news feed item as provisioned by XTM Hub; {@code type} is kept raw as the Hub sends it. */
public record ProvisionedNewsFeedItem(
    String id,
    String title,
    String type,
    List<String> tags,
    String urlPath,
    Instant creationDate,
    boolean deleted) {}
