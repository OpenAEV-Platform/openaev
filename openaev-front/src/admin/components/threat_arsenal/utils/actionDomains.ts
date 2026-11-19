import { type Domain, type ThreatArsenalAction } from '../../../../utils/api-types';
import { domainsOf } from '../../../../utils/domains/domainUtils';

// In the order the search sorts them: the first one is the action's section.
const actionDomains = (action: ThreatArsenalAction, allDomains: Domain[]): Domain[] => domainsOf(action.action_domains_ids, allDomains);

export default actionDomains;
