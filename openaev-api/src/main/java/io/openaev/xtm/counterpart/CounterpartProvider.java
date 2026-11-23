package io.openaev.xtm.counterpart;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * One connected XTM product that can tell which of our objects it also knows.
 *
 * <p>OpenCTI is the first implementation; another product (OpenCRQ) plugs in as a second bean of
 * this interface, with no change to the service, the API or the screens.
 */
public interface CounterpartProvider {

  /** The product key, lower-case (opencti). */
  String product();

  /** The base URL users open the product at, when the tenant is connected to it. */
  Optional<String> connectedUrl(String tenantId);

  /** Whether this product can hold objects of that kind. */
  boolean supports(CounterpartKind kind);

  /**
   * Resolves the given normalised keys in one batched call.
   *
   * @return the counterparts found, by key; a key with no counterpart is absent
   * @throws CounterpartUnavailableException when the product cannot be reached or refuses
   */
  Map<String, Counterpart> resolve(String tenantId, CounterpartKind kind, Collection<String> keys)
      throws CounterpartUnavailableException;

  /**
   * Whether {@link #resolve} reads the other product with the calling user's own identity, so what
   * it answers is what that user may read there. False for a lookup made with the connection's
   * service credential: the service then keeps the pivot link alone (no remote name, type or fact),
   * since the caller's rights in the other product are unknown.
   */
  default boolean readsAsCaller() {
    return false;
  }
}
