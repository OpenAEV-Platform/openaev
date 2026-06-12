import { Add, DeleteOutlined } from '@mui/icons-material';
import { Alert, Box, Button, Divider, Paper, TextField, ToggleButton, ToggleButtonGroup, Tooltip, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useEffect, useState } from 'react';

import Drawer from '../../../../../components/common/Drawer';
import { useFormatter } from '../../../../../components/i18n';
import type { ConditionType, WorkflowCondition, WorkflowStep } from '../../../../../utils/api-types-custom';
import LogicConditionRuleRow, { type ConditionRule } from './LogicConditionRuleRow';
import { getStepLabel } from './logicUtils';

// ─── Data model ──────────────────────────────────────────────────────────────

export interface ConditionGroup {
  id: string;
  itemType: 'group';
  groupOperator: 'AND' | 'OR';
  conditions: ConditionRule[];
}

export interface ConditionSingle {
  id: string;
  itemType: 'single';
  condition: ConditionRule;
}

export type ConditionItem = ConditionSingle | ConditionGroup;

let _seq = 0;
const uid = () => `ci_${Date.now()}_${_seq++}`;

const emptyRule = (): ConditionRule => ({
  key: '', field: '', operator: 'EQ', value: '', caseSensitive: false,
});

// ─── Parse backend conditions into ConditionItem[] ───────────────────────────

const parseConditionItems = (rawConditions: WorkflowCondition[]): {
  items: ConditionItem[];
  topOperator: 'AND' | 'OR';
} => {
  // Find root (no parent, type AND/OR, no condition_key, no step_from_id)
  const root = rawConditions.find(
    c => !c.condition_parent_id && (c.condition_type === 'AND' || c.condition_type === 'OR') && !c.condition_key && !c.step_from_id,
  );
  const topOperator: 'AND' | 'OR' = (root?.condition_type as 'AND' | 'OR') ?? 'AND';

  // Direct children of root
  const rootChildren = root
    ? rawConditions.filter(c => c.condition_parent_id === root.condition_id)
    : rawConditions.filter(c => !c.condition_parent_id && c.condition_key);

  const items: ConditionItem[] = [];

  for (const child of rootChildren) {
    if (child.condition_type === 'AND' || child.condition_type === 'OR') {
      // It's a group node — gather its leaf children
      const leafChildren = rawConditions.filter(c => c.condition_parent_id === child.condition_id && c.condition_key);
      items.push({
        id: uid(),
        itemType: 'group',
        groupOperator: child.condition_type as 'AND' | 'OR',
        conditions: leafChildren.map(c => ({
          key: c.condition_key ?? '',
          field: c.condition_field ?? '',
          operator: c.condition_type as ConditionType,
          value: c.condition_value ?? '',
          caseSensitive: false,
        })),
      });
    } else if (child.condition_key) {
      // Standalone condition
      items.push({
        id: uid(),
        itemType: 'single',
        condition: {
          key: child.condition_key,
          field: child.condition_field ?? '',
          operator: child.condition_type as ConditionType,
          value: child.condition_value ?? '',
          caseSensitive: false,
        },
      });
    }
  }

  return { items, topOperator };
};

// ─── Props ────────────────────────────────────────────────────────────────────

interface Props {
  open: boolean;
  handleClose: () => void;
  onSubmit: (data: {
    label: string;
    description: string;
    conditionItems: ConditionItem[];
    topOperator: 'AND' | 'OR';
  }) => void;
  editingStep?: WorkflowStep | null;
  allSteps: WorkflowStep[];
  parentActionStepId?: string | null;
}

// ─── Component ────────────────────────────────────────────────────────────────

