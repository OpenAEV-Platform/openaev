package io.openaev.service;

import static io.openaev.asset.QueueService.EXCHANGE_KEY;
import static io.openaev.asset.QueueService.ROUTING_KEY;
import static io.openaev.helper.StreamHelper.fromIterable;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import io.openaev.config.RabbitmqConfig;
import io.openaev.database.model.*;
import io.openaev.database.repository.AttackPatternRepository;
import io.openaev.database.repository.ConnectorInstanceConfigurationRepository;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.database.repository.InjectorRepository;
import io.openaev.healthcheck.enums.ExternalServiceDependency;
import io.openaev.rest.catalog_connector.dto.ConnectorIds;
import io.openaev.rest.domain.DomainService;
import io.openaev.rest.injector.form.InjectorCreateInput;
import io.openaev.rest.injector.form.InjectorOutput;
import io.openaev.rest.injector.response.InjectorConnection;
import io.openaev.rest.injector.response.InjectorRegistration;
import io.openaev.rest.injector_contract.InjectorContractService;
import io.openaev.rest.injector_contract.form.InjectorContractInput;
import io.openaev.service.catalog_connectors.CatalogConnectorService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import io.openaev.service.connectors.AbstractConnectorService;
import io.openaev.utils.mapper.CatalogConnectorMapper;
import io.openaev.utils.mapper.InjectorMapper;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service("coreInjectorService")
// TODO needs to be merged with integrations/InjectorService
public class InjectorService extends AbstractConnectorService<Injector, InjectorOutput> {
  private static final String DUMMY_SUFFIX = "_dummy";

  @Resource private RabbitmqConfig rabbitmqConfig;
  private final InjectorRepository injectorRepository;
  private final InjectorContractRepository injectorContractRepository;
  private final AttackPatternRepository attackPatternRepository;

  private final FileService fileService;
  private final InjectorContractService injectorContractService;
  private final DomainService domainService;

  private final InjectorMapper injectorMapper;

  @Autowired
  public InjectorService(
      InjectorRepository injectorRepository,
      InjectorContractRepository injectorContractRepository,
      AttackPatternRepository attackPatternRepository,
      ConnectorInstanceConfigurationRepository connectorInstanceConfigurationRepository,
      FileService fileService,
      ConnectorInstanceService connectorInstanceService,
      CatalogConnectorService catalogConnectorService,
      InjectorContractService injectorContractService,
      DomainService domainService,
      InjectorMapper injectorMapper,
      CatalogConnectorMapper catalogConnectorMapper) {
    super(
        ConnectorType.INJECTOR,
        connectorInstanceConfigurationRepository,
        catalogConnectorService,
        connectorInstanceService,
        catalogConnectorMapper);
    this.injectorRepository = injectorRepository;
    this.injectorContractRepository = injectorContractRepository;
    this.attackPatternRepository = attackPatternRepository;
    this.fileService = fileService;
    this.injectorContractService = injectorContractService;
    this.domainService = domainService;
    this.injectorMapper = injectorMapper;
  }

  @Override
  protected List<Injector> getAllConnectors() {
    return fromIterable(injectorRepository.findAll());
  }

  @Override
  protected Injector getConnectorById(String injectorId) {
    return injectorRepository.findById(injectorId).orElse(null);
  }

  @Override
  protected InjectorOutput mapToOutput(
      Injector injector,
      CatalogConnector catalogConnector,
      ConnectorInstance instance,
      boolean existingInjector) {
    return injectorMapper.toInjectorOutput(injector, catalogConnector, instance, existingInjector);
  }

  @Override
  protected Injector createNewConnector() {
    return new Injector();
  }

  /**
   * Create a dummmy injector, that is used when importing the starter pack before the real
   * injectors are registered
   *
   * @param injectorType
   * @param injectorName
   * @return
   */
  public Injector createDummyInjector(
      @NotBlank final String injectorType, @NotBlank final String injectorName) {
    Injector injector = new Injector();
    injector.setName("Dummy " + injectorName);
    injector.setType(injectorType + DUMMY_SUFFIX);
    injector.setId(injectorType + DUMMY_SUFFIX);
    injector.setDependencies(
        new ExternalServiceDependency[] {ExternalServiceDependency.fromValue(injectorType)});
    return injectorRepository.save(injector);
  }

