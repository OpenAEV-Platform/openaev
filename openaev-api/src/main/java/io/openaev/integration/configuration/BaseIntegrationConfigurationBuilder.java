package io.openaev.integration.configuration;

import io.openaev.database.model.*;
import io.openaev.service.connector_instances.EncryptionFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BaseIntegrationConfigurationBuilder {
  @Autowired private EncryptionFactory encryptionFactory;

  @Autowired private ApplicationContext context;

  public <T extends BaseIntegrationConfiguration> T build(Class<T> configurationClass) {
    T config = context.getAutowireCapableBeanFactory().createBean(configurationClass);
    config.setEncryptionFactory(encryptionFactory);
    return config;
  }
}
