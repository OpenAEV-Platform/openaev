import ContractOutputElementType, { CONTRACT_OUTPUT_ELEMENT_TYPE_KEYS } from './ContractOutputElementType';

export const getFindingTypeKey = (findingType: string): string => (
  CONTRACT_OUTPUT_ELEMENT_TYPE_KEYS.find(key => ContractOutputElementType[key] === findingType)
  ?? findingType
);

// OCSF findings are security misconfigurations produced by scanners like Prowler: instead of
// surfacing the internal contract type name ("OCSF"), show the provider-independent user-facing
// type "Misconfig". Provider is exposed separately in the quick filters and details.
const getFindingTypeLabel = (
  t: (key: string) => string,
  findingType: string,
  _cloudProvider?: string | null,
): string => {
  const findingTypeKey = getFindingTypeKey(findingType);
  if (findingTypeKey === 'ocsf') {
    return t('Misconfig');
  }
  return t(
    ContractOutputElementType[findingTypeKey as keyof typeof ContractOutputElementType]
    ?? findingType,
  );
};

export default getFindingTypeLabel;
