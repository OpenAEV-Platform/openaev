import { Autocomplete, Chip, TextField } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import type { AxiosResponse } from 'axios';
import { type FunctionComponent, useEffect, useState } from 'react';

import { searchScenarioAsOption } from '../../actions/scenarios/scenario-actions';

export interface MultiSelectScenario {
  id: string;
  label: string;
}

interface Props {
  label?: string;
  value: MultiSelectScenario[];
  onChange: (selected: MultiSelectScenario[]) => void;
}

const ScenariosField: FunctionComponent<Props> = ({ label = 'Scenarios', value, onChange }) => {
  const theme = useTheme();
  const [options, setOptions] = useState<MultiSelectScenario[]>([]);
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState(false);

  useEffect(() => {
    setLoading(true);
    searchScenarioAsOption()
      .then((response: AxiosResponse<MultiSelectScenario[]>) => setOptions(response.data))
      .finally(() => setLoading(false));
  }, []);

  return (
    <Autocomplete
      multiple
      open={open}
      onOpen={() => setOpen(true)}
      onClose={(_, reason) => {
        if (reason === 'selectOption') return;
        setOpen(false);
      }}
      options={options}
      loading={loading}
      value={value}
      onChange={(_, newValue) => onChange(newValue)}
      getOptionLabel={option => option.label}
      isOptionEqualToValue={(option, val) => option.id === val.id}
      renderTags={(tagValue, getTagProps) =>
        tagValue.map((option, index) => (
          <Chip
            label={option.label}
            {...getTagProps({ index })}
            key={option.id}
            size="small"
          />
        ))}
      renderInput={params => (
        <TextField
          {...params}
          label={label}
          variant="outlined"
          size="small"
        />
      )}
      style={{ marginTop: theme.spacing(2) }}
    />
  );
};

export default ScenariosField;
