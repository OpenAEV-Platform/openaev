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
 * <p>A key is looked up once per tenant, product and kind for {@link #CACHE_TTL}, a miss included,
 * so a list of fifty rows costs one call per product. A product that cannot be reached answers
 * "unavailable" for the batch and is never cached, so the next view retries.
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

  public List<Resolution> resolve(String tenantId, CounterpartKind kind, Collection<String> keys) {
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
      out.addAll(resolveWith(provider, tenantId, kind, normalised));
    }
    return out;
  }

  private List<Resolution> resolveWith(
      CounterpartProvider provider, String tenantId, CounterpartKind kind, Set<String> keys) {
    Map<String, Optional<Counterpart>> known = new LinkedHashMap<>();
    List<String> missing = new ArrayList<>();
    for (String key : keys) {
      Optional<Counterpart> hit = cache.getIfPresent(cacheKey(tenantId, provider, kind, key));
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
          Optional<Counterpart> value = Optional.ofNullable(found.get(key));
          cache.put(cacheKey(tenantId, provider, kind, key), value);
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

  private static String cacheKey(
      String tenantId, CounterpartProvider provider, CounterpartKind kind, String key) {
    return tenantId + '|' + provider.product() + '|' + kind + '|' + key;
  }
}
