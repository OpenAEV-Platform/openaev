import { type Domain, type ThreatArsenalAction } from '../../../../utils/api-types';
import { TO_CLASSIFY } from '../../../../utils/domains/domainUtils';

// "To classify" left out, in name order as the search sorts them: the first one is the action's section.
const actionDomains = (action: ThreatArsenalAction, allDomains: Domain[]): Domain[] => allDomains
  .filter(domain => action.action_domains_ids?.includes(domain.domain_id) && domain.domain_name !== TO_CLASSIFY)
  .sort((a, b) => a.domain_name.localeCompare(b.domain_name));

export default actionDomains;
