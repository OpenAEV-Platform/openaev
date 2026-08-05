import { DragDropContext, type DropResult } from '@hello-pangea/dnd';
import { zodResolver } from '@hookform/resolvers/zod';
import { AddOutlined } from '@mui/icons-material';
import { Box, Button, Typography } from '@mui/material';
import { type FunctionComponent, useCallback, useState } from 'react';
import { FormProvider, useForm } from 'react-hook-form';
import { z } from 'zod';

import TextFieldController from '../../../../../components/fields/TextFieldController';
import { useFormatter } from '../../../../../components/i18n';
import ActionFormButtons from '../drawer/ActionFormButtons';
import ConditionGroupBuilder from './ConditionGroupBuilder';
import {
  type ConditionGroup,
  createEmptyGroup,
  type EventFormData,
  generateId,
  isEventFormValid,
  type LogicalOperator,
} from './event-types';
import LogicalOperatorSelect from './LogicalOperatorSelect';

/**
 * Seed one AND group with an "is not null" condition per field, so an "add trigger after action"
 * insertion starts pre-wired to fire on the upstream action's output types.
 */
const buildPrefilledGroups = (fields: string[]): ConditionGroup[] => ([{
  id: generateId(),
  operator: 'AND',
  conditions: fields.map(field => ({
    id: generateId(),
    field,
    operator: 'IS_NOT_NULL' as const,
    value: '',
    caseSensitive: true,
  })),
  subGroups: [],
}]);

// Defined outside the component so the resolver reference is stable across renders
const eventBaseSchema = z.object({
  event_name: z.string().min(1),
  event_description: z.string(),
});
type EventBaseInput = z.infer<typeof eventBaseSchema>;

interface EventCreationFormProps {
  onSubmit: (data: EventFormData) => void;
  onCancel: () => void;
  initialData?: EventFormData;
  submitLabel?: string;
  defaultName?: string;
  /** Output types of an upstream action - seeds the first condition group (inline "add trigger"). */
  prefillFields?: string[];
}

