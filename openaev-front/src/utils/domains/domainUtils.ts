import { type Domain } from '../api-types';
export interface DomainAutocompleteState {
  currentIds: string[];
  options: {
    id: string;
    label: string;
  }[];
}

export const TO_CLASSIFY = 'To classify';

export const buildDomainAutocompleteState = (
  domains: Domain[],
  value: Domain[] | string[] | undefined,
): DomainAutocompleteState => {
  const selectableDomains = domains.filter(
    d => d.domain_name !== TO_CLASSIFY,
  );

  const toClassifyDomain = domains.find(
    d => d.domain_name === TO_CLASSIFY,
  );

  const currentIds: string[] = Array.isArray(value)
    ? value.map(v =>
        typeof v === 'string' ? v : v.domain_id,
      )
    : [];

  const hasToClassifySelected
    = !!toClassifyDomain
      && currentIds.includes(toClassifyDomain.domain_id);

  const optionsToDisplay
    = hasToClassifySelected && toClassifyDomain
      ? [...selectableDomains, toClassifyDomain]
      : selectableDomains;

  return {
    currentIds,
    options: optionsToDisplay.map(d => ({
      id: d.domain_id,
      label: d.domain_name,
    })),
  };
};

// "To classify" left out, in name order: the first one is the primary domain.
export const domainsOf = (ids: string[] | undefined, allDomains: Domain[]): Domain[] => allDomains
  .filter(domain => ids?.includes(domain.domain_id) && domain.domain_name !== TO_CLASSIFY)
  .sort((a, b) => a.domain_name.localeCompare(b.domain_name));
