package io.openaev.utils.helpers;

import io.openaev.context.TenantContext;
import io.openaev.database.model.*;
import io.openaev.database.repository.*;
import io.openaev.utils.fixtures.*;
import io.openaev.utils.fixtures.composers.*;
import io.openaev.utilstest.DefaultTenantScopeTestListener;
import jakarta.persistence.EntityManager;
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
  private final EntityManager entityManager;

  /**
   * A {@code REQUIRES_NEW} transaction starts without the test's scope, so its reads and writes of
   * an active table see no row: run it in the default tenant's scope, as a request would.
   */
  private void inDefaultScope() {
    DefaultTenantScopeTestListener.setAmbientScope(entityManager, Tenant.DEFAULT_TENANT_UUID);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Inject getPendingInjectWithAssets(
      InjectComposer injectComposer,
      InjectorContractComposer injectorContractComposer,
      EndpointComposer endpointComposer,
      AgentComposer agentComposer,
      InjectStatusComposer injectStatusComposer) {
    inDefaultScope();
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

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public BaseInjectExpectation forceSaveInjectExpectation(BaseInjectExpectation expectation) {
    inDefaultScope();
    return injectExpectationRepository.save(expectation);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Domain forceSaveDomain(Domain domain) {
    inDefaultScope();
    return domainRepository.save(domain);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Payload forceSavePayload(Payload payload) {
    inDefaultScope();
    // The listener no longer stamps an unattributed write; fall back to the ambient tenant when
    // the caller left it unset, mirroring PayloadComposer.
    if (payload.getTenant() == null) {
      payload.setTenant(new Tenant(TenantContext.getCurrentTenant()));
    }
    return payloadRepository.save(payload);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public InjectorContract forceSaveInjectorContract(InjectorContract injectorContract) {
    inDefaultScope();
    return injectorContractRepository.save(injectorContract);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Inject forceSaveInject(Inject inject) {
    inDefaultScope();
    return injectRepository.save(inject);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Agent forceSaveAgent(Agent agent) {
    inDefaultScope();
    return agentRepository.save(agent);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Injector forceSaveInjector(Injector injector) {
    inDefaultScope();
    return injectorRepository.save(injector);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Endpoint forceSaveEndpoint(Endpoint endpoint) {
    inDefaultScope();
    return endpointRepository.save(endpoint);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Finding forceSaveFinding(Finding finding) {
    inDefaultScope();
    return findingRepository.save(finding);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Asset forceSaveAsset(Asset asset) {
    inDefaultScope();
    return assetRepository.save(asset);
  }

  /**
   * Queries findings for a given inject ID in a new independent transaction, so that findings
   * committed by async processing threads are visible even when called from within an outer
   * {@code @Transactional} test method.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public List<Finding> findFindingsByInjectId(String injectId) {
    inDefaultScope();
    return findingRepository.findAllByInjectId(injectId);
  }
}
