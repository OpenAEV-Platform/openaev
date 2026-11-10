package io.openaev.xtmhub;

import java.util.List;

/** What XTM Hub returns when the news feed items provisioned for the platform are consumed. */
public record ProvisionedNewsFeedItems(
    List<ProvisionedNewsFeedItem> items, List<String> availableTypes) {}
