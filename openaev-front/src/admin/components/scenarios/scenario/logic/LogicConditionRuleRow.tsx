import { DeleteOutlined } from '@mui/icons-material';
import { Autocomplete, Checkbox, FormControlLabel, IconButton, MenuItem, TextField } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

import FindingIcon from '../../../../../components/FindingIcon';
import { useFormatter } from '../../../../../components/i18n';
import type { ConditionType, WorkflowStep } from '../../../../../utils/api-types-custom';
import { getActionsProvisioningField, getStepLabel } from './logicUtils';

const FINDING_TYPES = [
  'text', 'number', 'port', 'portscan', 'ipv4', 'ipv6',
  'credentials', 'vulnerability', 'username', 'admin_username',
  'share', 'group', 'computer', 'password_policy', 'delegation',
  'sid', 'account_with_password_not_required', 'asreproastable_account',
  'kerberoastable_account',
];

const OPERATORS: { value: ConditionType; label: string }[] = [
  { value: 'EQ', label: 'Equals' },
  { value: 'NEQ', label: 'Not equals' },
  { value: 'GT', label: 'Greater than' },
  { value: 'GTE', label: 'Greater or equal' },
  { value: 'LT', label: 'Less than' },
  { value: 'LTE', label: 'Less or equal' },
  { value: 'IN', label: 'Contains' },
  { value: 'NIN', label: 'Not contains' },
  { value: 'IS_NULL', label: 'Is null' },
  { value: 'IS_NOT_NULL', label: 'Is not null' },
];

interface ConditionRule {
  key: string;
  operator: ConditionType;
  value: string;
  caseSensitive: boolean;
}

interface Props {
  rule: ConditionRule;
  onChange: (rule: ConditionRule) => void;
  onDelete: () => void;
  allSteps: WorkflowStep[];
}

const LogicConditionRuleRow: FunctionComponent<Props> = ({ rule, onChange, onDelete, allSteps }) => {
  const { t } = useFormatter();
  const theme = useTheme();

  const providers = rule.key ? getActionsProvisioningField(allSteps, rule.key) : [];
  const providerSuggestions = providers.map(s => getStepLabel(s));

  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: theme.spacing(1),
        marginBottom: theme.spacing(1),
      }}
    >
      <TextField
        select
        label={t('Field to inspect')}
        value={rule.key}
        onChange={e => onChange({ ...rule, key: e.target.value })}
        size="small"
        sx={{ minWidth: 180 }}
      >
        {FINDING_TYPES.map(type => (
          <MenuItem key={type} value={type}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <FindingIcon findingType={type} />
              {type}
            </div>
          </MenuItem>
        ))}
      </TextField>

      <TextField
        select
        label={t('Operator')}
        value={rule.operator}
        onChange={e => onChange({ ...rule, operator: e.target.value as ConditionType })}
        size="small"
        sx={{ minWidth: 150 }}
      >
        {OPERATORS.map(op => (
          <MenuItem key={op.value} value={op.value}>
            {t(op.label)}
          </MenuItem>
        ))}
      </TextField>

      <Autocomplete
        freeSolo
        options={providerSuggestions}
        value={rule.value}
        onInputChange={(_e, newValue) => onChange({ ...rule, value: newValue })}
        renderInput={params => (
          <TextField
            {...params}
            label={t('Expected value')}
            size="small"
            sx={{ minWidth: 200 }}
          />
        )}
        sx={{ flex: 1 }}
      />

      <FormControlLabel
        control={(
          <Checkbox
            checked={rule.caseSensitive}
            onChange={e => onChange({ ...rule, caseSensitive: e.target.checked })}
            size="small"
          />
        )}
        label={t('Case sensitive')}
        sx={{ whiteSpace: 'nowrap' }}
      />

      <IconButton size="small" onClick={onDelete} color="error">
        <DeleteOutlined fontSize="small" />
      </IconButton>
    </div>
  );
};

export type { ConditionRule };
export default LogicConditionRuleRow;
