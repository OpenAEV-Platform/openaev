package io.openaev.database.raw;

/** Projection of {@link io.openaev.database.repository.UserRepository#rawNewsFeedRecipients} */
public interface RawNewsFeedRecipient {

  String getId();

  String[] getUnsubscribedTypes();
}
