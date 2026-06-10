package io.openaev.utils.helpers;

import io.openaev.database.model.*;
import io.openaev.database.repository.*;
import io.openaev.utils.fixtures.*;
import io.openaev.utils.fixtures.composers.*;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class InjectTestHelper {

  private final InjectExpectationRepository injectExpectationRepository;
  private final PayloadRepository payloadRepository;
  private final InjectorContractRepository injectorContractRepository;
  private final AgentRepository agentRepository;
  private final EndpointRepository endpointRepository;
  private final InjectRepository injectRepository;
  private final FindingRepository findingRepository;
  private final AssetRepository assetRepository;
  private final DomainRepository domainRepository;
  private final InjectorRepository injectorRepository;

  public Inject getPendingInjectWithAssets(
      InjectComposer injectComposer,
      InjectorContractComposer injectorContractComposer,
      EndpointComposer endpointComposer,
      AgentComposer agentComposer,
      InjectStatusComposer injectStatusComposer) {
    return injectComposer
        .forInject(InjectFixture.getDefaultInject())
        .withInjectorContract(
            injectorContractComposer
                .forInjectorContract(InjectorContractFixture.createDefaultInjectorContract())
                .withInjector(InjectorFixture.createDefaultPayloadInjector()))
        .withEndpoint(
            endpointComposer
                .forEndpoint(EndpointFixture.createEndpoint())
                .withAgent(agentComposer.forAgent(AgentFixture.createDefaultAgentService()))
                .withAgent(agentComposer.forAgent(AgentFixture.createDefaultAgentSession())))
        .withInjectStatus(
            injectStatusComposer.forInjectStatus(InjectStatusFixture.createPendingInjectStatus()))
        .persist()
        .get();
  }

  public InjectExpectation forceSaveInjectExpectation(InjectExpectation expectation) {
    return injectExpectationRepository.save(expectation);
  }

  public Domain forceSaveDomain(Domain domain) {
    return domainRepository.save(domain);
  }

  public Payload forceSavePayload(Payload payload) {
    return payloadRepository.save(payload);
  }

  public InjectorContract forceSaveInjectorContract(InjectorContract injectorContract) {
    return injectorContractRepository.save(injectorContract);
  }

  public Inject forceSaveInject(Inject inject) {
    return injectRepository.save(inject);
  }

  public Agent forceSaveAgent(Agent agent) {
    return agentRepository.save(agent);
  }

  public Injector forceSaveInjector(Injector injector) {
    return injectorRepository.save(injector);
  }

  public Endpoint forceSaveEndpoint(Endpoint endpoint) {
    return endpointRepository.save(endpoint);
  }

  public Finding forceSaveFinding(Finding finding) {
    return findingRepository.save(finding);
  }

  public Asset forceSaveAsset(Asset asset) {
    return assetRepository.save(asset);
  }

  /**
   * Queries findings for a given inject ID in a new independent transaction, so that findings
   * committed by async processing threads are visible even when called from within an outer
   * {@code @Transactional} test method.
   */
  public List<Finding> findFindingsByInjectId(String injectId) {
    return findingRepository.findAllByInjectId(injectId);
  }
}
