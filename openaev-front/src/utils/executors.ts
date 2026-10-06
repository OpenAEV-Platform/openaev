// Executor types gated by the Enterprise Edition license, used to show the EE chip.
// Mirrors EnterpriseEditionService.eeExecutorsTypes in the backend: add a new EE executor in both places.
export const EE_EXECUTOR_TYPES: readonly string[] = [
  'openaev_crowdstrike_executor',
  'openaev_tanium',
  'openaev_sentinelone_executor',
  'openaev_paloaltocortex_executor',
  'openaev_mde_executor',
];

export const isEnterpriseExecutorType = (executorType?: string): boolean => !!executorType && EE_EXECUTOR_TYPES.includes(executorType);