  /**
   * Check if a dummy injector exist for an injector type and delete it
   *
   * @param injectorType
   */
  public void deleteDummyInjectorIfItExists(@NotBlank final String injectorType) {
    injectorRepository.findById(injectorType + DUMMY_SUFFIX).ifPresent(injectorRepository::delete);
  }

  /**
   * This method will check if the injector type is a dummy if yes it will remove the dummy suffix
   * if no it will return the parameter It is used to send the execution to the correct injector
   * even if the current one is just a dummy injector
   *
   * @param injectorType
   * @return
   */
  public String getOriginInjectorType(@NotBlank final String injectorType) {
    if (injectorType.endsWith(DUMMY_SUFFIX)) {
      return injectorType.substring(0, injectorType.length() - DUMMY_SUFFIX.length());
    }
    return injectorType;
  }

  public List<Injector> findAll() {
    return StreamSupport.stream(injectorRepository.findAll().spliterator(), false)
        .collect(Collectors.toList());
  }

  /**
   * Retrieve all injectors.
   *
   * @param isIncludeNext Include pending injectors.
   * @return List of injector output
   */
  public Iterable<InjectorOutput> injectorsOutput(boolean isIncludeNext) {
    return getConnectorsOutput(isIncludeNext);
  }

  /**
   * Find injector by its type
   *
   * @param injectorType injector type to search for
   * @return an Optional containing the injector if found, empty otherwise
   */
  public Optional<Injector> injectorByType(@NotBlank final String injectorType) {
    return injectorRepository.findByType(injectorType);
  }

  /**
   * Retrieves IDs of resources associated with an injector.
   *
   * @param injectorId injector identifier.
   * @return connector instance ID and catalog connector ID if available, null values if not found
   */
  public ConnectorIds getInjectorRelationsId(String injectorId) {
    return getConnectorRelationsId(injectorId);
  }

  public InjectorRegistration registerInjector(
      InjectorCreateInput input, Optional<MultipartFile> file) {
    ConnectionFactory factory = new ConnectionFactory();
    factory.setHost(rabbitmqConfig.getHostname());
    factory.setPort(rabbitmqConfig.getPort());
    factory.setUsername(rabbitmqConfig.getUser());
    factory.setPassword(rabbitmqConfig.getPass());
    factory.setVirtualHost(rabbitmqConfig.getVhost());
    // Declare queueing
    Connection connection = null;
    try {
      if (rabbitmqConfig.isSsl()) {
        factory.useSslProtocol();
      }
      // Upload icon
      if (file.isPresent() && "image/png".equals(file.get().getContentType())) {
        fileService.uploadFile(
            FileService.INJECTORS_IMAGES_BASE_PATH + input.getType() + ".png", file.get());
      }
      connection = factory.newConnection();
      Channel channel = connection.createChannel();
      String queueName = rabbitmqConfig.getPrefix() + "_injector_" + input.getType();
      Map<String, Object> queueOptions = new HashMap<>();
      queueOptions.put("x-queue-type", rabbitmqConfig.getQueueType());
      channel.queueDeclare(queueName, true, false, false, queueOptions);
      String routingKey = rabbitmqConfig.getPrefix() + ROUTING_KEY + input.getType();
      String exchangeKey = rabbitmqConfig.getPrefix() + EXCHANGE_KEY;
      channel.exchangeDeclare(exchangeKey, "direct", true);
      channel.queueBind(queueName, exchangeKey, routingKey);
      // We need to support upsert for registration
      Injector injector = injectorRepository.findById(input.getId()).orElse(null);
      if (injector == null) {
        Injector injectorChecking = injectorRepository.findByType(input.getType()).orElse(null);
      }
      if (injector != null) {
        updateInjector(
            injector,
            input.getType(),
            input.getName(),
            input.getContracts(),
            input.getCustomContracts(),
            input.getCategory(),
            input.getExecutorCommands(),
            input.getExecutorClearCommands(),
            input.getPayloads());
      } else {
        // save the injector
        Injector newInjector = new Injector();
        newInjector.setId(input.getId());
        newInjector.setExternal(true);
        newInjector.setName(input.getName());
        newInjector.setType(input.getType());
        newInjector.setCategory(input.getCategory());
        newInjector.setCustomContracts(input.getCustomContracts());
        newInjector.setExecutorCommands(input.getExecutorCommands());
        newInjector.setExecutorClearCommands(input.getExecutorClearCommands());
        newInjector.setPayloads(input.getPayloads());
        Injector savedInjector = injectorRepository.save(newInjector);
        // Save the contracts
        List<InjectorContract> injectorContracts =
            input.getContracts().stream()
                .map(in -> injectorContractService.convertInjectorFromInput(in, savedInjector))
                .toList();
        injectorContractRepository.saveAll(injectorContracts);

        // delete the dummy injector if it was created when importing the starter pack
        deleteDummyInjectorIfItExists(input.getType());
      }
      InjectorConnection conn =
          new InjectorConnection(
              rabbitmqConfig.getHostname(),
              rabbitmqConfig.getVhost(),
              rabbitmqConfig.isSsl(),
              rabbitmqConfig.getPort(),
              rabbitmqConfig.getUser(),
              rabbitmqConfig.getPass());
      return new InjectorRegistration(conn, queueName);
    } catch (Exception e) {
      throw new RuntimeException(e);
    } finally {
      if (connection != null) {
        try {
          connection.close();
        } catch (IOException e) {
          log.error(
              "Unable to close RabbitMQ connection. You should worry as this could impact performance",
              e);
        }
      }
    }
  }

