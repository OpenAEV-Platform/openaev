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

// OCSF findings are cloud misconfigurations produced by scanners like Prowler: instead of
// surfacing the internal contract type name ("OCSF"), show a user-facing "Cloud (AWS)" label
// built from the provider captured on the finding (see Finding#cloudProvider). Falls back to a
// generic "Cloud" label when the provider is unknown/missing, and to the regular type mapping
// for every other finding type.
const getFindingTypeLabel = (
  t: (key: string) => string,
  findingType: string,
  cloudProvider?: string | null,
): string => {
  if (findingType === 'ocsf') {
    return cloudProvider ? `${t('Cloud')} (${formatCloudProvider(cloudProvider)})` : t('Cloud');
  }
  return t(ContractOutputElementType[findingType as keyof typeof ContractOutputElementType] ?? findingType);
};

export default getFindingTypeLabel;
