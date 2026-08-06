import { Box, Button } from '@mui/material';

import { useFormatter } from '../../../../../components/i18n';

interface ActionFormButtonsProps {
  disabled: boolean;
  onCancel: () => void;
  submitLabel?: string;
}

const ActionFormButtons = ({ disabled, onCancel, submitLabel }: ActionFormButtonsProps) => {
  const { t } = useFormatter();

  return (
    <Box sx={{
      display: 'flex',
      justifyContent: 'flex-end',
      gap: 1,
      mt: 1,
    }}
    >
      <Button variant="outlined" color="primary" onClick={onCancel}>
        {t('Cancel')}
      </Button>
      <Button variant="contained" color="primary" type="submit" disabled={disabled}>
        {submitLabel ?? t('Save')}
      </Button>
    </Box>
  );
};

export default ActionFormButtons;
