import ContractOutputElementType from './ContractOutputElementType';

// OCSF findings are security misconfigurations produced by scanners like Prowler: instead of
// surfacing the internal contract type name ("OCSF"), show the provider-independent user-facing
// type "Misconfig". Provider is exposed separately in the quick filters and details.
const getFindingTypeLabel = (
  t: (key: string) => string,
  findingType: string,
  _cloudProvider?: string | null,
): string => {
  if (findingType === 'ocsf') {
    return t('Misconfig');
  }
  return t(ContractOutputElementType[findingType as keyof typeof ContractOutputElementType] ?? findingType);
};

export default getFindingTypeLabel;
