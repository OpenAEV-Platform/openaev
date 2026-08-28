/**
 * Connectors that ARE the platform: the implant injector executes every payload and the agent
 * executor drives every agent, so removing either one breaks execution itself and no amount of
 * re-registration recovers the injects that referenced them.
 *
 * <p>Every other connector is removable, including the ones running in-process: they are shipped
 * with the platform, not vital to it, and a legacy row whose implementation no longer exists (an
 * injector dropped from the code in an earlier version) must be cleanable from the UI. In-process
 * connectors that are still implemented simply register again on the next platform restart.
 *
 * <p>Kept as connector types, not ids: the ids are seeded per tenant, the types are stable.
 */
const PLATFORM_CONNECTOR_TYPES = ['openaev_implant', 'openaev_agent'];

const isPlatformConnector = (connectorType?: string): boolean =>
  connectorType != null && PLATFORM_CONNECTOR_TYPES.includes(connectorType);

export default isPlatformConnector;
