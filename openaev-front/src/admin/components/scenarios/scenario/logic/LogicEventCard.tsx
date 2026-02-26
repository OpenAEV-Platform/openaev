import { DeleteOutlined, EditOutlined } from '@mui/icons-material';
import { Card, CardActions, CardContent, Chip, IconButton, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../../../components/i18n';
import type { WorkflowCondition, WorkflowStep } from '../../../../../utils/api-types-custom';
import { getStepLabel } from './logicUtils';

interface Props {
  step: WorkflowStep;
  allSteps: WorkflowStep[];
  onEdit: (step: WorkflowStep) => void;
  onDelete: (stepId: string) => void;
}

const LogicEventCard: FunctionComponent<Props> = ({ step, allSteps, onEdit, onDelete }) => {
  const { t } = useFormatter();
  const theme = useTheme();

  const getStepFromLabel = (condition: WorkflowCondition): string => {
    if (!condition.step_from_id) return t('None');
    const fromStep = allSteps.find(s => s.step_id === condition.step_from_id);
    return fromStep ? getStepLabel(fromStep) : condition.step_from_id.substring(0, 8);
  };

  return (
    <Card
      variant="outlined"
      sx={{
        mb: 2,
        borderLeft: `4px solid ${theme.palette.warning.main}`,
      }}
    >
      <CardContent sx={{ pb: 1 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <Typography variant="subtitle1" fontWeight="bold">
            {getStepLabel(step)}
          </Typography>
          <Chip
            size="small"
            label={t('Event')}
            color="warning"
            variant="outlined"
          />
        </div>
        {step.step_conditions.length > 0 && (
          <div style={{ marginTop: theme.spacing(1) }}>
            <Typography variant="caption" color="text.secondary" sx={{ mb: 0.5, display: 'block' }}>
              {t('Trigger conditions:')}
            </Typography>
            {step.step_conditions.map(condition => (
              <div
                key={condition.condition_id}
                style={{
                  display: 'flex',
                  gap: 8,
                  alignItems: 'center',
                  marginBottom: 4,
                }}
              >
                <Chip size="small" label={condition.condition_type} variant="outlined" />
                {condition.condition_key && (
                  <Typography variant="body2">
                    {condition.condition_key} = {condition.condition_value ?? '?'}
                  </Typography>
                )}
                <Typography variant="caption" color="text.secondary">
                  {t('from')} {getStepFromLabel(condition)}
                </Typography>
              </div>
            ))}
          </div>
        )}
      </CardContent>
      <CardActions sx={{ justifyContent: 'flex-end', pt: 0 }}>
        <IconButton size="small" onClick={() => onEdit(step)}>
          <EditOutlined fontSize="small" />
        </IconButton>
        <IconButton size="small" onClick={() => onDelete(step.step_id)} color="error">
          <DeleteOutlined fontSize="small" />
        </IconButton>
      </CardActions>
    </Card>
  );
};

export default LogicEventCard;
