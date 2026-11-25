package io.openaev.rest.payload.contract_output_element;

import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Payload;
import org.hibernate.Hibernate;

/**
 * Forces the LAZY {@code ContractOutputElement#tags} association of a payload's output parsers to
 * load while the tenant scope is set.
 *
 * <p>{@code tags} is a tenant table, so the scope lives on the transaction. With open-in-view, a
 * collection serialized by Jackson resolves once the handler is done and that scope is gone: the
 * load matches nothing and the response carries {@code contract_output_element_tags: []} with a
 * 200, and a client that sends that list back on a payload update removes the links. Output parsers
 * and their elements load eagerly with the payload; their tags are the one lazy step.
 */
public final class ContractOutputElementInitializer {

  private ContractOutputElementInitializer() {}

  public static void initializeFromPayload(Payload payload) {
    payload.getOutputParsers().stream()
        .flatMap(outputParser -> outputParser.getContractOutputElements().stream())
        .forEach(element -> Hibernate.initialize(element.getTags()));
  }

  public static void initializeFromPayloads(Iterable<Payload> payloads) {
    payloads.forEach(ContractOutputElementInitializer::initializeFromPayload);
  }

  public static void initializeFromContract(InjectorContract injectorContract) {
    if (injectorContract.getPayload() != null) {
      initializeFromPayload(injectorContract.getPayload());
    }
  }

  public static void initializeFromContracts(Iterable<InjectorContract> injectorContracts) {
    injectorContracts.forEach(ContractOutputElementInitializer::initializeFromContract);
  }
}
