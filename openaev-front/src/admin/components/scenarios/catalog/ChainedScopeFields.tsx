import { Text } from '@filigran/design-system';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../../components/i18n';
import { type WorkflowConfigurationInput, type WorkflowConfigurationOutput } from '../../../../utils/api-types';
import { ScopeSection } from '../../autonomous/AutonomousRunConfigFields';
import { type ScopeSelection } from '../../autonomous/useAutonomousRunConfig';
import ScopeExecutionLimits from '../../chaining/ScopeExecutionLimits';

interface Props {
  allow: ScopeSelection;
  deny: ScopeSelection;
  limits: WorkflowConfigurationInput;
  onAllowChange: (selection: ScopeSelection) => void;
  onDenyChange: (selection: ScopeSelection) => void;
  onLimitsChange: (limits: WorkflowConfigurationInput) => void;
}

// The scope of a chained scenario, chosen at install with the pickers of its Scope tab.
const ChainedScopeFields: FunctionComponent<Props> = ({ allow, deny, limits, onAllowChange, onDenyChange, onLimitsChange }) => {
  const { t } = useFormatter();
  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      gap: 24,
    }}
    >
      <Text variant="content-base">{t('A chained scenario only acts inside its scope. It is saved on the installed scenario.')}</Text>
      <div>
        <Text variant="content-base-bold">{t('Allow list')}</Text>
        <ScopeSection mode="ALLOWLIST" selection={allow} onChange={onAllowChange} />
      </div>
      <div>
        <Text variant="content-base-bold">{t('Deny list')}</Text>
        <Text variant="content-caption">{t('Entries in the deny list always take priority over those in the allow list.')}</Text>
        <ScopeSection mode="DENYLIST" selection={deny} onChange={onDenyChange} />
      </div>
      <ScopeExecutionLimits
        workflowConfiguration={limits as WorkflowConfigurationOutput}
        onUpdate={overrides => onLimitsChange({
          ...limits,
          ...overrides,
        })}
      />
    </div>
  );
};

export default ChainedScopeFields;
