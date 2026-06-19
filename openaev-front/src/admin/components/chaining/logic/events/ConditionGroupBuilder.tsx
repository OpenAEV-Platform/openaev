import { Draggable, Droppable } from '@hello-pangea/dnd';
import { AddOutlined, DeleteOutline } from '@mui/icons-material';
import { Button } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../../../components/i18n';
import {
  type ConditionGroup,
  createEmptyCondition,
  type EventCondition,
  type LogicalOperator,
} from './event-types';
import EventConditionRow from './EventConditionRow';
import LogicalOperatorSelect from './LogicalOperatorSelect';

interface Props {
  group: ConditionGroup;
  onUpdate: (updated: ConditionGroup) => void;
  onDelete?: () => void;
}

const ConditionGroupBuilder: FunctionComponent<Props> = ({
  group,
  onUpdate,
  onDelete,
}) => {
  const { t } = useFormatter();
  const theme = useTheme();

  const handleOperatorChange = (operator: LogicalOperator) => {
    onUpdate({
      ...group,
      operator,
    });
  };

  const handleAddCondition = () => {
    onUpdate({
      ...group,
      conditions: [...group.conditions, createEmptyCondition()],
    });
  };

  const handleUpdateCondition = (index: number, updated: EventCondition) => {
    const newConditions = [...group.conditions];
    newConditions[index] = updated;
    onUpdate({
      ...group,
      conditions: newConditions,
    });
  };

  const handleDeleteCondition = (index: number) => {
    const newConditions = group.conditions.filter((_, i) => i !== index);
    onUpdate({
      ...group,
      conditions: newConditions,
    });
  };

  const handleUpdateSubGroup = (index: number, updated: ConditionGroup) => {
    const newSubGroups = [...group.subGroups];
    newSubGroups[index] = updated;
    onUpdate({
      ...group,
      subGroups: newSubGroups,
    });
  };

  const handleDeleteSubGroup = (index: number) => {
    const newSubGroups = group.subGroups.filter((_, i) => i !== index);
    onUpdate({
      ...group,
      subGroups: newSubGroups,
    });
  };

  return (
    <div style={{
      border: `1px solid ${theme.palette.divider}`,
      borderRadius: theme.spacing(1),
      padding: theme.spacing(2),
    }}
    >
      <div style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        marginBottom: theme.spacing(2),
      }}
      >
        <div style={{
          display: 'flex',
          alignItems: 'center',
          gap: theme.spacing(1),
        }}
        >
          <LogicalOperatorSelect
            value={group.operator}
            onChange={handleOperatorChange}
          />
          {onDelete && (
            <Button
              size="small"
              color="error"
              startIcon={<DeleteOutline fontSize="small" />}
              onClick={onDelete}
              sx={{ ml: 1 }}
            >
              {t('Remove group')}
            </Button>
          )}
        </div>
        <Button
          size="small"
          color="primary"
          startIcon={<AddOutlined fontSize="small" />}
          onClick={handleAddCondition}
        >
          {t('Add Condition')}
        </Button>
      </div>

      {/* Droppable conditions list */}
      <Droppable droppableId={group.id} type="condition">
        {(provided, snapshot) => (
          <div
            ref={provided.innerRef}
            {...provided.droppableProps}
            style={{
              display: 'flex',
              flexDirection: 'column',
              gap: theme.spacing(1),
              minHeight: 40,
              borderRadius: theme.spacing(1),
              transition: 'background 0.2s',
              background: snapshot.isDraggingOver ? `${theme.palette.primary.main}0F` : 'transparent',
            }}
          >
            {group.conditions.map((condition, index) => (
              <Draggable key={condition.id} draggableId={condition.id} index={index}>
                {(providedDrag, snapshotDrag) => (
                  <div
                    ref={providedDrag.innerRef}
                    {...providedDrag.draggableProps}
                    style={{
                      ...providedDrag.draggableProps.style,
                      opacity: snapshotDrag.isDragging ? 0.85 : 1,
                    }}
                  >
                    <EventConditionRow
                      condition={condition}
                      dragHandleProps={providedDrag.dragHandleProps}
                      onUpdate={updated => handleUpdateCondition(index, updated)}
                      onDelete={() => handleDeleteCondition(index)}
                      canDelete={group.conditions.length > 1}
                    />
                  </div>
                )}
              </Draggable>
            ))}
            {provided.placeholder}
          </div>
        )}
      </Droppable>

      {/* Sub-groups */}
      {group.subGroups.map((subGroup, index) => (
        <ConditionGroupBuilder
          key={subGroup.id}
          group={subGroup}
          onUpdate={updated => handleUpdateSubGroup(index, updated)}
          onDelete={() => handleDeleteSubGroup(index)}
        />
      ))}

    </div>
  );
};

export default ConditionGroupBuilder;
