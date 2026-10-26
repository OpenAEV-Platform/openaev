package io.openaev.xtmhub;

import java.util.Set;

/**
 * News feed items consumed from XTM Hub, with the tenants registered on the Hub at that time: their
 * users are the recipients of the items.
 */
public record NewsFeedConsumption(
    ProvisionedNewsFeedItems provisioned, Set<String> registeredTenantIds) {}
