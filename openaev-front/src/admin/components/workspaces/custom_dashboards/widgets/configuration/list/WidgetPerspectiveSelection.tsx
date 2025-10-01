import { Button } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useState } from 'react';

import { useFormatter } from '../../../../../../../components/i18n';
import {
  type FilterGroup,
  type ListPerspective,
} from '../../../../../../../utils/api-types';
import WidgetSeriesSelection from '../WidgetSeriesSelection';

const WidgetPerspectiveSelection: FunctionComponent<{
  perspective?: {
    name?: string;
    filter?: FilterGroup;
  };
  onChange: (perspective: ListPerspective) => void;
  onSubmit: () => void;
}> = ({ perspective, onChange, onSubmit }) => {
  // Standard hooks
  const { t } = useFormatter();
  const theme = useTheme();

  const [error, setError] = useState<boolean>(false);

  const handleSubmit = () => {
    if (perspective?.filter) {
      onSubmit();
    } else {
      setError(true);
    }
  };

  return (
    <>
      <WidgetSeriesSelection index={0} perspective={perspective} onChange={onChange} error={error} />

      <div style={{
        display: 'flex',
        justifyContent: 'center',
      }}
      >
        <Button
          variant="contained"
          color="primary"
          sx={{ marginTop: theme.spacing(2) }}
          onClick={handleSubmit}
        >
          {t('Validate')}
        </Button>
      </div>
    </>
  );
};

export default WidgetPerspectiveSelection;
