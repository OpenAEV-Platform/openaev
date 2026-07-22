import { Alert, Button, Dialog, DialogActions, DialogContent, DialogTitle, FormControl, FormControlLabel, Radio, RadioGroup, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type ChangeEvent, useState } from 'react';

import Transition from '../../../components/common/Transition';
import { useFormatter } from '../../../components/i18n';
import { type LessonsTemplate } from '../../../utils/api-types';
import { Can } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import CreateLessonsTemplate from '../components/lessons/CreateLessonsTemplate';

interface Props {
  open: boolean;
  onClose: () => void;
  onApply: (templateId: string) => Promise<unknown> | void;
  lessonsTemplates: LessonsTemplate[];
  /** 'simulation' | 'scenario' - drives the informational alert wording. */
  variant: string;
}

// Shared "apply a lessons learned template" dialog (simulation + scenario).
const LessonsApplyTemplateDialog = ({ open, onClose, onApply, lessonsTemplates, variant }: Props) => {
  const theme = useTheme();
  const { t } = useFormatter();
  const [templateValue, setTemplateValue] = useState<string | null>(null);

  const handleChange = (event: ChangeEvent<HTMLInputElement>) => {
    setTemplateValue(event.target.value);
  };

  const applyTemplate = async () => {
    if (templateValue !== null) {
      await onApply(templateValue);
      onClose();
    }
  };

  return (
    <Dialog
      TransitionComponent={Transition}
      keepMounted={false}
      open={open}
      onClose={onClose}
      fullWidth
      maxWidth="md"
      PaperProps={{ elevation: 1 }}
    >
      <DialogTitle>{t('Apply a lessons learned template')}</DialogTitle>
      <DialogContent>
        <Alert severity="info">
          {variant === 'scenario'
            ? t('Applying a template will add all its categories and questions to this scenario.')
            : t('Applying a template will add all its categories and questions to this simulation.')}
        </Alert>
        <FormControl sx={{
          width: '100%',
          marginTop: 1,
        }}
        >
          <RadioGroup
            sx={{ width: '100%' }}
            aria-labelledby="controlled-radio-buttons-group"
            name="template"
            value={templateValue}
            onChange={handleChange}
          >
            {lessonsTemplates.map((template: LessonsTemplate) => (
              <FormControlLabel
                key={template.lessonstemplate_id}
                sx={{
                  width: '100%',
                  margin: 0,
                  borderBottom: `1px solid ${alpha(theme.palette.text.primary, 0.08)}`,
                }}
                value={template.lessonstemplate_id}
                control={<Radio />}
                label={(
                  <div style={{ margin: `${theme.spacing(1.5)} 0 ${theme.spacing(1.5)} ${theme.spacing(1)}` }}>
                    <Typography sx={{
                      fontSize: 14,
                      fontWeight: 600,
                    }}
                    >
                      {template.lessons_template_name}
                    </Typography>
                    <Typography variant="body2" sx={{ color: 'text.secondary' }}>
                      {template.lessons_template_description || t('No description')}
                    </Typography>
                  </div>
                )}
              />
            ))}
          </RadioGroup>
        </FormControl>
        <CreateLessonsTemplate inline />
      </DialogContent>
      <DialogActions>
        <Button variant="outlined" color="primary" onClick={onClose}>
          {t('Cancel')}
        </Button>
        <Can I={ACTIONS.ACCESS} a={SUBJECTS.LESSONS_LEARNED}>
          <Button
            variant="contained"
            color="primary"
            onClick={applyTemplate}
            disabled={templateValue === null}
          >
            {t('Apply')}
          </Button>
        </Can>
      </DialogActions>
    </Dialog>
  );
};

export default LessonsApplyTemplateDialog;