  public Injector updateInjector(
      Injector injector,
      String type,
      String name,
      List<InjectorContractInput> contracts,
      Boolean customContracts,
      String category,
      Map<String, String> executorCommands,
      Map<String, String> executorClearCommands,
      Boolean payloads) {
    injector.setUpdatedAt(Instant.now());
    injector.setType(type);
    injector.setName(name);
    injector.setExternal(true);
    injector.setCustomContracts(customContracts);
    injector.setCategory(category);
    injector.setExecutorCommands(executorCommands);
    injector.setExecutorClearCommands(executorClearCommands);
    injector.setPayloads(payloads);
    List<String> existing = new ArrayList<>();
    List<String> toDeletes = new ArrayList<>();
    injector
        .getContracts()
        .forEach(
            contract -> {
              Optional<InjectorContractInput> current =
                  contracts.stream().filter(c -> c.getId().equals(contract.getId())).findFirst();
              if (current.isPresent()) {
                existing.add(contract.getId());
                contract.setManual(current.get().isManual());
                contract.setLabels(current.get().getLabels());
                contract.setContent(current.get().getContent());
                contract.setAtomicTesting(current.get().isAtomicTesting());
                contract.setPlatforms(current.get().getPlatforms());
                if (!current.get().getAttackPatternsExternalIds().isEmpty()) {
                  List<AttackPattern> attackPatterns =
                      fromIterable(
                          attackPatternRepository.findAllByExternalIdInIgnoreCase(
                              current.get().getAttackPatternsExternalIds()));
                  contract.setAttackPatterns(attackPatterns);
                } else {
                  contract.setAttackPatterns(new ArrayList<>());
                }

                if (!payloads) {
                  Set<Domain> currentDomains = this.domainService.upserts(contract.getDomains());
                  Set<Domain> domainsToAdd = this.domainService.upserts(current.get().getDomains());
                  contract.setDomains(
                      this.domainService.mergeDomains(currentDomains, domainsToAdd));
                }
              } else if (!contract.getCustom()) {
                toDeletes.add(contract.getId());
              }
            });
    List<InjectorContract> toCreates =
        contracts.stream()
            .filter(c -> !existing.contains(c.getId()))
            .map(in -> injectorContractService.convertInjectorFromInput(in, injector))
            .toList();
    injectorContractRepository.deleteAllById(toDeletes);
    injectorContractRepository.saveAll(toCreates);
    return injectorRepository.save(injector);
  }
}
