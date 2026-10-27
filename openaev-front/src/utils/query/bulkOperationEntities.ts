/**
 * `bulk_operation_entity` is the human label the backend gives to a bulk operation
 * (`BulkOperationMonitor.start(action, entityLabel, total)`), e.g. "injects" or "asset groups".
 * The stream names entities by schema name, so this maps a label to the schema names whose live
 * queries it may change.
 */
const BULK_ENTITY_TYPES: Record<string, string[]> = {
  'injects': ['injects', 'inject_statuses', 'injectexpectations'],
  'atomic testings': ['injects', 'inject_statuses', 'injectexpectations'],
  'inject expectations': ['injectexpectations'],
  'players': ['users'],
  'teams': ['teams'],
  'asset groups': ['asset_groups'],
  'assets': ['assets', 'agents'],
  'simulations': ['exercises'],
  'scenarios': ['scenarios'],
  'organizations': ['organizations'],
};

/** Schema names concerned by a bulk operation label; undefined when unknown (then everything is concerned). */
const bulkEntityTypes = (entity: string | undefined): string[] | undefined =>
  entity === undefined ? undefined : BULK_ENTITY_TYPES[entity.trim().toLowerCase()];

export default bulkEntityTypes;
