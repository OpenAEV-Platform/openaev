import { Select, SelectContent, SelectHelperText, SelectItem, SelectLabel, SelectTrigger, SelectValue, Switch, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { type DraggableProvidedDragHandleProps } from '@hello-pangea/dnd';
import { DeleteOutline, DragHandleOutlined, InfoOutlined } from '@mui/icons-material';
// fds:keep-mui the event condition value field stay on MUI until the AI/EE screens wave (deferred by ruling, IMPLEMENTATION-LOG.md 2026-09-15)
import { Box, IconButton, TextField, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useMemo } from 'react';

import { useFormatter } from '../../../../../components/i18n';
import useArgumentTypes from '../../../threat_arsenal/form/useArgumentTypes';
import ActionTypeIcon from '../ActionTypeIcon';
import { useOutputProviders } from '../useOutputProviders';
import {
  CASE_SENSITIVE_OPERATORS,
  type ComparisonOperator,
  type ConditionKeyType,
  type EventCondition,
  formatConditionKeyLabel,
  getAvailableOperators,
  getConditionValueError,
  isNumericField,
  OPERATOR_LABELS,
  resolveOperator,
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

// Helper texts are floated below their control so they never grow the row: otherwise the
// centred flex layout would drift the input upwards, out of line with the other fields.
const floatingHelperTextSx = {
  position: 'absolute',
  top: '100%',
  left: 0,
  right: 0,
  marginTop: '2px',
} as const;

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

  const handleFieldChange = (newField: ConditionKeyType) => {
    // The new field may not support the current operator (e.g. "greater than" on a text field)
    const newOperator = resolveOperator(newField, condition.operator);
    onUpdate({
      ...condition,
      field: newField,
      operator: newOperator,
      // Unary operators (IS_NULL / IS_NOT_NULL) take no value
      value: UNARY_OPERATORS.includes(newOperator) ? '' : condition.value,
    });
  };

  const handleOperatorChange = (newOp: ComparisonOperator) => {
    onUpdate({
      ...condition,
      operator: newOp,
      value: UNARY_OPERATORS.includes(newOp) ? '' : condition.value,
    });
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
  const showCaseSensitive = CASE_SENSITIVE_OPERATORS.includes(condition.operator)
    && !isNumericField(condition.field);
  // Only surface format errors: an untouched (empty) value already disables the submit button.
  const valueError = showValue && condition.value.trim() !== ''
    ? getConditionValueError(condition.field, condition.operator, condition.value)
    : undefined;
  const operatorOptions = useMemo(() => {
    const available = getAvailableOperators(condition.field);
    // Events stored before the field/operator restriction may carry an operator that is no longer
    // offered: keep it listed so the row renders its actual configuration instead of an empty select.
    return available.includes(condition.operator) ? available : [...available, condition.operator];
  }, [condition.field, condition.operator]);

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
      <div style={{ minWidth: 140 }}>
        <Select
          value={condition.field}
          onValueChange={value => handleFieldChange(value as ConditionKeyType)}
          disabled={readOnly || isArgumentTypesUnavailable}
          // The library declares `error` once on the root and propagates it by
          // context; main's `<FormHelperText error>` said the same thing locally.
          error={!isLoadingArgumentTypes && !!argumentTypesError}
        >
          <SelectLabel>{t('Field to Check')}</SelectLabel>
          <SelectTrigger style={{ minWidth: 140 }}>
            <span>{formatConditionKeyLabel(condition.field)}</span>
          </SelectTrigger>
          <SelectContent>
            {!isLoadingArgumentTypes && !argumentTypesError && conditionKeyTypes.map((key) => {
              const keyProviders = providers[key] ?? [];
              return (
                <SelectItem
                  key={key}
                  value={key}
                >
                  <span style={{ flex: 1 }}>{formatConditionKeyLabel(key)}</span>
                  {keyProviders.length > 0 && (
                    <Tooltip>
                      <TooltipTrigger asChild>
                        <InfoOutlined sx={{
                          fontSize: 16,
                          color: 'info.main',
                          flexShrink: 0,
                        }}
                        />
                      </TooltipTrigger>
                      {buildProviderTooltip(key) && <TooltipContent side="right">{buildProviderTooltip(key)}</TooltipContent>}
                    </Tooltip>
                  )}
                </SelectItem>
              );
            })}
          </SelectContent>
          {isLoadingArgumentTypes && (
            <SelectHelperText>{t('Loading argument types...')}</SelectHelperText>
          )}
          {!isLoadingArgumentTypes && argumentTypesError && (
            <SelectHelperText>{t('Failed to load argument types')}</SelectHelperText>
          )}
        </Select>
      </div>

      {/* Operator */}
      <div style={{ minWidth: 130 }}>
        <Select
          value={condition.operator}
          onValueChange={value => handleOperatorChange(value as ComparisonOperator)}
          disabled={readOnly}
        >
          <SelectLabel>{t('Operator')}</SelectLabel>
          <SelectTrigger style={{ minWidth: 130 }}>
            <SelectValue placeholder={t('Operator')} />
          </SelectTrigger>
          <SelectContent>
            {operatorOptions.map(op => (
              <SelectItem key={op} value={op}>
                {t(OPERATOR_LABELS[op])}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {/* Expected value */}
      {showValue && (
        <TextField
          label={t('Expected Value')}
          size="small"
          value={condition.value}
          onChange={e => handleValueChange(e.target.value)}
          disabled={readOnly}
          error={!!valueError}
          helperText={valueError ? t(valueError) : undefined}
          slotProps={{ formHelperText: { sx: floatingHelperTextSx } }}
          sx={{
            flex: 1,
            position: 'relative',
          }}
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
          <Tooltip>
            <TooltipTrigger asChild>
              <div style={{
                display: 'flex',
                alignItems: 'center',
                gap: 2,
              }}
              >
                {/* The visible "Aa" is a caption beside the control, and the
                  tooltip text follows the state — neither can be the name, so
                  the switch carries a stable one of its own. */}
                <Switch
                  aria-label={t('Case-sensitive')}
                  checked={condition.caseSensitive}
                  onCheckedChange={handleCaseSensitiveToggle}
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
            </TooltipTrigger>
            <TooltipContent>{condition.caseSensitive ? t('Case-sensitive') : t('Case-insensitive')}</TooltipContent>
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
