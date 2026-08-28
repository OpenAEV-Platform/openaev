import {
  Combobox,
  ComboboxContent,
  ComboboxControls,
  ComboboxField,
  ComboboxInput,
  ComboboxTrigger,
} from '@filigran/design-system';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../components/i18n';
import { type AgentOption } from './agentApi';

interface AgentSelectorProps {
  options: AgentOption[];
  value: AgentOption | null;
  onChange: (agent: AgentOption | null) => void;
  loading?: boolean;
  disabled?: boolean;
  width?: number | string;
}

/**
 * Compact agent picker used in AI dialogs (TTP extraction, remediation,
 * Ask AI…) — matches the look of the OpenCTI / OpenAEV AskAI agent dropdown.
 */
const AgentSelector: FunctionComponent<AgentSelectorProps> = ({
  options,
  value,
  onChange,
  loading = false,
  disabled = false,
  width = 220,
}) => {
  const { t } = useFormatter();
  const noAgents = !loading && options.length === 0;

  return (
    <div style={{ width }}>
      <Combobox<AgentOption>
        options={options}
        value={value}
        onValueChange={newValue => onChange(newValue as AgentOption | null)}
        getOptionLabel={option => option.name}
        isOptionEqualToValue={(option, optValue) => option.id === optValue.id}
        loading={loading}
        disabled={disabled || noAgents}
        clearable={false}
      >
        <ComboboxField>
          <ComboboxInput placeholder={noAgents ? t('No agent available') : t('Select agent')} />
          <ComboboxControls>
            <ComboboxTrigger />
          </ComboboxControls>
        </ComboboxField>
        <ComboboxContent emptyMessage={t('No agent available')} />
      </Combobox>
    </div>
  );
};

export default AgentSelector;
