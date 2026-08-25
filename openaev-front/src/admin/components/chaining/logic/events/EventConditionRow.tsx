import {
  Select,
  SelectContent,
  SelectHelperText,
  SelectItem,
  SelectLabel,
  SelectTrigger,
  SelectValue,
} from '@filigran/design-system';
import { type DraggableProvidedDragHandleProps } from '@hello-pangea/dnd';
import { DeleteOutline, DragHandleOutlined, InfoOutlined } from '@mui/icons-material';
import { Box, IconButton, Switch, TextField, Tooltip, Typography } from '@mui/material';
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

  const handleFieldChange = (value: ConditionKeyType) => {
    onUpdate({
      ...condition,
      field: value,
    });
  };

  const handleOperatorChange = (value: ComparisonOperator) => {
    const newOp = value;
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
      <div style={{ minWidth: 140 }}>
        <Select
          value={condition.field}
          onValueChange={value => handleFieldChange(value as ConditionKeyType)}
          disabled={isArgumentTypesUnavailable}
        >
          <SelectLabel>{t('Field to Check')}</SelectLabel>
          <SelectTrigger>
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
        >
          <SelectLabel>{t('Operator')}</SelectLabel>
          <SelectTrigger>
            <SelectValue placeholder={t('Operator')} />
          </SelectTrigger>
          <SelectContent>
            {COMPARISON_OPERATORS.map(op => (
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
