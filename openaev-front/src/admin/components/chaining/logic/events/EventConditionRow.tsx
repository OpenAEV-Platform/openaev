import { type DraggableProvidedDragHandleProps } from '@hello-pangea/dnd';
import { DeleteOutline, DragHandleOutlined } from '@mui/icons-material';
import {
  Box,
  FormControl,
  IconButton,
  InputLabel,
  MenuItem,
  Select,
  type SelectChangeEvent,
  Switch,
  TextField,
  Typography,
} from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../../../components/i18n';
import {
  CASE_SENSITIVE_OPERATORS,
  COMPARISON_OPERATORS,
  type ComparisonOperator,
  CONDITION_KEY_TYPES,
  type ConditionKeyType,
  type EventCondition,
  formatConditionKeyLabel,
  OPERATOR_LABELS,
  UNARY_OPERATORS,
} from './event-types';

interface Props {
  condition: EventCondition;
  dragHandleProps?: DraggableProvidedDragHandleProps | null;
  onUpdate: (updated: EventCondition) => void;
  onDelete: () => void;
  canDelete: boolean;
}

const EventConditionRow: FunctionComponent<Props> = ({
  condition,
  dragHandleProps,
  onUpdate,
  onDelete,
  canDelete,
}) => {
  const { t } = useFormatter();
  const theme = useTheme();

  const handleFieldChange = (e: SelectChangeEvent<ConditionKeyType>) => {
    onUpdate({
      ...condition,
      field: e.target.value,
    });
  };

  const handleOperatorChange = (e: SelectChangeEvent<ComparisonOperator>) => {
    const newOp = e.target.value;
    const updated: EventCondition = {
      ...condition,
      operator: newOp,
    };
    if (UNARY_OPERATORS.includes(newOp)) {
      updated.value = '';
    }
    onUpdate(updated);
  };

  const handleValueChange = (value: string) => {
    onUpdate({
      ...condition,
      value,
    });
  };

  const handleCaseSensitiveToggle = () => {
    onUpdate({
      ...condition,
      caseSensitive: !condition.caseSensitive,
    });
  };

  const showValue = !UNARY_OPERATORS.includes(condition.operator);
  const showCaseSensitive = CASE_SENSITIVE_OPERATORS.includes(condition.operator);

  return (
    <Box sx={{
      display: 'flex',
      alignItems: 'center',
      gap: '8px',
      padding: '8px 12px',
      borderRadius: 1,
      backgroundColor: 'background.paper',
      width: '100%',
    }}
    >
      {/* Drag handle icon */}
      <span
        {...(dragHandleProps ?? {})}
        style={{
          display: 'flex',
          alignItems: 'center',
          cursor: 'grab',
        }}
      >
        <DragHandleOutlined sx={{
          color: 'text.secondary',
          fontSize: 20,
        }}
        />
      </span>

      {/* Field to check */}
      <FormControl size="small" sx={{ minWidth: 140 }}>
        <InputLabel>{t('Field to Check')}</InputLabel>
        <Select<ConditionKeyType>
          label={t('Field to Check')}
          value={condition.field}
          onChange={handleFieldChange}
        >
          {CONDITION_KEY_TYPES.map(key => (
            <MenuItem key={key} value={key}>
              {formatConditionKeyLabel(key)}
            </MenuItem>
          ))}
        </Select>
      </FormControl>

      {/* Operator */}
      <FormControl size="small" sx={{ minWidth: 130 }}>
        <InputLabel>{t('Operator')}</InputLabel>
        <Select<ComparisonOperator>
          label={t('Operator')}
          value={condition.operator}
          onChange={handleOperatorChange}
        >
          {COMPARISON_OPERATORS.map(op => (
            <MenuItem key={op} value={op}>
              {t(OPERATOR_LABELS[op])}
            </MenuItem>
          ))}
        </Select>
      </FormControl>

      {/* Expected value */}
      {showValue && (
        <TextField
          label={t('Expected Value')}
          size="small"
          value={condition.value}
          onChange={e => handleValueChange(e.target.value)}
          sx={{ flex: 1 }}
        />
      )}
      {!showValue && <Box sx={{ flex: 1 }} />}

      {/* Case sensitivity toggle + delete — always pushed to the right */}
      <Box sx={{
        display: 'flex',
        alignItems: 'center',
        gap: 1,
        flexShrink: 0,
      }}
      >
        {showCaseSensitive && (
          <div style={{
            display: 'flex',
            alignItems: 'center',
            gap: 2,
          }}
          >
            <Switch
              size="small"
              checked={condition.caseSensitive}
              onChange={handleCaseSensitiveToggle}
              color="primary"
            />
            <Typography
              variant="caption"
              sx={{
                fontWeight: 600,
                whiteSpace: 'nowrap',
              }}
            >
              {t('Aa')}
            </Typography>
          </div>
        )}

        {/* Delete button — only visible when more than one condition */}
        {canDelete && (
          <IconButton
            size="small"
            onClick={onDelete}
            sx={{
              'color': 'error.main',
              'border': '1px solid',
              'borderColor': 'error.main',
              'borderRadius': 1,
              '&:hover': { backgroundColor: `${theme.palette.error.main}1A` },
            }}
            aria-label={t('Delete condition')}
          >
            <DeleteOutline fontSize="small" />
          </IconButton>
        )}
      </Box>
    </Box>
  );
};

export default EventConditionRow;
