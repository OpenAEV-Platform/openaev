package io.openaev.xtm.counterpart;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Resolves the counterparts of our objects in every connected XTM product, batched and cached.
 *
 * <p>A key is looked up once per tenant, user, product and kind for {@link #CACHE_TTL}, a miss
 * included, so a list of fifty rows costs one call per product. A product that cannot be reached
 * answers "unavailable" for the batch and is never cached, so the next view retries.
 *
 * <p>Segregation: a counterpart read with the connection's service credential ({@link
 * CounterpartProvider#readsAsCaller()} false) is reduced to the pivot link, its name replaced by
 * the caller's own key and its type by the kind, its facts dropped: the caller's rights in the
 * other product are unknown, so nothing that product holds is shown. The cache is per user, so one
 * user's answer never reaches another.
 */
@Slf4j
@Service
public class CounterpartService {

  static final Duration CACHE_TTL = Duration.ofMinutes(5);
  static final int MAX_KEYS = 200;

  /** What a product answered for one key. */
  public enum Status {
    MATCH,
    NO_MATCH,
    NOT_CONNECTED,
    UNAVAILABLE
  }

  /** The answer for one key in one product. */
  public record Resolution(String key, String product, Status status, Counterpart counterpart) {}

  /** The connection state of one product for a tenant; never carries a credential. */
  public record Connection(String product, boolean connected, String url) {}

  private final List<CounterpartProvider> providers;
  private final Cache<String, Optional<Counterpart>> cache;

  public CounterpartService(List<CounterpartProvider> providers) {
    this.providers = List.copyOf(providers);
    this.cache = Caffeine.newBuilder().expireAfterWrite(CACHE_TTL).maximumSize(50_000).build();
  }

  public List<Connection> connections(String tenantId) {
    return providers.stream()
        .map(
            p -> {
              Optional<String> url = p.connectedUrl(tenantId);
              return new Connection(p.product(), url.isPresent(), url.orElse(null));
            })
        .toList();
  }

  public List<Resolution> resolve(
      String tenantId, String userId, CounterpartKind kind, Collection<String> keys) {
    Set<String> normalised = new LinkedHashSet<>();
    for (String key : keys) {
      String n = kind.normalise(key);
      if (n != null) {
        normalised.add(n);
      }
      if (normalised.size() >= MAX_KEYS) {
        break;
      }
    }
    List<Resolution> out = new ArrayList<>();
    for (CounterpartProvider provider : providers) {
      if (!provider.supports(kind)) {
        continue;
      }
      if (provider.connectedUrl(tenantId).isEmpty()) {
        normalised.forEach(
            k -> out.add(new Resolution(k, provider.product(), Status.NOT_CONNECTED, null)));
        continue;
      }
      out.addAll(resolveWith(provider, tenantId, userId, kind, normalised));
    }
    return out;
  }

  private List<Resolution> resolveWith(
      CounterpartProvider provider,
      String tenantId,
      String userId,
      CounterpartKind kind,
      Set<String> keys) {
    Map<String, Optional<Counterpart>> known = new LinkedHashMap<>();
    List<String> missing = new ArrayList<>();
    for (String key : keys) {
      Optional<Counterpart> hit =
          cache.getIfPresent(cacheKey(tenantId, userId, provider, kind, key));
      if (hit != null) {
        known.put(key, hit);
      } else {
        missing.add(key);
      }
    }
    boolean unavailable = false;
    if (!missing.isEmpty()) {
      try {
        Map<String, Counterpart> found = provider.resolve(tenantId, kind, missing);
        for (String key : missing) {
          Optional<Counterpart> value =
              Optional.ofNullable(found.get(key)).map(c -> visibleTo(provider, kind, c));
          cache.put(cacheKey(tenantId, userId, provider, kind, key), value);
          known.put(key, value);
        }
      } catch (CounterpartUnavailableException | RuntimeException e) {
        log.warn(
            "Counterpart lookup in {} failed for tenant {} ({}): {}",
            provider.product(),
            tenantId,
            kind,
            e.getMessage());
        unavailable = true;
      }
    }
    List<Resolution> out = new ArrayList<>();
    for (String key : keys) {
      Optional<Counterpart> value = known.get(key);
      if (value == null) {
        out.add(
            new Resolution(
                key, provider.product(), unavailable ? Status.UNAVAILABLE : Status.NO_MATCH, null));
      } else if (value.isPresent()) {
        out.add(new Resolution(key, provider.product(), Status.MATCH, value.get()));
      } else {
        out.add(new Resolution(key, provider.product(), Status.NO_MATCH, null));
      }
    }
    return out;
  }

  /** The counterpart as the caller may see it: the link alone unless read as the caller. */
  static Counterpart visibleTo(
      CounterpartProvider provider, CounterpartKind kind, Counterpart counterpart) {
    if (provider.readsAsCaller()) {
      return counterpart;
    }
    return new Counterpart(
        counterpart.key(),
        counterpart.product(),
        counterpart.entityId(),
        kind.name().toLowerCase(java.util.Locale.ROOT),
        counterpart.key(),
        counterpart.url());
  }

  private static String cacheKey(
      String tenantId,
      String userId,
      CounterpartProvider provider,
      CounterpartKind kind,
      String key) {
    return tenantId + '|' + userId + '|' + provider.product() + '|' + kind + '|' + key;
  }
}
