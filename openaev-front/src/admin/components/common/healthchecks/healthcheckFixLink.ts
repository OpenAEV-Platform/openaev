const DOCUMENTATION_ROOT_URL = 'https://docs.openaev.io';

export interface HealthcheckFixLink {
  // A page of the platform, opened in place.
  to?: string;
  // A documentation page, opened in a new tab.
  href?: string;
}

// Where a failing health check gets fixed: shared by the configuration pill and the launch check.
const healthcheckFixLink = (type: string, { scenarioId, exerciseId }: {
  scenarioId?: string;
  exerciseId?: string;
}): HealthcheckFixLink => {
  switch (type) {
    case 'SMTP':
      return { href: `${DOCUMENTATION_ROOT_URL}/latest/deployment/configuration/?h=smtp#mail-services` };
    case 'IMAP':
      return { href: `${DOCUMENTATION_ROOT_URL}/latest/deployment/configuration/?h=smtp#imap` };
    case 'AGENT_OR_EXECUTOR':
      return { to: '/admin/agents' };
    case 'SECURITY_SYSTEM_COLLECTOR':
      return { href: `${DOCUMENTATION_ROOT_URL}/latest/usage/collectors/?h=collector` };
    case 'INJECT':
    case 'TEAMS':
    case 'PAYLOAD':
      return { to: exerciseId ? `/admin/simulations/${exerciseId}/injects` : `/admin/scenarios/${scenarioId}/injects` };
    case 'NMAP':
    case 'NUCLEI':
      return { href: `${DOCUMENTATION_ROOT_URL}/latest/usage/injectors` };
    case 'SCOPE_DEFINITION':
      return { to: exerciseId ? `/admin/simulations/${exerciseId}/scope` : `/admin/scenarios/${scenarioId}/scope` };
    default:
      return {};
  }
};

export default healthcheckFixLink;
