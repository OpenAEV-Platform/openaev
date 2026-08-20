package io.openaev.integration.impl.injectors.channel;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.openaev.api.url_access_token.UrlAccessTokenService;
import io.openaev.authorisation.HttpClientFactory;
import io.openaev.database.model.ConnectorInstance;
import io.openaev.database.model.ConnectorType;
import io.openaev.database.repository.ArticleRepository;
import io.openaev.executors.InjectorContext;
import io.openaev.healthcheck.enums.ExternalServiceDependency;
import io.openaev.injectors.channel.ChannelContract;
import io.openaev.injectors.email.service.EmailService;
import io.openaev.integration.BuiltinIntegrationFactory;
import io.openaev.integration.ComponentRequestEngine;
import io.openaev.integration.Integration;
import io.openaev.service.InjectExpectationService;
import io.openaev.service.InjectorService;
import io.openaev.service.catalog_connectors.CatalogConnectorService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ChannelInjectorIntegrationFactory extends BuiltinIntegrationFactory {
  private final ChannelContract channelContract;
  private final InjectorContext injectorContext;

  private final EmailService emailService;
  private final InjectorService injectorService;
  private final InjectExpectationService injectExpectationService;
  private final ArticleRepository articleRepository;
  private final ConnectorInstanceService connectorInstanceService;
  private final ComponentRequestEngine componentRequestEngine;
  private final UrlAccessTokenService urlAccessTokenService;

  public ChannelInjectorIntegrationFactory(
      ComponentRequestEngine componentRequestEngine,
      ConnectorInstanceService connectorInstanceService,
      CatalogConnectorService catalogConnectorService,
      ChannelContract channelContract,
      InjectorContext injectorContext,
      EmailService emailService,
      InjectorService injectorService,
      InjectExpectationService injectExpectationService,
      ArticleRepository articleRepository,
      HttpClientFactory httpClientFactory,
      UrlAccessTokenService urlAccessTokenService) {
    super(connectorInstanceService, catalogConnectorService, httpClientFactory);
    this.componentRequestEngine = componentRequestEngine;
    this.connectorInstanceService = connectorInstanceService;
    this.channelContract = channelContract;
    this.injectorContext = injectorContext;
    this.emailService = emailService;
    this.injectorService = injectorService;
    this.injectExpectationService = injectExpectationService;
    this.articleRepository = articleRepository;
    this.urlAccessTokenService = urlAccessTokenService;
  }

  @Override
  protected final String getClassName() {
    return ChannelInjectorIntegrationFactory.class.getCanonicalName();
  }

  @Override
  protected void runMigrations(String tenantId) throws Exception {
    // noop
  }

  @Override
  protected void insertCatalogEntry() throws Exception {
    // noop
  }

  @Override
  public List<ConnectorInstance> findRelatedInstances(String tenantId) {
    return List.of(
        connectorInstanceService.createAutostartInstance(
            ChannelInjectorIntegration.CHANNEL_INJECTOR_ID,
            this.getClassName(),
            ConnectorType.INJECTOR));
  }

  @Override
  public Integration spawn(ConnectorInstance instance)
      throws JsonProcessingException,
          InvocationTargetException,
          NoSuchMethodException,
          InstantiationException,
          IllegalAccessException {
    return new ChannelInjectorIntegration(
        componentRequestEngine,
        instance,
        connectorInstanceService,
        channelContract,
        injectorContext,
        emailService,
        injectorService,
        injectExpectationService,
        articleRepository,
        urlAccessTokenService);
  }

  @Override
  public void registerConnectorForTenant(String tenantId) throws Exception {
    injectorService.registerBuiltinInjector(
        tenantId,
        ChannelInjectorIntegration.CHANNEL_INJECTOR_ID,
        ChannelInjectorIntegration.CHANNEL_INJECTOR_NAME,
        channelContract,
        false,
        "media-pressure",
        null,
        null,
        false,
        List.of(ExternalServiceDependency.SMTP, ExternalServiceDependency.IMAP));
  }
}
