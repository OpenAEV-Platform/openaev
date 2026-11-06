package io.openaev.architecture.fixture;

import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.InjectorContractId;
import io.openaev.database.model.Payload;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.database.repository.autonomous.AutonomousObjectiveTemplateRepository;
import io.openaev.database.repository.autonomous.AutonomousRunRepository;
import io.openaev.rest.injector_contract.InjectorContractService;
import java.util.Optional;
import java.util.function.Function;
import org.springframework.data.jpa.domain.Specification;

/** Accessors of a tenant-active table's repository that no access rule allowlists. */
public final class TenantActiveTableAccessFixtures {

  private TenantActiveTableAccessFixtures() {}

  public static class UnreviewedAutonomousRunAccessor {

    private final AutonomousRunRepository autonomousRunRepository;

    public UnreviewedAutonomousRunAccessor(AutonomousRunRepository autonomousRunRepository) {
      this.autonomousRunRepository = autonomousRunRepository;
    }

    public long countRuns() {
      return autonomousRunRepository.count();
    }
  }

  public static class UnreviewedObjectiveTemplateAccessor {

    private final AutonomousObjectiveTemplateRepository objectiveTemplateRepository;

    public UnreviewedObjectiveTemplateAccessor(
        AutonomousObjectiveTemplateRepository objectiveTemplateRepository) {
      this.objectiveTemplateRepository = objectiveTemplateRepository;
    }

    public long countTemplates() {
      return objectiveTemplateRepository.count();
    }
  }

  // -- injectors_contracts: one accessor per family of query the tenantless rule must catch --

  /** A query the repository declares itself, by a bare id. */
  public static class DeclaredBareIdLookup {
    private final InjectorContractRepository repository;

    public DeclaredBareIdLookup(InjectorContractRepository repository) {
      this.repository = repository;
    }

    public Optional<InjectorContract> find(String id) {
      return repository.findById(id);
    }
  }

  /** A declared query keyed on a payload, with no tenant. */
  public static class DeclaredPayloadLookup {
    private final InjectorContractRepository repository;

    public DeclaredPayloadLookup(InjectorContractRepository repository) {
      this.repository = repository;
    }

    public Optional<InjectorContract> find(Payload payload) {
      return repository.findInjectorContractByPayload(payload);
    }
  }

  /** A read by specification, inherited from {@code JpaSpecificationExecutor}. */
  public static class SpecificationRead {
    private final InjectorContractRepository repository;

    public SpecificationRead(InjectorContractRepository repository) {
      this.repository = repository;
    }

    public Iterable<InjectorContract> find(Specification<InjectorContract> specification) {
      return repository.findAll(specification);
    }
  }

  /** A read without argument, inherited from {@code CrudRepository}. */
  public static class ArglessCrudRead {
    private final InjectorContractRepository repository;

    public ArglessCrudRead(InjectorContractRepository repository) {
      this.repository = repository;
    }

    public long count() {
      return repository.count();
    }
  }

  /** A method reference to a bare lookup, which is an access and not a call. */
  public static class BareIdLookupReference {
    private final InjectorContractRepository repository;

    public BareIdLookupReference(InjectorContractRepository repository) {
      this.repository = repository;
    }

    public Function<String, Optional<InjectorContract>> lookup() {
      return repository::findById;
    }
  }

  /** A service method that wraps a bare lookup. */
  public static class ServiceBareIdLookup {
    private final InjectorContractService service;

    public ServiceBareIdLookup(InjectorContractService service) {
      this.service = service;
    }

    public InjectorContract find(String id) {
      return service.injectorContract(id);
    }
  }

  /** Every way of reaching the contracts that does carry a tenant. */
  public static class TenantCarryingAccess {
    private final InjectorContractRepository repository;
    private final InjectorContractService service;

    public TenantCarryingAccess(
        InjectorContractRepository repository, InjectorContractService service) {
      this.repository = repository;
      this.service = service;
    }

    public void access(InjectorContract contract) {
      repository.findByContractIdAndTenant("id", "tenant");
      repository.findById(new InjectorContractId("id", "tenant"));
      repository.save(contract);
      service.injectorContractInTenant("id", "tenant");
    }
  }
}
