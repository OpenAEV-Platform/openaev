import { type DraggableProvidedDragHandleProps } from '@hello-pangea/dnd';
import { DeleteOutline, DragHandleOutlined, InfoOutlined } from '@mui/icons-material';
import {
  Box,
  FormControl,
  FormHelperText,
  IconButton,
  InputLabel,
  MenuItem,
  Select,
  type SelectChangeEvent,
  Switch,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../../../components/i18n';
import useArgumentTypes from '../../../threat_arsenal/form/useArgumentTypes';
import ActionTypeIcon from '../ActionTypeIcon';
import { useOutputProviders } from '../useOutputProviders';
import {
  CASE_SENSITIVE_OPERATORS,
  COMPARISON_OPERATORS,
  type ComparisonOperator,
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
  readOnly?: boolean;
}

const EventConditionRow: FunctionComponent<Props> = ({
  condition,
  dragHandleProps,
  onUpdate,
  onDelete,
  canDelete,
  readOnly = false,
}) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const { argumentTypes, isLoading: isLoadingArgumentTypes, error: argumentTypesError } = useArgumentTypes();
  const conditionKeyTypes = argumentTypes;
  const isArgumentTypesUnavailable = isLoadingArgumentTypes || !!argumentTypesError || conditionKeyTypes.length === 0;
  const { providers } = useOutputProviders();

  /**
     * Build tooltip content for a given output type's providers.
     * Shows each action with its associated icon.
     */
  const buildProviderTooltip = (keyType: string) => {
    const keyProviders = providers[keyType] ?? [];
    if (keyProviders.length === 0) return '';
    const header = t('Actions on the logic flow which produce this input:');
    return (
      <Box>
        <Typography variant="caption">{header}</Typography>
        {keyProviders.map(p => (
          <Box
            key={p.stepId}
            sx={{
              display: 'flex',
              alignItems: 'center',
              gap: 0.5,
            }}
          >
            <ActionTypeIcon
              injectorType={p.injectorType}
              payloadType={p.payloadType}
              isPayload={p.isPayload}
            />
            <Typography variant="caption">{p.actionTitle}</Typography>
          </Box>
        ))}
      </Box>
    );
  };

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
          cursor: readOnly ? 'default' : 'grab',
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
          disabled={readOnly || isArgumentTypesUnavailable}
          renderValue={val => formatConditionKeyLabel(val)}
        >
          {isLoadingArgumentTypes && (
            <MenuItem disabled>{t('Loading argument types...')}</MenuItem>
          )}
          {!isLoadingArgumentTypes && argumentTypesError && (
            <MenuItem disabled>{t('Failed to load argument types')}</MenuItem>
          )}
          {!isLoadingArgumentTypes && !argumentTypesError && conditionKeyTypes.map((key) => {
            const keyProviders = providers[key] ?? [];
            return (
              <MenuItem
                key={key}
                value={key}
                sx={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 1,
                }}
              >
                <span style={{ flex: 1 }}>{formatConditionKeyLabel(key)}</span>
                {keyProviders.length > 0 && (
                  <Tooltip
                    title={buildProviderTooltip(key)}
                    placement="right"
                  >
                    <InfoOutlined sx={{
                      fontSize: 16,
                      color: 'info.main',
                      flexShrink: 0,
                    }}
                    />
                  </Tooltip>
                )}
              </MenuItem>
            );
          })}
        </Select>
        {isLoadingArgumentTypes && (
          <FormHelperText>{t('Loading argument types...')}</FormHelperText>
        )}
        {!isLoadingArgumentTypes && argumentTypesError && (
          <FormHelperText error>{t('Failed to load argument types')}</FormHelperText>
        )}
      </FormControl>

      {/* Operator */}
      <FormControl size="small" sx={{ minWidth: 130 }}>
        <InputLabel>{t('Operator')}</InputLabel>
        <Select<ComparisonOperator>
          label={t('Operator')}
          value={condition.operator}
          onChange={handleOperatorChange}
          disabled={readOnly}
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
          disabled={readOnly}
          sx={{ flex: 1 }}
        />
      )}
      {!showValue && <Box sx={{ flex: 1 }} />}

      <Box sx={{
        display: 'flex',
        alignItems: 'center',
        gap: 1,
        flexShrink: 0,
      }}
      >
        {showCaseSensitive && (
          <Tooltip title={condition.caseSensitive ? t('Case-sensitive') : t('Case-insensitive')}>
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
                disabled={readOnly}
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
          </Tooltip>
        )}

        {/* Delete button (only visible when more than one condition) */}
        {canDelete && (
          <IconButton
            size="small"
            onClick={onDelete}
            disabled={readOnly}
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
