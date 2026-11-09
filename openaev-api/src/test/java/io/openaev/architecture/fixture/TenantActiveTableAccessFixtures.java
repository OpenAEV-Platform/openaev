package io.openaev.architecture.fixture;

import io.openaev.aop.PlatformWrite;
import io.openaev.config.TenantUriUtils;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.InjectorContractId;
import io.openaev.database.model.Payload;
import io.openaev.database.model.Setting;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.database.repository.SettingRepository;
import io.openaev.database.repository.autonomous.AutonomousObjectiveTemplateRepository;
import io.openaev.database.repository.autonomous.AutonomousRunRepository;
import io.openaev.rest.injector_contract.InjectorContractService;
import io.openaev.utils.TxCtxScopeUtils;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

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

  /** Writes through the settings repository with nothing that marks the transaction. */
  public static class UnmarkedPlatformRowWriter {

    private final SettingRepository settingRepository;

    public UnmarkedPlatformRowWriter(SettingRepository settingRepository) {
      this.settingRepository = settingRepository;
    }

    public void entry(Setting setting) {
      write(setting);
    }

    private void write(Setting setting) {
      settingRepository.save(setting);
    }
  }

  /** The writer is private and reached from a method that carries the platform-write marker. */
  public static class PlatformRowWriterUnderTheAnnotation {

    private final SettingRepository settingRepository;

    public PlatformRowWriterUnderTheAnnotation(SettingRepository settingRepository) {
      this.settingRepository = settingRepository;
    }

    @PlatformWrite
    @Transactional
    public void entry(TxCtx ctx, Setting setting) {
      write(setting);
    }

    private void write(Setting setting) {
      settingRepository.deleteAll(java.util.List.of(setting));
    }
  }

  /** The write happens in a lambda handed to the platform-write primitive. */
  public static class PlatformRowWriterUnderThePrimitive {

    private final SettingRepository settingRepository;
    private final TenantScopedTransaction tenantScopedTransaction;

    public PlatformRowWriterUnderThePrimitive(
        SettingRepository settingRepository, TenantScopedTransaction tenantScopedTransaction) {
      this.settingRepository = settingRepository;
      this.tenantScopedTransaction = tenantScopedTransaction;
    }

    public void entry(TxCtx ctx, Setting setting) {
      tenantScopedTransaction.executeAsPlatformWrite(ctx, () -> settingRepository.save(setting));
    }
  }

  /** One of the two callers of the writer is not marked. */
  public static class PlatformRowWriterWithAnUnmarkedCaller {

    private final SettingRepository settingRepository;

    public PlatformRowWriterWithAnUnmarkedCaller(SettingRepository settingRepository) {
      this.settingRepository = settingRepository;
    }

    @PlatformWrite
    @Transactional
    public void markedEntry(TxCtx ctx, Setting setting) {
      write(setting);
    }

    public void unmarkedEntry(Setting setting) {
      write(setting);
    }

    private void write(Setting setting) {
      settingRepository.save(setting);
    }
  }

  /** Opens the platform-write callback, then writes directly once the primitive has returned. */
  public static class PlatformRowWriterAfterThePrimitiveReturned {

    private final SettingRepository settingRepository;
    private final TenantScopedTransaction tenantScopedTransaction;

    public PlatformRowWriterAfterThePrimitiveReturned(
        SettingRepository settingRepository, TenantScopedTransaction tenantScopedTransaction) {
      this.settingRepository = settingRepository;
      this.tenantScopedTransaction = tenantScopedTransaction;
    }

    public void entry(TxCtx ctx, Setting setting) {
      tenantScopedTransaction.executeAsPlatformWrite(ctx, () -> settingRepository.save(setting));
      settingRepository.save(setting);
    }
  }

  /** A class that opens the platform-write primitive in one method and has an unrelated lambda. */
  public static class UnrelatedLambdaInAClassThatOpensThePrimitive {

    private final SettingRepository settingRepository;
    private final TenantScopedTransaction tenantScopedTransaction;

    public UnrelatedLambdaInAClassThatOpensThePrimitive(
        SettingRepository settingRepository, TenantScopedTransaction tenantScopedTransaction) {
      this.settingRepository = settingRepository;
      this.tenantScopedTransaction = tenantScopedTransaction;
    }

    public void marked(TxCtx ctx, Setting setting) {
      tenantScopedTransaction.executeAsPlatformWrite(ctx, () -> settingRepository.save(setting));
    }

    public void unrelated(Setting setting) {
      Runnable write = () -> settingRepository.save(setting);
      write.run();
    }
  }

  /** The write follows the current-transaction marker in the same method. */
  public static class PlatformRowWriterAfterTheCurrentTransactionMarker {

    private final SettingRepository settingRepository;
    private final TenantScopedTransaction tenantScopedTransaction;

    public PlatformRowWriterAfterTheCurrentTransactionMarker(
        SettingRepository settingRepository, TenantScopedTransaction tenantScopedTransaction) {
      this.settingRepository = settingRepository;
      this.tenantScopedTransaction = tenantScopedTransaction;
    }

    public void entry(Setting setting) {
      tenantScopedTransaction.setPlatformWriteOnCurrentTransaction();
      settingRepository.save(setting);
    }
  }

  /** The write comes BEFORE the current-transaction marker. */
  public static class PlatformRowWriterBeforeTheCurrentTransactionMarker {

    private final SettingRepository settingRepository;
    private final TenantScopedTransaction tenantScopedTransaction;

    public PlatformRowWriterBeforeTheCurrentTransactionMarker(
        SettingRepository settingRepository, TenantScopedTransaction tenantScopedTransaction) {
      this.settingRepository = settingRepository;
      this.tenantScopedTransaction = tenantScopedTransaction;
    }

    public void entry(Setting setting) {
      settingRepository.save(setting);
      tenantScopedTransaction.setPlatformWriteOnCurrentTransaction();
    }
  }

  /** The annotation is on a method the aspect ignores: no @Transactional. */
  public static class PlatformWriteWithoutTransactional {

    private final SettingRepository settingRepository;

    public PlatformWriteWithoutTransactional(SettingRepository settingRepository) {
      this.settingRepository = settingRepository;
    }

    @PlatformWrite
    public void entry(TxCtx ctx, Setting setting) {
      settingRepository.save(setting);
    }
  }

  /** The annotation is on a transactional method with no TxCtx parameter. */
  public static class PlatformWriteWithoutTxCtx {

    private final SettingRepository settingRepository;

    public PlatformWriteWithoutTxCtx(SettingRepository settingRepository) {
      this.settingRepository = settingRepository;
    }

    @PlatformWrite
    @Transactional
    public void entry(Setting setting) {
      settingRepository.save(setting);
    }
  }

  /** The annotation is honoured: transactional with a TxCtx. */
  public static class PlatformWriteHonoured {

    private final SettingRepository settingRepository;

    public PlatformWriteHonoured(SettingRepository settingRepository) {
      this.settingRepository = settingRepository;
    }

    @PlatformWrite
    @Transactional
    public void entry(TxCtx ctx, Setting setting) {
      settingRepository.save(setting);
    }
  }

  /** Writes a Setting through the entity manager. */
  public static class PlatformRowEntityManagerWriter {

    private final EntityManager entityManager;

    public PlatformRowEntityManagerWriter(EntityManager entityManager) {
      this.entityManager = entityManager;
    }

    public void entry(Setting setting) {
      entityManager.persist(setting);
    }
  }

  /** Writes something that is not a Setting through the entity manager. */
  public static class EntityManagerWriterOfAnotherEntity {

    private final EntityManager entityManager;

    public EntityManagerWriterOfAnotherEntity(EntityManager entityManager) {
      this.entityManager = entityManager;
    }

    public void entry(String other) {
      entityManager.persist(other);
    }
  }

  /** Inherited CrudRepository write that is not named save/delete. */
  public static class PlatformRowInheritedWriter {

    private final SettingRepository settingRepository;

    public PlatformRowInheritedWriter(SettingRepository settingRepository) {
      this.settingRepository = settingRepository;
    }

    public void entry(java.util.List<Setting> settings) {
      settingRepository.saveAll(settings);
    }
  }

  /** Changes a managed Setting with a setter and never saves it: dirty checking writes it. */
  public static class PlatformRowDirtyCheckedWriter {

    public void entry(Setting managed) {
      managed.setValue("changed");
    }
  }

  /** An HQL bulk statement on the table, in a method that never names the entity type. */
  public static class PlatformRowHqlWriter {

    private final EntityManager entityManager;

    public PlatformRowHqlWriter(EntityManager entityManager) {
      this.entityManager = entityManager;
    }

    public int entry(String key) {
      return entityManager
          .createQuery("delete from Setting s where s.key = :key")
          .setParameter("key", key)
          .executeUpdate();
    }
  }

  /** A native statement on the table. */
  public static class PlatformRowNativeWriter {

    private final EntityManager entityManager;

    public PlatformRowNativeWriter(EntityManager entityManager) {
      this.entityManager = entityManager;
    }

    public int entry(String key) {
      return entityManager
          .createNativeQuery("UPDATE parameters SET parameter_value = 'x' WHERE parameter_key = ?1")
          .setParameter(1, key)
          .executeUpdate();
    }
  }

  /** A JDBC statement on the table, the table name split across a concatenation. */
  public static class PlatformRowJdbcWriter {

    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    public PlatformRowJdbcWriter(org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
      this.jdbcTemplate = jdbcTemplate;
    }

    public int entry(String key) {
      return jdbcTemplate.update("DELETE FROM " + "parameters WHERE parameter_key = ?", key);
    }
  }

  /** A Hibernate session write of a Setting. */
  public static class PlatformRowSessionWriter {

    private final org.hibernate.Session session;

    public PlatformRowSessionWriter(org.hibernate.Session session) {
      this.session = session;
    }

    public void entry(Setting setting) {
      session.merge(setting);
    }
  }

  /** Statements and reads that do not touch the table. */
  public static class StatementsThatAreNotPlatformRowWriters {

    private final EntityManager entityManager;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    public StatementsThatAreNotPlatformRowWriters(
        EntityManager entityManager, org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
      this.entityManager = entityManager;
      this.jdbcTemplate = jdbcTemplate;
    }

    public int otherTable(String id) {
      return entityManager
          .createQuery("delete from Exercise e where e.id = :id")
          .setParameter("id", id)
          .executeUpdate();
    }

    public int otherTableByJdbc(String id) {
      return jdbcTemplate.update(
          "UPDATE exercises SET exercise_name = 'x' WHERE exercise_id = ?", id);
    }

    public Object readsTheTable() {
      return entityManager
          .createNativeQuery("SELECT parameter_value FROM parameters")
          .getResultList();
    }

    public String readsASetting(Setting setting) {
      return setting.getValue();
    }
  }

  /** Writes a platform row through each new shape, every one inside the platform-write callback. */
  public static class PlatformRowWritersUnderThePrimitive {

    private final EntityManager entityManager;
    private final TenantScopedTransaction tenantScopedTransaction;

    public PlatformRowWritersUnderThePrimitive(
        EntityManager entityManager, TenantScopedTransaction tenantScopedTransaction) {
      this.entityManager = entityManager;
      this.tenantScopedTransaction = tenantScopedTransaction;
    }

    public void entry(TxCtx ctx, Setting managed) {
      tenantScopedTransaction.executeAsPlatformWrite(
          ctx,
          () -> {
            managed.setValue("changed");
            return entityManager
                .createNativeQuery("UPDATE parameters SET parameter_value = 'x'")
                .executeUpdate();
          });
    }
  }

  /** Calls a service method that saves settings, from a method that marks nothing. */
  public static class UnmarkedCallerOfThePlatformSettingsService {

    private final io.openaev.service.PlatformSettingsService platformSettingsService;

    public UnmarkedCallerOfThePlatformSettingsService(
        io.openaev.service.PlatformSettingsService platformSettingsService) {
      this.platformSettingsService = platformSettingsService;
    }

    public void entry(io.openaev.rest.settings.form.SettingsSessionsUpdateInput input) {
      platformSettingsService.updateSettingsSessions(input);
    }
  }

  /** The read is private and reached from a job method that sets no scope. */
  public static class TenantRowReaderFromAnUnscopedRoot {

    private final SettingRepository settingRepository;

    public TenantRowReaderFromAnUnscopedRoot(SettingRepository settingRepository) {
      this.settingRepository = settingRepository;
    }

    public void job() {
      read();
    }

    private void read() {
      settingRepository.findByKeyAndTenantId("k", "t");
    }
  }

  /** The aspect scopes a transactional method that takes a TxCtx. */
  public static class TenantRowReaderUnderATxCtx {

    private final SettingRepository settingRepository;

    public TenantRowReaderUnderATxCtx(SettingRepository settingRepository) {
      this.settingRepository = settingRepository;
    }

    @Transactional(readOnly = true)
    public void entry(TxCtx ctx) {
      settingRepository.findAllByTenantId("t");
    }
  }

  /** The read is in the callback of the primitive. */
  public static class TenantRowReaderInThePrimitiveCallback {

    private final SettingRepository settingRepository;
    private final TenantScopedTransaction tenantScopedTransaction;

    public TenantRowReaderInThePrimitiveCallback(
        SettingRepository settingRepository, TenantScopedTransaction tenantScopedTransaction) {
      this.settingRepository = settingRepository;
      this.tenantScopedTransaction = tenantScopedTransaction;
    }

    public void job(TxCtx ctx) {
      tenantScopedTransaction.execute(ctx, () -> settingRepository.findAllByTenantId("t"));
    }
  }

  /** The callback is handed to a helper that runs it inside the primitive. */
  public static class TenantRowReaderInAHelperCallback {

    private final SettingRepository settingRepository;
    private final TenantScopedTransaction tenantScopedTransaction;

    public TenantRowReaderInAHelperCallback(
        SettingRepository settingRepository, TenantScopedTransaction tenantScopedTransaction) {
      this.settingRepository = settingRepository;
      this.tenantScopedTransaction = tenantScopedTransaction;
    }

    public void job(TxCtx ctx) {
      inTenant(ctx, () -> settingRepository.findAllByTenantId("t"));
    }

    private void inTenant(TxCtx ctx, Runnable work) {
      tenantScopedTransaction.execute(ctx, work);
    }
  }

  /** The read follows the primitive's return, outside its callback. */
  public static class TenantRowReaderAfterThePrimitiveReturned {

    private final SettingRepository settingRepository;
    private final TenantScopedTransaction tenantScopedTransaction;

    public TenantRowReaderAfterThePrimitiveReturned(
        SettingRepository settingRepository, TenantScopedTransaction tenantScopedTransaction) {
      this.settingRepository = settingRepository;
      this.tenantScopedTransaction = tenantScopedTransaction;
    }

    public void job(TxCtx ctx) {
      tenantScopedTransaction.execute(ctx, () -> settingRepository.findAllByTenantId("t"));
      settingRepository.findAllByTenantId("t");
    }
  }

  /** Reads the platform row only. */
  public static class PlatformRowReaderOnly {

    private final SettingRepository settingRepository;

    public PlatformRowReaderOnly(SettingRepository settingRepository) {
      this.settingRepository = settingRepository;
    }

    public void job() {
      settingRepository.findByKeyAndTenantIsNull("k");
    }
  }

  /** The same repository write is made twice, once before and once after the marker. */
  public static class PlatformRowWriterBeforeAndAfterTheCurrentTransactionMarker {

    private final SettingRepository settingRepository;
    private final TenantScopedTransaction tenantScopedTransaction;

    public PlatformRowWriterBeforeAndAfterTheCurrentTransactionMarker(
        SettingRepository settingRepository, TenantScopedTransaction tenantScopedTransaction) {
      this.settingRepository = settingRepository;
      this.tenantScopedTransaction = tenantScopedTransaction;
    }

    public void entry(Setting setting) {
      settingRepository.save(setting);
      tenantScopedTransaction.setPlatformWriteOnCurrentTransaction();
      settingRepository.save(setting);
    }
  }

  /** Deletes a platform row through a method reference, reached from no marker. */
  public static class PlatformRowMethodReferenceDeleter {

    private final SettingRepository settingRepository;

    public PlatformRowMethodReferenceDeleter(SettingRepository settingRepository) {
      this.settingRepository = settingRepository;
    }

    public void entry(String key) {
      settingRepository.findByKeyAndTenantIsNull(key).ifPresent(settingRepository::delete);
    }
  }

  /** Saves settings through a method reference, reached from no marker. */
  public static class PlatformRowMethodReferenceSaver {

    private final SettingRepository settingRepository;

    public PlatformRowMethodReferenceSaver(SettingRepository settingRepository) {
      this.settingRepository = settingRepository;
    }

    public void entry(List<Setting> settings) {
      settings.forEach(settingRepository::save);
    }
  }

  /** Changes a managed setting through a setter reference, reached from no marker. */
  public static class PlatformRowSetterMethodReference {

    public void entry(Setting managed) {
      Stream.of("changed").forEach(managed::setValue);
    }
  }

  /** The method references are created in the callback handed to the platform-write primitive. */
  public static class PlatformRowMethodReferencesUnderThePrimitive {

    private final SettingRepository settingRepository;
    private final TenantScopedTransaction tenantScopedTransaction;

    public PlatformRowMethodReferencesUnderThePrimitive(
        SettingRepository settingRepository, TenantScopedTransaction tenantScopedTransaction) {
      this.settingRepository = settingRepository;
      this.tenantScopedTransaction = tenantScopedTransaction;
    }

    public void entry(TxCtx ctx, String key, List<Setting> settings) {
      tenantScopedTransaction.executeAsPlatformWrite(
          ctx,
          () -> {
            settingRepository.findByKeyAndTenantIsNull(key).ifPresent(settingRepository::delete);
            settings.forEach(settingRepository::save);
          });
    }
  }

  /** Builds a setting through a constructor reference: not a write. */
  public static class SettingConstructorReference {

    public BiFunction<String, String, Setting> factory() {
      return Setting::new;
    }
  }

  /** Reads tenant rows through a method reference, from a root that sets no scope. */
  public static class TenantRowReaderByMethodReference {

    private final SettingRepository settingRepository;

    public TenantRowReaderByMethodReference(SettingRepository settingRepository) {
      this.settingRepository = settingRepository;
    }

    public void job(List<String> tenantIds) {
      tenantIds.stream().map(settingRepository::findAllByTenantId).toList();
    }
  }

  /** Writes tenant rows only: the row it saves is looked up by tenant or built for one. */
  public static class TenantRowWriterThatStaysTenantOnly {

    private final SettingRepository settingRepository;

    public TenantRowWriterThatStaysTenantOnly(SettingRepository settingRepository) {
      this.settingRepository = settingRepository;
    }

    public void write(Tenant tenant, String key, String value) {
      Setting setting =
          settingRepository
              .findByKeyAndTenantId(key, tenant.getId())
              .orElseGet(
                  () -> {
                    Setting created = new Setting(key, value);
                    created.setTenant(tenant);
                    return created;
                  });
      setting.setValue(value);
      settingRepository.save(setting);
    }
  }

  /** Builds a new row and never attributes it to a tenant: that row is a platform row. */
  public static class TenantRowWriterBuildingARowWithoutATenant {

    private final SettingRepository settingRepository;

    public TenantRowWriterBuildingARowWithoutATenant(SettingRepository settingRepository) {
      this.settingRepository = settingRepository;
    }

    public void write(Tenant tenant, String key, String value) {
      Setting setting =
          settingRepository
              .findByKeyAndTenantId(key, tenant.getId())
              .orElseGet(() -> new Setting(key, value));
      settingRepository.save(setting);
    }
  }

  /** Looks the row up by the platform key before saving it. */
  public static class TenantRowWriterLookingUpThePlatformRow {

    private final SettingRepository settingRepository;

    public TenantRowWriterLookingUpThePlatformRow(SettingRepository settingRepository) {
      this.settingRepository = settingRepository;
    }

    public void write(String key, String value) {
      settingRepository
          .findByKeyAndTenantIsNull(key)
          .ifPresent(
              setting -> {
                setting.setValue(value);
                settingRepository.save(setting);
              });
    }
  }

  /** Deletes by key alone, which removes the platform row and every tenant's row of that key. */
  public static class TenantRowWriterDeletingByKeyOnly {

    private final SettingRepository settingRepository;

    public TenantRowWriterDeletingByKeyOnly(SettingRepository settingRepository) {
      this.settingRepository = settingRepository;
    }

    public void write(List<String> keys) {
      settingRepository.deleteByKeyIn(keys);
    }
  }

  /** Two overloads of one name: the three-argument one writes a tenant row, the other does not. */
  public static class TenantRowWriterWithAPlatformOverload {

    private final SettingRepository settingRepository;

    public TenantRowWriterWithAPlatformOverload(SettingRepository settingRepository) {
      this.settingRepository = settingRepository;
    }

    public void upsert(Tenant tenant, String key, String value) {
      Setting setting = new Setting(key, value);
      setting.setTenant(tenant);
      settingRepository.save(setting);
    }

    public void upsert(String key, String value) {
      settingRepository.save(new Setting(key, value));
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

  /** Two methods of one class making the same bare lookup, one of them reviewed. */
  public static class ReviewedAndUnreviewedLookup {
    private final InjectorContractRepository repository;

    public ReviewedAndUnreviewedLookup(InjectorContractRepository repository) {
      this.repository = repository;
    }

    /** The reviewed method; its lookup sits in a lambda, which counts as the method. */
    public Optional<InjectorContract> approvedLookup(String id) {
      return Optional.of(id).flatMap(value -> repository.findById(value));
    }

    public Optional<InjectorContract> newLookup(String id) {
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

  // -- injectors_contracts: the pin an allowlist reason relies on --

  /** An endpoint shape that pins the scope, then looks the contract up. */
  public static class PinBeforeLookup {
    private final InjectorContractService service;

    public PinBeforeLookup(InjectorContractService service) {
      this.service = service;
    }

    public InjectorContract find(TxCtx ctx, String id) {
      TxCtxScopeUtils.requireSingleTenant(ctx);
      return service.injectorContract(id);
    }
  }

  /** The pin is there, but after the lookup it was meant to guard. */
  public static class PinAfterLookup {
    private final InjectorContractService service;

    public PinAfterLookup(InjectorContractService service) {
      this.service = service;
    }

    public InjectorContract find(TxCtx ctx, String id) {
      InjectorContract contract = service.injectorContract(id);
      TxCtxScopeUtils.requireSingleTenant(ctx);
      return contract;
    }
  }

  /** No pin at all: the lookup is the only thing the endpoint does. */
  public static class LookupWithoutPin {
    private final InjectorContractService service;

    public LookupWithoutPin(InjectorContractService service) {
      this.service = service;
    }

    public InjectorContract find(TxCtx ctx, String id) {
      return service.injectorContract(id);
    }
  }

  /** The pin sits in a sibling method, not in the one that does the lookup. */
  public static class PinInAnotherMethod {
    private final InjectorContractService service;

    public PinInAnotherMethod(InjectorContractService service) {
      this.service = service;
    }

    public void pin(TxCtx ctx) {
      TxCtxScopeUtils.requireSingleTenant(ctx);
    }

    public InjectorContract find(TxCtx ctx, String id) {
      return service.injectorContract(id);
    }
  }

  /** The pin is behind a condition: a caller with no context skips it and reaches the lookup. */
  public static class PinInsideIf {
    private final InjectorContractService service;

    public PinInsideIf(InjectorContractService service) {
      this.service = service;
    }

    public InjectorContract find(TxCtx ctx, String id) {
      if (ctx != null) {
        TxCtxScopeUtils.requireSingleTenant(ctx);
      }
      return service.injectorContract(id);
    }
  }

  /** The pin is inside a try block whose handler swallows its refusal. */
  public static class PinInsideTry {
    private final InjectorContractService service;

    public PinInsideTry(InjectorContractService service) {
      this.service = service;
    }

    public InjectorContract find(TxCtx ctx, String id) {
      try {
        TxCtxScopeUtils.requireSingleTenant(ctx);
      } catch (RuntimeException ignored) {
        // the refusal is dropped and the lookup runs anyway
      }
      return service.injectorContract(id);
    }
  }

  /** The pin is inside a lambda that is never run before the lookup. */
  public static class PinInsideLambda {
    private final InjectorContractService service;

    public PinInsideLambda(InjectorContractService service) {
      this.service = service;
    }

    public InjectorContract find(TxCtx ctx, String id) {
      Runnable pin = () -> TxCtxScopeUtils.requireSingleTenant(ctx);
      return service.injectorContract(id);
    }
  }

  /** The pin runs first on every execution, and the branch that follows it is the caller's own. */
  public static class PinBeforeABranch {
    private final InjectorContractService service;

    public PinBeforeABranch(InjectorContractService service) {
      this.service = service;
    }

    public InjectorContract find(TxCtx ctx, String id) {
      TxCtxScopeUtils.requireSingleTenant(ctx);
      if (id == null) {
        return null;
      }
      return service.injectorContract(id);
    }
  }

  // -- injectors_contracts: the callers and routes an allowlist reason relies on --

  /** A method and two callers of it, to tell a listed caller from an unlisted one. */
  public static class CallerShapes {
    public void claimed() {}

    public void listed() {
      claimed();
    }

    public void unlisted() {
      claimed();
    }
  }

  /** Endpoints mapped on the tenant route only, on both routes, and not mapped at all. */
  public static class MethodRoutes {
    @Transactional
    @GetMapping(TenantUriUtils.TENANT_PREFIX + "/things")
    public void tenantOnly(TxCtx ctx) {}

    @Transactional
    @GetMapping({"/api/things", TenantUriUtils.TENANT_PREFIX + "/things"})
    public void bothRoutes(TxCtx ctx) {}

    public void unmapped() {}
  }

  /** The route comes from the class mapping: tenant-prefixed. */
  @RequestMapping(TenantUriUtils.TENANT_PREFIX + "/things")
  public static class TenantPrefixedClassRoute {
    @Transactional
    @GetMapping("/{id}/export")
    public void export(TxCtx ctx) {}
  }

  /** The route comes from the class mapping: the short route, with no tenant in the path. */
  @RequestMapping("/api/things")
  public static class ShortClassRoute {
    @Transactional
    @GetMapping("/{id}/export")
    public void export(TxCtx ctx) {}
  }

  // -- injectors_contracts: classes allowlisted for their methods, and the scope those run under --

  /**
   * Callers of a method that run under a scope: a TxCtx parameter, and a scope opened around it.
   */
  public static class ScopedCallers {
    private final TenantScopedTransaction tenantTx;

    public ScopedCallers(TenantScopedTransaction tenantTx) {
      this.tenantTx = tenantTx;
    }

    public void target() {}

    public void withTxCtx(TxCtx ctx) {
      target();
    }

    public void openedAroundLambda() {
      tenantTx.execute(TxCtx.forTenant("tenant"), () -> target());
    }
  }

  /** Callers of a method that run under no scope, each in its own way. */
  public static class UnscopedCallers {
    private final TenantScopedTransaction tenantTx;

    public UnscopedCallers(TenantScopedTransaction tenantTx) {
      this.tenantTx = tenantTx;
    }

    public void target() {}

    public void withoutTxCtx() {
      target();
    }

    public void outsideTheLambda() {
      target();
      tenantTx.execute(TxCtx.forTenant("tenant"), () -> {});
    }

    public void lambdaWithNoScope() {
      Runnable work = () -> target();
      work.run();
    }
  }

  /** One method reaches the contract repository and is listed, another is not. */
  public static class ListedAndUnlistedReach {
    private final InjectorContractRepository repository;

    public ListedAndUnlistedReach(InjectorContractRepository repository) {
      this.repository = repository;
    }

    public long listed() {
      return repository.count();
    }

    public long unlisted() {
      return repository.count();
    }
  }

  /** A public method takes a TxCtx and reaches the repository through a private one. */
  public static class TxCtxEntryPoint {
    private final InjectorContractRepository repository;

    public TxCtxEntryPoint(InjectorContractRepository repository) {
      this.repository = repository;
    }

    public long entry(TxCtx ctx) {
      return helper();
    }

    private long helper() {
      return repository.count();
    }
  }

  /** A public method with no TxCtx reaches the repository through a private one. */
  public static class UnscopedEntryPoint {
    private final InjectorContractRepository repository;

    public UnscopedEntryPoint(InjectorContractRepository repository) {
      this.repository = repository;
    }

    public long entry() {
      return helper();
    }

    private long helper() {
      return repository.count();
    }
  }

  /** The repository is reached by a method that nothing non-private leads to. */
  public static class NoEntryPoint {
    private final InjectorContractRepository repository;

    public NoEntryPoint(InjectorContractRepository repository) {
      this.repository = repository;
    }

    private long helper() {
      return repository.count();
    }
  }
}