const LogicEventForm: FunctionComponent<Props> = ({
  open,
  handleClose,
  onSubmit,
  editingStep,
  allSteps,
  parentActionStepId,
}) => {
  const { t } = useFormatter();
  const theme = useTheme();

  const [label, setLabel] = useState('');
  const [description, setDescription] = useState('');
  const [topOperator, setTopOperator] = useState<'AND' | 'OR'>('AND');
  const [items, setItems] = useState<ConditionItem[]>([]);

  useEffect(() => {
    if (editingStep) {
      setLabel(getStepLabel(editingStep));
      try {
        const data = JSON.parse(editingStep.step_data ?? '{}');
        setDescription(data.event_description ?? '');
      } catch { setDescription(''); }

      const { items: parsedItems, topOperator: parsedOp } = parseConditionItems(
        editingStep.step_conditions.filter(c => c.condition_type !== 'DEPEND_ON'),
      );
      setItems(parsedItems);
      setTopOperator(parsedOp);
    } else {
      setLabel('');
      setDescription('');
      setTopOperator('AND');
      setItems([]);
    }
  }, [editingStep, open]);

  // ── Mutations ──────────────────────────────────────────────────────────────

  const addSingleCondition = () => {
    setItems(prev => [...prev, { id: uid(), itemType: 'single', condition: emptyRule() }]);
  };

  const addGroup = () => {
    setItems(prev => [...prev, {
      id: uid(), itemType: 'group', groupOperator: 'AND', conditions: [emptyRule()],
    }]);
  };

  const removeItem = (itemId: string) => {
    setItems(prev => prev.filter(i => i.id !== itemId));
  };

  const updateSingle = (itemId: string, rule: ConditionRule) => {
    setItems(prev => prev.map(i => i.id === itemId && i.itemType === 'single' ? { ...i, condition: rule } : i));
  };

  const updateGroupOperator = (itemId: string, op: 'AND' | 'OR') => {
    setItems(prev => prev.map(i => i.id === itemId && i.itemType === 'group' ? { ...i, groupOperator: op } : i));
  };

  const addConditionToGroup = (itemId: string) => {
    setItems(prev => prev.map(i => i.id === itemId && i.itemType === 'group'
      ? { ...i, conditions: [...i.conditions, emptyRule()] }
      : i));
  };

  const updateConditionInGroup = (itemId: string, index: number, rule: ConditionRule) => {
    setItems(prev => prev.map(i => i.id === itemId && i.itemType === 'group'
      ? { ...i, conditions: i.conditions.map((c, ci) => ci === index ? rule : c) }
      : i));
  };

  const removeConditionFromGroup = (itemId: string, index: number) => {
    setItems(prev => prev.map(i => {
      if (i.id !== itemId || i.itemType !== 'group') return i;
      const next = i.conditions.filter((_, ci) => ci !== index);
      return next.length === 0 ? null : { ...i, conditions: next };
    }).filter(Boolean) as ConditionItem[]);
  };

  // ── Validation ─────────────────────────────────────────────────────────────

  const VALUE_OPERATORS = new Set(['EQ', 'NEQ', 'GT', 'GTE', 'LT', 'LTE', 'IN', 'NIN', 'STARTS_WITH', 'ENDS_WITH']);

  const isRuleValid = (r: ConditionRule) => r.key && !(VALUE_OPERATORS.has(r.operator) && !r.value);

  const hasInvalid = items.some(item =>
    item.itemType === 'single'
      ? !isRuleValid(item.condition)
      : item.conditions.some(c => !isRuleValid(c)),
  );

  const handleSubmit = () => {
    onSubmit({ label, description, conditionItems: items, topOperator });
    handleClose();
  };

  // ── Render ─────────────────────────────────────────────────────────────────

  return (
    <Drawer open={open} handleClose={handleClose} title={editingStep ? t('Edit event') : t('Create event')}>
      <div style={{ display: 'flex', flexDirection: 'column', gap: theme.spacing(2), padding: theme.spacing(2) }}>
        <TextField label={t('Event name')} fullWidth value={label} onChange={e => setLabel(e.target.value)} variant="standard" required />
        <TextField label={t('Description')} fullWidth value={description} onChange={e => setDescription(e.target.value)} variant="standard" multiline rows={2} />

        <Divider />

        {parentActionStepId && (
          <Alert severity="info" sx={{ mb: 1 }}>
            {t('Conditions below evaluate the output of the parent action.')}
          </Alert>
        )}

        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <Typography variant="subtitle2">
            {t('Trigger conditions')}
          </Typography>
          {items.length > 1 && (
            <ToggleButtonGroup value={topOperator} exclusive onChange={(_e, val) => { if (val) setTopOperator(val); }} size="small">
              <ToggleButton value="AND">{t('AND')}</ToggleButton>
              <ToggleButton value="OR">{t('OR')}</ToggleButton>
            </ToggleButtonGroup>
          )}
        </div>

        {items.map((item, itemIdx) => (
          <Box key={item.id}>
            {/* separator between items */}
            {itemIdx > 0 && (
              <Typography variant="caption" color="text.secondary" sx={{ display: 'block', textAlign: 'center', my: 0.5, fontWeight: 700 }}>
                {topOperator}
              </Typography>
            )}

            {item.itemType === 'single' ? (
              /* ── Standalone condition ── */
              <LogicConditionRuleRow
                rule={item.condition}
                index={0}
                onChange={rule => updateSingle(item.id, rule)}
                onDelete={() => removeItem(item.id)}
                allSteps={allSteps}
              />
            ) : (
              /* ── Condition group ── */
              <Paper variant="outlined" sx={{ p: 1.5, borderRadius: 2, position: 'relative' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: theme.spacing(1) }}>
                  <Typography variant="caption" color="text.secondary" fontWeight={700}>
                    {t('Condition Group')}
                  </Typography>
                  <div style={{ display: 'flex', alignItems: 'center', gap: theme.spacing(1) }}>
                    <ToggleButtonGroup value={item.groupOperator} exclusive onChange={(_e, val) => { if (val) updateGroupOperator(item.id, val); }} size="small">
                      <ToggleButton value="AND" sx={{ fontSize: 11, py: 0.25, px: 1 }}>{t('AND')}</ToggleButton>
                      <ToggleButton value="OR" sx={{ fontSize: 11, py: 0.25, px: 1 }}>{t('OR')}</ToggleButton>
                    </ToggleButtonGroup>
                    <Tooltip title={t('Remove group')}>
                      <span>
                        <Add
                          component="button"
                          onClick={() => removeItem(item.id)}
                          sx={{ display: 'none' }}
                        />
                      </span>
                    </Tooltip>
                    <Tooltip title={t('Remove group')}>
                      <DeleteOutlined
                        fontSize="small"
                        onClick={() => removeItem(item.id)}
                        sx={{ cursor: 'pointer', color: theme.palette.error.main, fontSize: 18 }}
                      />
                    </Tooltip>
                  </div>
                </div>

                {item.conditions.map((rule, ci) => (
                  <Box key={ci} sx={{ mb: 0.5 }}>
                    {ci > 0 && (
                      <Typography variant="caption" color="text.secondary" sx={{ display: 'block', textAlign: 'center', my: 0.25, fontWeight: 700 }}>
                        {item.groupOperator}
                      </Typography>
                    )}
                    <LogicConditionRuleRow
                      rule={rule}
                      index={ci}
                      onChange={updated => updateConditionInGroup(item.id, ci, updated)}
                      onDelete={() => removeConditionFromGroup(item.id, ci)}
                      allSteps={allSteps}
                    />
                  </Box>
                ))}

                <Button variant="outlined" size="small" startIcon={<Add />} onClick={() => addConditionToGroup(item.id)} sx={{ mt: 1 }}>
                  {t('Add condition')}
                </Button>
              </Paper>
            )}
          </Box>
        ))}

        <div style={{ display: 'flex', gap: theme.spacing(1) }}>
          <Button variant="outlined" size="small" startIcon={<Add />} onClick={addSingleCondition}>
            {t('Add condition')}
          </Button>
          <Button variant="outlined" size="small" startIcon={<Add />} onClick={addGroup}>
            {t('Add condition group')}
          </Button>
        </div>

        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: theme.spacing(1), marginTop: theme.spacing(2) }}>
          <Button variant="contained" onClick={handleClose}>{t('Cancel')}</Button>
          <Button
            variant="contained"
            color="secondary"
            onClick={handleSubmit}
            disabled={!label || hasInvalid}
          >
            {editingStep ? t('Update') : t('Create')}
          </Button>
        </div>
      </div>
    </Drawer>
  );
};

export default LogicEventForm;

