import { Alert, Text } from '@filigran/design-system';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../../components/i18n';
import { ScopeSection } from '../../autonomous/AutonomousRunConfigFields';
import { type ScopeSelection } from '../../autonomous/useAutonomousRunConfig';
import ScopeExecutionLimits from '../../chaining/ScopeExecutionLimits';
import { type ChainedScope, hasScope } from './chainedScope';

interface Props {
  value: ChainedScope;
  onChange: (value: ChainedScope) => void;
}

// The scope of a chained scenario, chosen at install with the pickers of its Scope tab.
const ChainedScopeFields: FunctionComponent<Props> = ({ value, onChange }) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const change = (field: Partial<ChainedScope>) => onChange({
    ...value,
    ...field,
  });
  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      gap: theme.spacing(3),
    }}
    >
      <Text variant="content-base">{t('A chained scenario only acts inside its scope. It is saved on the installed scenario.')}</Text>
      <div>
        <Text variant="content-base-bold">{t('Allow list')}</Text>
        <ScopeSection mode="ALLOWLIST" selection={value.allow} onChange={(allow: ScopeSelection) => change({ allow })} />
      </div>
      <div>
        <Text variant="content-base-bold">{t('Deny list')}</Text>
        <Text variant="content-caption">{t('Entries in the deny list always take priority over those in the allow list.')}</Text>
        <ScopeSection mode="DENYLIST" selection={value.deny} onChange={(deny: ScopeSelection) => change({ deny })} />
      </div>
      <ScopeExecutionLimits
        workflowConfiguration={value.limits}
        onUpdate={overrides => change({
          limits: {
            ...value.limits,
            ...overrides,
          },
        })}
      />
      {!hasScope(value) && (
        <Alert elevation={1} severity="warning" title={t('Add at least one entry to the allow list.')} />
      )}
    </div>
  );
};

export default ChainedScopeFields;
