import type { HealthCheck } from '../../../../utils/api-types';
import docsUrl, { type DocsLinkKey } from '../../../../utils/docsUrl';

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
const documentation = (key: DocsLinkKey) => (): HealthcheckFixLink => ({ href: docsUrl(key) });

const FIX_LINKS: Partial<Record<HealthCheck['type'], (owner: HealthcheckOwner) => HealthcheckFixLink>> = {
  SMTP: documentation('configuration_mail_services'),
  IMAP: documentation('configuration_imap'),
  AGENT_OR_EXECUTOR: () => ({ to: '/admin/agents' }),
  SECURITY_SYSTEM_COLLECTOR: documentation('collectors'),
  INJECT: ownerPage('injects'),
  PAYLOAD: ownerPage('injects'),
  TEAMS: ownerPage('injects'),
  NMAP: documentation('injectors'),
  NUCLEI: documentation('injectors'),
  SCOPE_DEFINITION: ownerPage('scope'),
};

// Where a failing health check gets fixed: shared by the configuration pill, the scope pages and the launch check.
const healthcheckFixLink = (type: HealthCheck['type'], owner: HealthcheckOwner): HealthcheckFixLink => FIX_LINKS[type]?.(owner) ?? {};

export default healthcheckFixLink;
