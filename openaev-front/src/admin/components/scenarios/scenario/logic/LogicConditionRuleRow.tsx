import { DeleteOutlined } from '@mui/icons-material';
import { Autocomplete, IconButton, MenuItem, Switch, TextField, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useEffect, useMemo, useState } from 'react';

import FindingIcon from '../../../../../components/FindingIcon';
import { useFormatter } from '../../../../../components/i18n';
import { simpleCall } from '../../../../../utils/Action';
import type { ConditionType, OutputTypeDescriptor, WorkflowStep } from '../../../../../utils/api-types-custom';
import InjectIcon from '../../../common/injects/InjectIcon';
import { getActionsProvisioningField, getStepLabel, getStepInjectorType } from './logicUtils';

// Singleton cache for output types catalog (shared with PayloadArgumentsField)
let outputTypesCachePromise: Promise<OutputTypeDescriptor[]> | null = null;
const fetchOutputTypesCatalog = (): Promise<OutputTypeDescriptor[]> => {
  if (!outputTypesCachePromise) {
    outputTypesCachePromise = simpleCall('/api/output_types')
      .then((res: { data: OutputTypeDescriptor[] }) => res.data)
      .catch(() => [] as OutputTypeDescriptor[]);
  }
  return outputTypesCachePromise;
};

const ALL_OPERATORS: { value: ConditionType; label: string }[] = [
  { value: 'EQ', label: 'Equals' },
  { value: 'NEQ', label: 'NotEquals' },
  { value: 'IN', label: 'Contains' },
  { value: 'NIN', label: 'NotContains' },
  { value: 'STARTS_WITH', label: 'StartsWith' },
  { value: 'ENDS_WITH', label: 'EndsWith' },
  { value: 'IS_NULL', label: 'IsNull' },
  { value: 'IS_NOT_NULL', label: 'IsNotNull' },
];

const TYPE_ONLY_OPERATORS: { value: ConditionType; label: string }[] = [
  { value: 'IS_NOT_NULL', label: 'IsNotNull' },
  { value: 'IS_NULL', label: 'IsNull' },
];

const NO_VALUE_OPERATORS = new Set<string>(['IS_NULL', 'IS_NOT_NULL']);

interface ConditionRule {
  key: string;       // finding type
  field: string;     // sub-field (empty string = no sub-field)
  operator: ConditionType;
  value: string;
  caseSensitive: boolean;
}

interface Props {
  rule: ConditionRule;
  index: number;
  onChange: (rule: ConditionRule) => void;
  onDelete: () => void;
  allSteps: WorkflowStep[];
}

const LogicConditionRuleRow: FunctionComponent<Props> = ({ rule, index, onChange, onDelete, allSteps }) => {
  const { t } = useFormatter();
  const theme = useTheme();

  const [outputTypes, setOutputTypes] = useState<OutputTypeDescriptor[]>([]);

  useEffect(() => {
    fetchOutputTypesCatalog().then(setOutputTypes);
  }, []);

  const FILTER_TYPE_ALLOWLIST = new Set([
    'username', 'password', 'hash', 'token', 'ticket', 'share', 'cve',
  ]);

  const typeItems = useMemo(() =>
    outputTypes
      .filter(ot => ot.fields.length === 0 || FILTER_TYPE_ALLOWLIST.has(ot.outputType))
      .map(ot => ot.outputType),
  [outputTypes]);

  const availableOperators = ALL_OPERATORS;
  const showValueField = !NO_VALUE_OPERATORS.has(rule.operator);

  const providers = rule.key ? getActionsProvisioningField(allSteps, rule.key) : [];

  const handleTypeChange = (newType: string) => {
    onChange({ ...rule, key: newType, field: '', operator: 'EQ', value: '' });
  };

  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: theme.spacing(1),
        marginBottom: theme.spacing(1),
      }}
    >
      <Typography variant="body2" sx={{ fontWeight: 'bold', whiteSpace: 'nowrap', minWidth: 28 }}>
        {`# ${index + 1}`}
      </Typography>

      {/* Step 1: Finding type */}
      <TextField
        select
        label={t('Filter type')}
        value={rule.key}
        onChange={e => handleTypeChange(e.target.value)}
        size="small"
        sx={{ minWidth: 160 }}
      >
        {typeItems.map(type => (
          <MenuItem key={type} value={type}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <FindingIcon findingType={type} />
              {type}
            </div>
          </MenuItem>
        ))}
      </TextField>

      {/* Operator */}
      <TextField
        select
        label={t('Operator')}
        value={rule.operator}
        onChange={e => onChange({ ...rule, operator: e.target.value as ConditionType })}
        size="small"
        sx={{ minWidth: 140 }}
      >
        {availableOperators.map(op => (
          <MenuItem key={op.value} value={op.value}>
            {t(op.label)}
          </MenuItem>
        ))}
      </TextField>

      {/* Value (only when sub-field selected and operator needs a value) */}
      {showValueField && (
        <div style={{ flex: 1, minWidth: 160 }}>
          <Autocomplete
            freeSolo
            options={[]}
            value={rule.value}
            onInputChange={(_e, newValue) => onChange({ ...rule, value: newValue })}
            renderInput={params => (
              <TextField
                {...params}
                label={t('Expected value')}
                size="small"
              />
            )}
          />
        </div>
      )}

      {/* Spacer when no value field */}
      {!showValueField && <div style={{ flex: 1 }} />}

      {/* Provider hints */}
      {providers.length > 0 && (
        <div style={{ display: 'flex', alignItems: 'center', gap: 4, flexWrap: 'wrap' }}>
          {providers.map(provider => (
            <div key={provider.step_id} style={{ display: 'inline-flex', alignItems: 'center', gap: 2 }}>
              <InjectIcon type={getStepInjectorType(provider) ?? undefined} size="small" variant="inline" />
              <Typography variant="caption" color="text.secondary">
                {getStepLabel(provider)}
              </Typography>
            </div>
          ))}
        </div>
      )}

      {showValueField && (
        <>
          <Switch
            checked={rule.caseSensitive}
            onChange={e => onChange({ ...rule, caseSensitive: e.target.checked })}
            size="small"
          />
          <Typography variant="caption" sx={{ whiteSpace: 'nowrap' }}>
            {t('Case sensitive')}
          </Typography>
        </>
      )}

      <IconButton size="small" onClick={onDelete} color="error">
        <DeleteOutlined fontSize="small" />
      </IconButton>
    </div>
  );
};

export type { ConditionRule };
export default LogicConditionRuleRow;
