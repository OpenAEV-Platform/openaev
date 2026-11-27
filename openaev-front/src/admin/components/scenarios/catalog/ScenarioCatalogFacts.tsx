import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';
import { useForm } from 'react-hook-form';

import { type ScenarioFormInput } from '../ScenarioForm';
import ScenarioGeneralFields from '../ScenarioGeneralFields';

interface Props { values: ScenarioFormInput }

// A catalogue scenario before install: its general facts in the fields of the scenario form, read only.
const ScenarioCatalogFacts: FunctionComponent<Props> = ({ values }) => {
  const theme = useTheme();
  const form = useForm<ScenarioFormInput>({ defaultValues: values });
  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      gap: theme.spacing(2),
    }}
    >
      <ScenarioGeneralFields form={form} initialValues={values} readOnly />
    </div>
  );
};

export default ScenarioCatalogFacts;
