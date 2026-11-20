import { useFormatter } from '../../../../components/i18n';
import { type CatalogScenarioRequirementOutput } from '../../../../utils/api-types';

const NAMED_AT_MOST = 2;

// What a catalogue scenario lacks: named when it is short, counted otherwise, and the full list.
const useMissingRequirementsLabel = (missing: CatalogScenarioRequirementOutput[]) => {
  const { t } = useFormatter();
  const names = missing.map(requirement => requirement.requirement_label).join(', ');
  return {
    label: missing.length > NAMED_AT_MOST
      ? t('Needs {count} prerequisites', { count: missing.length })
      : t('Needs {names}', { names }),
    names,
  };
};

export default useMissingRequirementsLabel;
