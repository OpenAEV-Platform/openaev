import { Autocomplete, CircularProgress, TextField } from '@mui/material';
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
    <Autocomplete<AgentOption>
      sx={{ width }}
      size="small"
      options={options}
      getOptionLabel={option => option.name}
      value={value}
      onChange={(_event, newValue) => onChange(newValue)}
      loading={loading}
      disabled={disabled || noAgents}
      noOptionsText={t('No agent available')}
      renderInput={params => (
        <TextField
          {...params}
          variant="outlined"
          size="small"
          placeholder={noAgents ? t('No agent available') : t('Select agent')}
          InputProps={{
            ...params.InputProps,
            endAdornment: (
              <>
                {loading ? <CircularProgress color="inherit" size={16} /> : null}
                {params.InputProps.endAdornment}
              </>
            ),
          }}
        />
      )}
      isOptionEqualToValue={(option, optValue) => option.id === optValue.id}
      clearIcon={null}
    />
  );
};

export default AgentSelector;
