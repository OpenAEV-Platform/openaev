import { Add, DataObjectOutlined, DeleteOutlined } from '@mui/icons-material';
import {
  Box,
  Chip,
  IconButton,
  Paper,
  Tooltip,
  Typography,
} from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { useState } from 'react';

import { useFormatter } from '../../../components/i18n';
import {
  type ScopeVariableInput,
  type ScopeVariableOutput,
  type WorkflowConfigurationInput,
  type WorkflowConfigurationOutput,
} from '../../../utils/api-types';
import ScopeVariableCreateDialog from './ScopeVariableCreateDialog';

interface ScopeVariablesProps {
  workflowConfiguration: WorkflowConfigurationOutput | undefined;
  onUpdate: (overrides: Partial<WorkflowConfigurationInput>) => void;
}

const ScopeVariables = ({ workflowConfiguration, onUpdate }: ScopeVariablesProps) => {
  const { t } = useFormatter();
  const theme = useTheme();

  const variables: ScopeVariableOutput[] = workflowConfiguration?.workflow_scope_variables ?? [];

  const [open, setOpen] = useState(false);

  const toInput = (v: ScopeVariableOutput): ScopeVariableInput => ({
    scope_variable_id: v.scope_variable_id,
    scope_variable_key: v.scope_variable_key ?? '',
    scope_variable_type: v.scope_variable_type ?? 'text',
    scope_variable_value: v.scope_variable_value ?? '',
    scope_variable_description: v.scope_variable_description,
  });

  const handleCreate = (data: Omit<ScopeVariableInput, 'scope_variable_id'>) => {
    onUpdate({
      workflow_scope_variables: [
        ...variables.map(toInput),
        data,
      ],
    });
  };

  const handleDelete = (id: string | undefined) => {
    onUpdate({ workflow_scope_variables: variables.filter(v => v.scope_variable_id !== id).map(toInput) });
  };

  return (
    <Paper
      variant="outlined"
      sx={{
        height: '100%',
        display: 'grid',
        gridTemplateRows: 'min-content 1fr',
        gap: 1.5,
        minHeight: 168,
        p: theme.spacing(2),
      }}
    >
      {/* Header */}
      <Box sx={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        gap: theme.spacing(2),
      }}
      >
        <Box sx={{
          display: 'flex',
          alignItems: 'center',
          gap: theme.spacing(1),
          color: 'text.secondary',
        }}
        >
          <DataObjectOutlined fontSize="small" />
          <Typography variant="subtitle2" sx={{ color: 'text.primary' }}>
            {t('Variables')}
          </Typography>
          <Chip
            label={variables.length}
            size="small"
            sx={{
              height: 20,
              minWidth: 24,
              fontWeight: 700,
              color: 'primary.main',
              backgroundColor: alpha(theme.palette.primary.main, 0.12),
            }}
          />
        </Box>
        <IconButton
          color="primary"
          size="small"
          onClick={() => setOpen(true)}
          aria-label={t('Add variable')}
        >
          <Add fontSize="small" />
        </IconButton>
      </Box>

      {/* List */}
      <Box sx={{ alignContent: 'start' }}>
        {variables.length > 0 ? (
          <div style={{
            display: 'grid',
            gridTemplateColumns: '1fr 1fr 1fr 1fr auto',
            gap: theme.spacing(1),
            alignItems: 'center',
          }}
          >
            {/* Column headers */}
            {[t('Key'), t('Type'), t('Value'), t('Description')].map(label => (
              <Typography
                key={label}
                variant="caption"
                sx={{
                  color: 'text.disabled',
                  fontWeight: 600,
                  textAlign: 'left',
                }}
              >
                {label}
              </Typography>
            ))}
            <span />

            {/* Rows */}
            {variables.map(variable => (
              <>
                <Typography
                  key={`key-${variable.scope_variable_id}`}
                  variant="body2"
                  sx={{
                    fontWeight: 600,
                    wordBreak: 'break-all',
                  }}
                >
                  {variable.scope_variable_key}
                </Typography>
                <Chip
                  key={`type-${variable.scope_variable_id}`}
                  label={variable.scope_variable_type ?? '—'}
                  size="small"
                  variant="outlined"
                  sx={{
                    justifySelf: 'start',
                    fontSize: '0.7rem',
                  }}
                />
                <Typography
                  key={`value-${variable.scope_variable_id}`}
                  variant="body2"
                  sx={{
                    color: 'text.secondary',
                    wordBreak: 'break-all',
                  }}
                >
                  {variable.scope_variable_value ?? '—'}
                </Typography>
                <Typography
                  key={`desc-${variable.scope_variable_id}`}
                  variant="body2"
                  sx={{
                    color: 'text.secondary',
                    fontStyle: variable.scope_variable_description ? 'normal' : 'italic',
                    wordBreak: 'break-all',
                  }}
                >
                  {variable.scope_variable_description ?? '—'}
                </Typography>
                <Tooltip key={`del-${variable.scope_variable_id}`} title={t('Delete variable')}>
                  <IconButton
                    size="small"
                    color="error"
                    onClick={() => handleDelete(variable.scope_variable_id)}
                    aria-label={t('Delete variable')}
                  >
                    <DeleteOutlined fontSize="small" />
                  </IconButton>
                </Tooltip>
              </>
            ))}
          </div>
        ) : (
          <Typography variant="body2" sx={{ color: 'text.disabled' }}>
            {t('No variable defined yet.')}
          </Typography>
        )}
      </Box>

      <ScopeVariableCreateDialog
        open={open}
        onClose={() => setOpen(false)}
        onSubmit={handleCreate}
        existingVariables={variables.map(variable => ({
          scope_variable_key: variable.scope_variable_key ?? '',
          scope_variable_type: variable.scope_variable_type ?? 'text',
        }))}
      />
    </Paper>
  );
};

export default ScopeVariables;
