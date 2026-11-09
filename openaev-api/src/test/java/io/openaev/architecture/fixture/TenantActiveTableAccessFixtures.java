package io.openaev.architecture.fixture;

import io.openaev.aop.PlatformWrite;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Setting;
import io.openaev.database.repository.SettingRepository;
import io.openaev.database.repository.autonomous.AutonomousObjectiveTemplateRepository;
import io.openaev.database.repository.autonomous.AutonomousRunRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Stream;
import org.springframework.transaction.annotation.Transactional;

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
}