const EventCreationForm: FunctionComponent<EventCreationFormProps> = ({
  onSubmit,
  onCancel,
  initialData,
  submitLabel,
  defaultName,
  prefillFields,
}) => {
  const { t } = useFormatter();
  const methods = useForm<EventBaseInput>({
    mode: 'onChange',
    resolver: zodResolver(eventBaseSchema),
    defaultValues: {
      event_name: initialData?.name ?? defaultName ?? '',
      event_description: initialData?.description ?? '',
    },
  });

  const { handleSubmit, formState: { isValid: isFormValid } } = methods;

  // -- Condition groups as local state --
  const [groupOperators, setGroupOperators] = useState<LogicalOperator[]>(
    initialData?.groupOperators ?? [],
  );
  const [conditionGroups, setConditionGroups] = useState<ConditionGroup[]>(() => {
    if (initialData?.conditionGroups) return initialData.conditionGroups;
    if (prefillFields && prefillFields.length > 0) return buildPrefilledGroups(prefillFields);
    return [createEmptyGroup('AND')];
  });

  const handleUpdateGroup = useCallback((index: number, updatedGroup: ConditionGroup) => {
    setConditionGroups(prev => prev.map((group, i) => (i === index ? updatedGroup : group)));
  }, []);

  const handleDeleteGroup = useCallback((index: number) => {
    setConditionGroups(prev => prev.filter((_, i) => i !== index));
    setGroupOperators(prev => prev.filter((_, i) => i !== Math.max(0, index - 1)));
  }, []);

  const handleAddConditionGroup = useCallback(() => {
    setConditionGroups(prev => [...prev, createEmptyGroup('AND')]);
    // Preserve the current operator so the new gap stays in sync with the others
    setGroupOperators(prev => [...prev, prev[0] ?? 'AND']);
  }, []);

  const handleUpdateGroupOperator = useCallback((gapIndex: number, op: LogicalOperator) => {
    // The backend stores a single root operator for all groups.
    // All gap operators must stay in sync — update every gap to the new value.
    setGroupOperators(prev => prev.map(() => op));
  }, []);

  const cloneGroup = (conditionGroup: ConditionGroup): ConditionGroup => ({
    ...conditionGroup,
    conditions: [...conditionGroup.conditions],
    subGroups: conditionGroup.subGroups.map(cloneGroup),
  });

  // Find a group by id at any depth (top-level + sub-groups)
  const findGroup = (groups: ConditionGroup[], id: string): ConditionGroup | undefined => {
    for (const g of groups) {
      if (g.id === id) return g;
      const found = findGroup(g.subGroups, id);
      if (found) return found;
    }
    return undefined;
  };

  // Move a condition between groups (or reorder within the same group)
  const handleDragEnd = useCallback((result: DropResult) => {
    if (!result.destination) return;
    const { droppableId: sourceId, index: sourceIdx } = result.source;
    const { droppableId: destinationId, index: destinationIdx } = result.destination;
    if (sourceId === destinationId && sourceIdx === destinationIdx) return;

    const next = conditionGroups.map(cloneGroup);
    const srcGroup = findGroup(next, sourceId);
    const dstGroup = findGroup(next, destinationId);
    if (!srcGroup || !dstGroup) return;

    const [moved] = srcGroup.conditions.splice(sourceIdx, 1); // remove from source
    dstGroup.conditions.splice(destinationIdx, 0, moved); // add in destination

    // Remove top-level groups that became empty after the drag.
    const emptyIndices = next
      .map((_, i) => i)
      .filter(i => next[i].conditions.length === 0 && next[i].subGroups.length === 0)
      .reverse();

    let groups = next;
    let ops = groupOperators;

    for (const k of emptyIndices) {
      if (groups.length <= 1) break; // always keep at least one group
      const opIndex = Math.max(0, k - 1);
      groups = groups.filter((_, i) => i !== k);
      ops = ops.filter((_, i) => i !== opIndex);
    }

    setConditionGroups(groups);
    setGroupOperators(ops);
  }, [conditionGroups, groupOperators]);

  const conditionsValid = isEventFormValid({
    name: 'placeholder',
    description: '',
    groupOperators,
    conditionGroups,
  });
  const canSubmit = isFormValid && conditionsValid;

  const onValid = (base: EventBaseInput) => {
    onSubmit({
      name: base.event_name.trim(),
      description: (base.event_description ?? '').trim(),
      groupOperators,
      conditionGroups,
    });
  };

  return (
    <FormProvider {...methods}>
      <Box
        component="form"
        onSubmit={handleSubmit(onValid)}
        sx={{
          display: 'flex',
          flexDirection: 'column',
          gap: 3,
        }}
      >
        <TextFieldController
          name="event_name"
          label={t('Name')}
          required
          variant="standard"
        />

        <TextFieldController
          name="event_description"
          label={t('Description')}
          multiline
          rows={3}
          variant="standard"
        />

        <Box sx={{ mt: 2 }}>
          <Typography
            variant="h6"
            sx={{
              fontWeight: 700,
              mb: 1,
            }}
          >
            {t('Trigger Conditions')}
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            {t('Event conditions are based on data produced by Actions. To access more options in the "Field to inspect", consider adding additional.')}
          </Typography>
          <Box sx={{
            display: 'flex',
            justifyContent: 'flex-end',
            mb: 2,
          }}
          >
            <Button
              size="small"
              color="primary"
              startIcon={<AddOutlined fontSize="small" />}
              onClick={handleAddConditionGroup}
            >
              {t('Add Condition Group')}
            </Button>
          </Box>

          <DragDropContext onDragEnd={handleDragEnd}>
            <Box sx={{
              display: 'flex',
              flexDirection: 'column',
            }}
            >
              {conditionGroups.map((group, index) => (
                <Box key={group.id}>
                  {index > 0 && (
                    <Box sx={{
                      py: 1.5,
                      pl: 1,
                    }}
                    >
                      <LogicalOperatorSelect
                        value={groupOperators[index - 1] ?? 'AND'}
                        onChange={op => handleUpdateGroupOperator(index - 1, op)}
                      />
                    </Box>
                  )}
                  <ConditionGroupBuilder
                    group={group}
                    onUpdate={updated => handleUpdateGroup(index, updated)}
                    onDelete={conditionGroups.length > 1 ? () => handleDeleteGroup(index) : undefined}
                  />
                </Box>
              ))}
            </Box>
          </DragDropContext>
        </Box>

        <ActionFormButtons disabled={!canSubmit} onCancel={onCancel} submitLabel={submitLabel} />
      </Box>
    </FormProvider>
  );
};

export default EventCreationForm;
