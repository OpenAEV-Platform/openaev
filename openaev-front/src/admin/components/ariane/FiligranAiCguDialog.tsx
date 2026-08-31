import { Button, Checkbox, Dialog, DialogActions, DialogContent, DialogTitle, FormControlLabel, Stack, Typography } from '@mui/material';
import type React from 'react';
import { useState } from 'react';

import { updateChatbotAiCguStatus } from '../../../actions/Application';
import { useFormatter } from '../../../components/i18n';
import { useAppDispatch } from '../../../utils/hooks';
import { useAbility } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';

interface FiligranAiCguDialogProps {
  open: boolean;
  onClose: () => void;
}

const FiligranAiCguDialog: React.FC<FiligranAiCguDialogProps> = ({ open, onClose }) => {
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const ability = useAbility();
  const [isChecked, setIsChecked] = useState(false);

  const canManage = ability.can(ACTIONS.MANAGE, SUBJECTS.PLATFORM_SETTINGS);

  const handleSubmit = (status: 'enabled' | 'disabled') => {
    dispatch(updateChatbotAiCguStatus({ status }));
    setIsChecked(false);
    onClose();
  };

  return (
    <Dialog
      slotProps={{ paper: { elevation: 1 } }}
      open={open}
      onClose={onClose}
      fullWidth
      maxWidth="sm"
    >
      <DialogTitle>{t('Validate the Filigran AI Terms')}</DialogTitle>
      <DialogContent>
        <Stack gap={3}>
          <Typography>
            {t('Please take a moment to review our "Filigran AI Terms". Our chatbot is here to assist you, but it\'s important to understand how it works and what to expect. Please read the full terms to know how we protect your data and ensure service quality.')}
          </Typography>
          <Stack
            alignItems="center"
            gap={2}
          >
            <Button
              href="https://filigran.io/app/uploads/2025/09/filigran-ai-terms-september-2025.pdf"
              target="_blank"
              rel="noreferrer"
              variant="outlined"
              color="secondary"
            >
              {t('Read the Filigran AI Terms')}
            </Button>
            <FormControlLabel
              checked={isChecked}
              required
              control={<Checkbox />}
              label={t('I have read, I understand and I accept the Filigran AI terms')}
              labelPlacement="end"
              onChange={(_, checked) => setIsChecked(checked)}
            />
          </Stack>
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button
          variant="outlined"
          color="primary"
          onClick={() => handleSubmit('disabled')}
        >
          {t('Decline')}
        </Button>
        <Button
          variant="contained"
          color="primary"
          onClick={() => handleSubmit('enabled')}
          disabled={!isChecked || !canManage}
        >
          {t('I Agree to Filigran AI Terms')}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default FiligranAiCguDialog;
