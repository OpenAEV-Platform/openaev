import { type Domain, type ThreatArsenalAction } from '../../../utils/api-types';
import { TO_CLASSIFY } from '../../../utils/domains/domainUtils';

// Domains of an action, the primary one first: same rule as the server sort (alphabetical).
export const actionDomains = (action: ThreatArsenalAction, allDomains: Domain[]): Domain[] => allDomains
  .filter(d => action.action_domains_ids?.includes(d.domain_id) && d.domain_name !== TO_CLASSIFY)
  .sort((a, b) => Number(a.domain_name > b.domain_name) - Number(a.domain_name < b.domain_name));

export interface DomainSection {
  domain?: Domain;
  actions: ThreatArsenalAction[];
}

// The server sorts by primary domain first: consecutive actions of a domain make a section.
export const groupByPrimaryDomain = (actions: ThreatArsenalAction[], allDomains: Domain[]): DomainSection[] => {
  const sections: DomainSection[] = [];
  actions.forEach((action) => {
    const domain = actionDomains(action, allDomains)[0];
    const last = sections[sections.length - 1];
    if (last && last.domain?.domain_id === domain?.domain_id) {
      last.actions.push(action);
    } else {
      sections.push({
        domain,
        actions: [action],
      });
    }
  });
  return sections;
};
