import { DeleteOutlined, EditOutlined } from '@mui/icons-material';
import { Card, CardActions, CardContent, Chip, IconButton, Tooltip, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

import FindingIcon from '../../../../../components/FindingIcon';
import { useFormatter } from '../../../../../components/i18n';
import type { WorkflowStep } from '../../../../../utils/api-types-custom';
import { extractOutputTypesFromStepData, getStepLabel } from './logicUtils';

interface Props {
  step: WorkflowStep;
  onEdit: (step: WorkflowStep) => void;
  onDelete: (stepId: string) => void;
}

const LogicActionCard: FunctionComponent<Props> = ({ step, onEdit, onDelete }) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const outputTypes = extractOutputTypesFromStepData(step);
  const label = getStepLabel(step);
  const isEvent = step.step_conditions.length > 0;

  return (
    <Card
      variant="outlined"
      sx={{
        mb: 2,
        borderLeft: `4px solid ${isEvent ? theme.palette.warning.main : theme.palette.primary.main}`,
      }}
    >
      <CardContent sx={{ pb: 1 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <Typography variant="subtitle1" fontWeight="bold">
            {label}
          </Typography>
          <Chip
            size="small"
            label={step.step_action_class}
            color="primary"
            variant="outlined"
          />
        </div>
        {step.step_field_scope && (
          <Chip
            size="small"
            label={`Scope: ${step.step_field_scope}`}
            sx={{ mt: 0.5, mr: 1 }}
            variant="outlined"
          />
        )}
        {outputTypes.length > 0 && (
          <div style={{ display: 'flex', gap: 4, marginTop: theme.spacing(1), flexWrap: 'wrap' }}>
            <Typography variant="caption" color="text.secondary" sx={{ mr: 1, alignSelf: 'center' }}>
              {t('Outputs:')}
            </Typography>
            {outputTypes.map(type => (
              <Tooltip key={type} title={type}>
                <Chip
                  size="small"
                  icon={<FindingIcon findingType={type} />}
                  label={type}
                  variant="outlined"
                />
              </Tooltip>
            ))}
          </div>
        )}
        {isEvent && step.step_conditions.length > 0 && (
          <div style={{ marginTop: theme.spacing(1) }}>
            <Typography variant="caption" color="text.secondary">
              {t('Conditions:')} {step.step_conditions.length}
            </Typography>
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

export default LogicActionCard;
