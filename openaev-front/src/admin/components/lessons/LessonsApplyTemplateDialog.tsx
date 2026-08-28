import { Radio, RadioGroup } from '@filigran/design-system';
import { Alert, Button, Dialog, DialogActions, DialogContent, DialogTitle, FormControl } from '@mui/material';
import { alpha } from '@mui/material/styles';
import { useId, useState } from 'react';
import { makeStyles } from 'tss-react/mui';

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
  /** Drives the informational alert wording. */
  variant: 'simulation' | 'scenario';
}

// Shared "apply a lessons learned template" dialog (simulation + scenario).
// The template rows keep the separator the MUI list drew between them.
const useStyles = makeStyles()(theme => ({
  templateRow: {
    width: '100%',
    borderBottom: `1px solid ${alpha(theme.palette.text.primary, 0.08)}`,
    padding: `${theme.spacing(1.5)} 0`,
  },
}));

const LessonsApplyTemplateDialog = ({ open, onClose, onApply, lessonsTemplates, variant }: Props) => {
  const { classes } = useStyles();
  const titleId = useId();
  const { t } = useFormatter();
  const [templateValue, setTemplateValue] = useState<string | null>(null);

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
      <DialogTitle id={titleId}>{t('Apply a lessons learned template')}</DialogTitle>
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
            aria-labelledby={titleId}
            value={templateValue}
            onValueChange={setTemplateValue}
          >
            {lessonsTemplates.map((template: LessonsTemplate) => (
              <Radio
                key={template.lessonstemplate_id}
                value={template.lessonstemplate_id}
                className={classes.templateRow}
                label={template.lessons_template_name}
                description={template.lessons_template_description || t('No description')}
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
        <Can I={ACTIONS.MANAGE} a={SUBJECTS.LESSONS_LEARNED}>
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
