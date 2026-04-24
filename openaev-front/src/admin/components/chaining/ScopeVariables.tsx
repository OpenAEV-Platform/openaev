import { Paper, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { ScopeVariableOutput, WorkflowConfigurationOutput } from '../../../utils/api-types';
import { useFormatter } from '../../../components/i18n';


interface ScopeVariablesProps { workflowConfiguration: WorkflowConfigurationOutput | undefined }

const ScopeVariables = ({ workflowConfiguration }: ScopeVariablesProps) => {
  const { t } = useFormatter();
  const theme = useTheme();

  const variables: ScopeVariableOutput[] = workflowConfiguration?.workflow_scope_variables ?? [];

  return (
    <div style={{
      display: 'grid',
      gridTemplateRows: 'min-content 1fr',
      gap: theme.spacing(1),
    }}
    >
      <Typography variant="h4">
        {t('Variables')}
      </Typography>
      <Paper sx={{ padding: theme.spacing(2) }} variant="outlined">
        {variables.length > 0 ? (
          <div style={{ display: 'grid', gap: theme.spacing(1) }}>
            {variables.map(variable => (
              <div
                key={variable.scope_variable_id}
                style={{
                  display: 'grid',
                  gridTemplateColumns: '1fr 2fr',
                  gap: theme.spacing(1),
                  alignItems: 'center',
                }}
              >
                <Typography variant="body2" sx={{ fontWeight: 600 }}>
                  {variable.scope_variable_key}
                </Typography>
                <Typography variant="body2" sx={{ color: 'text.secondary' }}>
                  {variable.scope_variable_value ?? '—'}
                </Typography>
              </div>
            ))}
          </div>
        ) : (
          <Typography variant="body2" sx={{ color: 'text.disabled' }}>
            {t('No variable defined yet.')}
          </Typography>
        )}
      </Paper>
    </div>
  );
};

export default ScopeVariables;
