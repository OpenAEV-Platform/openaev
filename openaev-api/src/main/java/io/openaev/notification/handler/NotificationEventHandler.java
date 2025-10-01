package io.openaev.notification.handler;

import io.openaev.notification.model.NotificationEvent;

public interface NotificationEventHandler {
  void handle(NotificationEvent event);
}
