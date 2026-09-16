import ContractOutputElementType from './ContractOutputElementType';

// Known Prowler/OCSF cloud provider slugs mapped to their commonly used display acronym/name.
// These are proper nouns (AWS, Azure, GCP, Kubernetes) and are not translated across locales.
const CLOUD_PROVIDER_LABELS: Record<string, string> = {
  aws: 'AWS',
  azure: 'Azure',
  gcp: 'GCP',
  kubernetes: 'Kubernetes',
};

const formatCloudProvider = (cloudProvider: string) => CLOUD_PROVIDER_LABELS[cloudProvider.toLowerCase()]
  ?? (cloudProvider.charAt(0).toUpperCase() + cloudProvider.slice(1));

// OCSF findings are security misconfigurations produced by scanners like Prowler: instead of
// surfacing the internal contract type name ("OCSF"), show a user-facing "Misconfig (AWS)" label
// built from the provider captured on the finding. Falls back to a generic "Misconfig" label when
// the provider is missing, and to the regular type mapping for every other finding type.
const getFindingTypeLabel = (
  t: (key: string) => string,
  findingType: string,
  cloudProvider?: string | null,
): string => {
  if (findingType === 'ocsf') {
    return cloudProvider ? `${t('Misconfig')} (${formatCloudProvider(cloudProvider)})` : t('Misconfig');
  }
  return t(ContractOutputElementType[findingType as keyof typeof ContractOutputElementType] ?? findingType);
};

export default getFindingTypeLabel;
