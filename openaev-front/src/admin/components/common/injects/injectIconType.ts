interface InjectIconSource {
  inject_type?: string;
  inject_injector_contract?: {
    injector_contract_injector_type?: string;
    injector_contract_payload?: {
      payload_collector_type?: string;
      payload_type?: string;
    } | null;
  } | null;
}

/**
 * The type `InjectIcon` draws for an inject: the payload collector or payload type for a payload
 * contract, otherwise the injector type. `inject_type` is computed by the API from the inject's own
 * injector, which a row freshly pushed during a live simulation may not carry yet: the contract's
 * injector type is then used, so the icon does not fall back to the "unknown" placeholder.
 */
export const injectIconType = (inject: InjectIconSource): string | undefined => {
  const payload = inject.inject_injector_contract?.injector_contract_payload;
  if (payload) {
    return payload.payload_collector_type || payload.payload_type;
  }
  return inject.inject_type || inject.inject_injector_contract?.injector_contract_injector_type || undefined;
};
