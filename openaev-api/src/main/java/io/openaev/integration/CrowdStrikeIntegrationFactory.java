package io.openaev.integration;

import io.openaev.database.model.ConnectorInstance;
import org.springframework.stereotype.Service;

@Service
public class CrowdStrikeIntegrationFactory implements IntegrationFactory {
  @Override
  public Integration spawn(ConnectorInstance instance) {
    return new CrowdStrikeIntegration();
  }
}
