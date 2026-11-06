package io.openaev.architecture.fixture;

import io.openaev.aop.PlatformWrite;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Setting;
import io.openaev.database.repository.SettingRepository;
import io.openaev.database.repository.autonomous.AutonomousObjectiveTemplateRepository;
import io.openaev.database.repository.autonomous.AutonomousRunRepository;

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
    public void entry(Setting setting) {
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
    public void markedEntry(Setting setting) {
      write(setting);
    }

    public void unmarkedEntry(Setting setting) {
      write(setting);
    }

    private void write(Setting setting) {
      settingRepository.save(setting);
    }
  }
}
