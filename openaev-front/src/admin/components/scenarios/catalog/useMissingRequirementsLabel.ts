import { useFormatter } from '../../../../components/i18n';
import { type CatalogScenarioRequirementOutput } from '../../../../utils/api-types';

const NAMED_AT_MOST = 2;

// "Nmap: The network mapper" reads "Nmap" on a chip.
const shortName = (requirement: CatalogScenarioRequirementOutput) => requirement.requirement_name.split(':')[0].trim();

// What a catalogue scenario lacks: named when it is short, counted otherwise, and the full list.
const useMissingRequirementsLabel = (missing: CatalogScenarioRequirementOutput[]) => {
  const { t } = useFormatter();
  return {
    label: missing.length > NAMED_AT_MOST
      ? t('Needs {count} prerequisites', { count: String(missing.length) })
      : t('Needs {names}', { names: missing.map(shortName).join(', ') }),
    names: missing.map(requirement => requirement.requirement_name).join(', '),
  };
};

export default useMissingRequirementsLabel;
