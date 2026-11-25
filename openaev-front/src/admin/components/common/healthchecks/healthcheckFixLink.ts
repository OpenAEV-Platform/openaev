import type { HealthCheck } from '../../../../utils/api-types';

const DOCUMENTATION_ROOT_URL = 'https://docs.openaev.io';

// A page of the platform (to), or a documentation page (href).
interface HealthcheckFixLink {
  to?: string;
  href?: string;
}

export interface HealthcheckOwner {
  scenarioId?: string;
  exerciseId?: string;
}

const ownerPage = (page: string) => ({ scenarioId, exerciseId }: HealthcheckOwner): HealthcheckFixLink => ({ to: exerciseId ? `/admin/simulations/${exerciseId}/${page}` : `/admin/scenarios/${scenarioId}/${page}` });
const documentation = (path: string) => (): HealthcheckFixLink => ({ href: `${DOCUMENTATION_ROOT_URL}${path}` });

const FIX_LINKS: Partial<Record<HealthCheck['type'], (owner: HealthcheckOwner) => HealthcheckFixLink>> = {
  SMTP: documentation('/latest/deployment/configuration/?h=smtp#mail-services'),
  IMAP: documentation('/latest/deployment/configuration/?h=smtp#imap'),
  AGENT_OR_EXECUTOR: () => ({ to: '/admin/agents' }),
  SECURITY_SYSTEM_COLLECTOR: documentation('/latest/usage/collectors/?h=collector'),
  INJECT: ownerPage('injects'),
  PAYLOAD: ownerPage('injects'),
  TEAMS: ownerPage('injects'),
  NMAP: documentation('/latest/usage/injectors'),
  NUCLEI: documentation('/latest/usage/injectors'),
  SCOPE_DEFINITION: ownerPage('scope'),
};

// Where a failing health check gets fixed: shared by the configuration pill, the scope pages and the launch check.
const healthcheckFixLink = (type: HealthCheck['type'], owner: HealthcheckOwner): HealthcheckFixLink => FIX_LINKS[type]?.(owner) ?? {};

export default healthcheckFixLink;
