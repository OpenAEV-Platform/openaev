import { type Domain } from '../api-types';
export interface DomainAutocompleteState {
  currentIds: string[];
  options: {
    id: string;
    label: string;
  }[];
}

export const buildDomainAutocompleteState = (
  domains: Domain[],
  value: Domain[] | string[] | undefined,
): DomainAutocompleteState => {
  const selectableDomains = domains.filter(
    d => d.domain_name !== 'To classify',
  );

  const toClassifyDomain = domains.find(
    d => d.domain_name === 'To classify',
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

export const cleanSelectedDomains = (
  domains: Domain[],
  ids: string[],
): Domain[] => {
  const selected = domains.filter(d =>
    ids.includes(d.domain_id),
  );

  return selected.length > 1
    ? selected.filter(d => d.domain_name !== 'To classify')
    : selected;
};
