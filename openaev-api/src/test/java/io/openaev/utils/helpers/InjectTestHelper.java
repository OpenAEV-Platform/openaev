package io.openaev.utils.helpers;

import io.openaev.database.model.*;
import io.openaev.database.repository.*;
import io.openaev.utils.fixtures.AgentFixture;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.InjectStatusFixture;
import io.openaev.utils.fixtures.composers.AgentComposer;
import io.openaev.utils.fixtures.composers.EndpointComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.InjectStatusComposer;
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

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Inject getPendingInjectWithAssets(
      InjectComposer injectComposer,
      EndpointComposer endpointComposer,
      AgentComposer agentComposer,
      InjectStatusComposer injectStatusComposer) {
    return injectComposer
        .forInject(InjectFixture.getDefaultInject())
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

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public InjectExpectation forceSaveInjectExpectation(InjectExpectation expectation) {
    return injectExpectationRepository.save(expectation);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Domain forceSaveDomain(Domain domain) {
    return domainRepository.save(domain);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Payload forceSavePayload(Payload payload) {
    return payloadRepository.save(payload);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public InjectorContract forceSaveInjectorContract(InjectorContract injectorContract) {
    return injectorContractRepository.save(injectorContract);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Inject forceSaveInject(Inject inject) {
    return injectRepository.save(inject);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Agent forceSaveAgent(Agent agent) {
    return agentRepository.save(agent);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Injector forceSaveInjector(Injector injector) {
    return injectorRepository.save(injector);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Endpoint forceSaveEndpoint(Endpoint endpoint) {
    return endpointRepository.save(endpoint);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Finding forceSaveFinding(Finding finding) {
    return findingRepository.save(finding);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Asset forceSaveAsset(Asset asset) {
    return assetRepository.save(asset);
  }
}
