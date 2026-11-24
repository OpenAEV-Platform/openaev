package io.openaev.xtmone;

import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.AgentStatus;
import io.openaev.database.model.BaseConnectorEntity;
import io.openaev.database.repository.AgentRepository;
import io.openaev.database.repository.CollectorRepository;
import io.openaev.database.repository.ExecutorRepository;
import io.openaev.database.repository.InjectorRepository;
import io.openaev.healthcheck.dto.HealthCheck;
import io.openaev.injectors.email.service.ImapService;
import io.openaev.injectors.email.service.SmtpService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The platform health snapshot sent with every registration to XTM One (its Platform Health page,
 * {@code dev-docs/platform-health.md} section 4.4 of XTM One). XTM One reads it, bounded, and turns
 * it into findings: an unavailable mail service, an external injector, collector or executor that
 * went silent, and a large share of offline agents.
 *
 * <p>Format version {@link #SCHEMA_VERSION}, every key optional:
 *
 * <pre>
 * {"schema_version": 1,
 *  "healthchecks": [{"type": "smtp", "status": "ERROR", "count": 1}],
 *  "integrations": [{"id", "name", "kind": "injector" | "collector" | "executor", "updated_at"}],
 *  "agents": {"total": int, "offline": int}}
 * </pre>
 *
 * <p>Counts and heartbeats only: no tenant name, no asset, no finding content leaves the platform.
 * Built-in integrations are left out, their heartbeat is the backend's own. The reads span every
 * tenant ({@link TxCtx#allTenants()}): the registration is platform-wide. A snapshot that cannot be
 * read is {@code null}, and the registration goes out without it: it never blocks the heartbeat.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class XtmOneHealthSnapshot {

  /** The snapshot format XTM One reads ({@code SCHEMA_VERSION} of its snapshots module). */
  public static final int SCHEMA_VERSION = 1;

  /** Integrations sent at most, as XTM One keeps at most this many. */
  static final int MAX_INTEGRATIONS = 500;

  private final TenantScopedTransaction tenantTx;
  private final InjectorRepository injectorRepository;
  private final CollectorRepository collectorRepository;
  private final ExecutorRepository executorRepository;
  private final AgentRepository agentRepository;
  private final SmtpService smtpService;
  private final ImapService imapService;

  @Value("${openaev.listener.smtp.enabled:#{true}}")
  private boolean smtpEnabled;

  @Value("${openaev.mail.imap.enabled:false}")
  private boolean imapEnabled;

  /** The snapshot, or {@code null} when it cannot be read. Never throws. */
  public Map<String, Object> build() {
    try {
      Map<String, Object> snapshot = new LinkedHashMap<>();
      snapshot.put("schema_version", SCHEMA_VERSION);
      snapshot.put("healthchecks", healthchecks());
      tenantTx.execute(
          TxCtx.allTenants(),
          () -> {
            snapshot.put("integrations", integrations());
            Map<String, Object> agents = new LinkedHashMap<>();
            agents.put("total", agentRepository.count());
            agents.put("offline", agentRepository.countByStatus(AgentStatus.INACTIVE));
            snapshot.put("agents", agents);
            return null;
          });
      return snapshot;
    } catch (Exception e) {
      log.warn("[XTM One] Health snapshot not read, the registration goes without it", e);
      return null;
    }
  }

  /**
   * Platform-level checks, as the inject health checks raise them: SMTP an error, IMAP a warning.
   */
  List<Map<String, Object>> healthchecks() {
    List<Map<String, Object>> checks = new ArrayList<>();
    if (smtpEnabled && !smtpService.isServiceAvailable()) {
      checks.add(check(HealthCheck.Type.SMTP, HealthCheck.Status.ERROR));
    }
    if (imapEnabled && !imapService.isServiceAvailable()) {
      checks.add(check(HealthCheck.Type.IMAP, HealthCheck.Status.WARNING));
    }
    return checks;
  }

  private static Map<String, Object> check(HealthCheck.Type type, HealthCheck.Status status) {
    Map<String, Object> check = new LinkedHashMap<>();
    check.put("type", type.getValue());
    check.put("status", status.name());
    check.put("count", 1);
    return check;
  }

  /**
   * External integrations with their last heartbeat, one entry per id (an integration registered in
   * several tenants keeps its newest heartbeat), bounded at {@link #MAX_INTEGRATIONS}.
   */
  List<Map<String, Object>> integrations() {
    Map<String, Map<String, Object>> byKey = new LinkedHashMap<>();
    collect(byKey, "injector", injectorRepository.findAll(), i -> i.getUpdatedAt());
    collect(byKey, "collector", collectorRepository.findAll(), c -> c.getUpdatedAt());
    collect(byKey, "executor", executorRepository.findAll(), e -> e.getUpdatedAt());
    return byKey.values().stream().limit(MAX_INTEGRATIONS).toList();
  }

  private static <T extends BaseConnectorEntity> void collect(
      Map<String, Map<String, Object>> byKey,
      String kind,
      Iterable<T> entities,
      Function<T, Instant> heartbeat) {
    StreamSupport.stream(entities.spliterator(), false)
        .filter(BaseConnectorEntity::isExternal)
        .filter(entity -> entity.getId() != null)
        .forEach(
            entity -> {
              String key = kind + ":" + entity.getId();
              Instant updatedAt = heartbeat.apply(entity);
              Map<String, Object> known = byKey.get(key);
              if (known != null && !isNewer(updatedAt, (String) known.get("updated_at"))) {
                return;
              }
              Map<String, Object> entry = new HashMap<>();
              entry.put("id", entity.getId());
              entry.put("name", entity.getName() != null ? entity.getName() : entity.getId());
              entry.put("kind", kind);
              if (updatedAt != null) {
                entry.put("updated_at", updatedAt.toString());
              }
              byKey.put(key, entry);
            });
  }

  private static boolean isNewer(Instant candidate, String known) {
    return candidate != null && (known == null || candidate.isAfter(Instant.parse(known)));
  }
}
