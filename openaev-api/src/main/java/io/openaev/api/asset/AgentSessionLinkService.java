package io.openaev.api.asset;

import io.openaev.database.model.Agent;
import io.openaev.database.model.AssetCategory;
import io.openaev.database.model.AssetLinkType;
import io.openaev.database.model.AssetRelationSource;
import io.openaev.database.model.Endpoint;
import io.openaev.rest.settings.PreviewFeature;
import io.openaev.service.PreviewFeatureService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Automatic {@code LOGGED_ON} links from what OpenAEV agents report (#9725).
 *
 * <p>An agent reports, at each registration, the OS account it runs as ({@code executed_by_user}):
 * that is the only session data agents send today. When exactly one IDENTITY asset of the
 * endpoint's tenant carries that account name, the link identity -> endpoint is created or its last
 * seen time refreshed, with source {@link AssetRelationSource#AGENT}. The built-in system accounts
 * ({@code root}, {@code nt authority\system}) are never linked, and a name matching several
 * identities is left alone rather than guessed. A link a person removed stays removed ({@link
 * AssetLinkService#reportLink}).
 *
 * <p>Part of the exploration graph preview feature, like the asset links: nothing is written while
 * {@code EXPLORATION_GRAPH} is off.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentSessionLinkService {

  private static final Set<String> SYSTEM_ACCOUNTS =
      Set.of(
          Agent.ADMIN_SYSTEM_UNIX,
          Agent.ADMIN_SYSTEM_WINDOWS.toLowerCase(Locale.ROOT),
          "system",
          "local service",
          "network service");

  private final AssetLinkService assetLinkService;
  private final PreviewFeatureService previewFeatureService;

  @PersistenceContext private EntityManager entityManager;

  /**
   * Records that {@code account} has a session on {@code endpoint} at {@code seenAt}.
   *
   * @return the id of the identity asset linked, or null when nothing was linked
   */
  public String recordSession(Endpoint endpoint, String account, Instant seenAt) {
    if (endpoint == null
        || endpoint.getTenant() == null
        || !previewFeatureService.isFeatureEnabled(PreviewFeature.EXPLORATION_GRAPH)) {
      return null;
    }
    Set<String> names = accountNames(account);
    if (names.isEmpty()) {
      return null;
    }
    List<String> identities =
        entityManager
            .createQuery(
                "SELECT a.id FROM Asset a WHERE a.category = :category"
                    + " AND a.tenant.id = :tenantId AND LOWER(a.name) IN :names AND a.id <> :endpoint",
                String.class)
            .setParameter("category", AssetCategory.IDENTITY)
            .setParameter("tenantId", endpoint.getTenant().getId())
            .setParameter("names", names)
            .setParameter("endpoint", endpoint.getId())
            .setMaxResults(2)
            .getResultList();
    if (identities.size() != 1) {
      if (identities.size() > 1) {
        log.debug("Account {} matches several identity assets, no session link", account);
      }
      return null;
    }
    String identityId = identities.getFirst();
    boolean visible =
        assetLinkService.reportLink(
            identityId,
            endpoint.getId(),
            AssetLinkType.LOGGED_ON,
            AssetRelationSource.AGENT,
            seenAt == null ? Instant.now() : seenAt);
    return visible ? identityId : null;
  }

  /**
   * The names an identity asset may carry for an OS account, lower case: the account as reported
   * and, for {@code DOMAIN\\user} or {@code user@domain}, the bare user. Empty for a system account.
   */
  static Set<String> accountNames(String account) {
    if (account == null || account.isBlank()) {
      return Set.of();
    }
    String full = account.trim().toLowerCase(Locale.ROOT);
    Set<String> names = new LinkedHashSet<>();
    names.add(full);
    int slash = full.lastIndexOf('\\');
    if (slash >= 0 && slash < full.length() - 1) {
      names.add(full.substring(slash + 1));
    }
    int at = full.indexOf('@');
    if (at > 0) {
      names.add(full.substring(0, at));
    }
    names.removeIf(SYSTEM_ACCOUNTS::contains);
    if (SYSTEM_ACCOUNTS.contains(full)) {
      return Set.of();
    }
    return names;
  }
}
