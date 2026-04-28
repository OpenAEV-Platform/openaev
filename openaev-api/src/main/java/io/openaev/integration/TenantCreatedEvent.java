package io.openaev.integration;

import io.openaev.database.model.Tenant;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Published after a tenant has been successfully created and all synchronous dependencies have been
 * committed. Listeners can use this to register additional components in a separate transaction.
 */
@Getter
public class TenantCreatedEvent extends ApplicationEvent {

  private final Tenant tenant;

  public TenantCreatedEvent(Object source, Tenant tenant) {
    super(source);
    this.tenant = tenant;
  }
}
