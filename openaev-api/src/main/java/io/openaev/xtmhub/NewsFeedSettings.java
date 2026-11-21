package io.openaev.xtmhub;

import io.openaev.database.model.NewsFeedType;
import java.util.List;

/**
 * The XTM Hub news feed settings of a user.
 *
 * @param subscribedTypes the news feed types the user is subscribed to
 * @param hubRegistered whether the news feed is served by XTM Hub, that is a tenant in scope is
 *     registered
 * @param hubConnected whether a tenant in scope is registered and has not lost its connectivity
 * @param availableTypes the news feed types XTM Hub provisions for the tenants in scope that serve
 *     the news feed
 */
public record NewsFeedSettings(
    List<NewsFeedType> subscribedTypes,
    boolean hubRegistered,
    boolean hubConnected,
    List<NewsFeedType> availableTypes) {}
