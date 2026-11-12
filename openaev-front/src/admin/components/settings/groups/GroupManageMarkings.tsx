import { Switch } from '@filigran/design-system';
import { LensOutlined } from '@mui/icons-material';
import { Box } from '@mui/material';
import { type FunctionComponent, useEffect, useMemo, useState } from 'react';

import SelectListPicker, { type SelectListPickerElements } from '../../../../components/common/SelectListPicker';
import { useFormatter } from '../../../../components/i18n';
import { type MarkingDefinitionOutput } from '../../../../utils/api-types';
import { useMarkingDefinitionsWithLoading } from '../../../../utils/hooks/useMarkingDefinitions';
import { useAbility } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';

interface Props {
  initialState: string[];
  initialAutoNewMarking?: boolean;
  open: boolean;
  onClose: () => void;
  onSubmit: (markingIds: string[], autoNewMarking: boolean) => void;
  groupName?: string;
  title?: string;
}

// Markings are a small, effectively-static set (nine seeded TLP/PAP levels per tenant, see
// useMarkingDefinitions), so unlike GroupManageRoles this picker has no server-side pagination:
// the whole set is loaded once and filtered/sorted client-side by SelectListPicker.
//
// Checkboxes are independent, as in OpenCTI: the lower levels of a type are granted server-side
// (MarkingScopeResolver).
const GroupManageMarkings: FunctionComponent<Props> = ({
  initialState,
  initialAutoNewMarking = false,
  open,
  onClose,
  onSubmit,
  groupName = '',
  title,
}) => {
  const { t } = useFormatter();
  const { definitions: markingDefinitions, loading } = useMarkingDefinitionsWithLoading({ skip: !open });
  // Most restrictive first within a type (TLP:RED at the top), so the cumulative cutoff a user
  // picks reads top-down as "this and everything below it".
  const markingValues = useMemo(
    () => Object.values(markingDefinitions).sort((a, b) => {
      if (a.marking_definition_type !== b.marking_definition_type) {
        return a.marking_definition_type.localeCompare(b.marking_definition_type);
      }
      return b.marking_definition_order - a.marking_definition_order;
    }),
    [markingDefinitions],
  );

  const [selectedIds, setSelectedIds] = useState<string[]>([]);
  const [autoNewMarking, setAutoNewMarking] = useState(false);
  const ability = useAbility();
  const canAssignMarking = ability.can(ACTIONS.MANAGE, SUBJECTS.MARKING_ASSIGNMENT);

  useEffect(() => {
    if (open) {
      setSelectedIds(initialState);
      setAutoNewMarking(initialAutoNewMarking);
    }
  }, [open, initialState, initialAutoNewMarking]);

  const toggleMarking = (markingId: string) => {
    setSelectedIds(prev => (prev.includes(markingId) ? prev.filter(id => id !== markingId) : [...prev, markingId]));
  };

  const elements: SelectListPickerElements<MarkingDefinitionOutput> = useMemo(() => ({
    icon: {
      value: (marking: MarkingDefinitionOutput) => (
        <LensOutlined sx={{
          color: marking.marking_definition_color,
          fontSize: 16,
        }}
        />
      ),
    },
    headers: [
      {
        field: 'marking_definition_type',
        label: 'Type',
        value: (marking: MarkingDefinitionOutput) => marking.marking_definition_type.toUpperCase(),
        width: 40,
      },
      {
        field: 'marking_definition_definition',
        label: 'Definition',
        value: (marking: MarkingDefinitionOutput) => (
          <Box sx={{
            display: 'flex',
            alignItems: 'center',
            gap: 0.5,
          }}
          >
            <span>{marking.marking_definition_definition.toUpperCase()}</span>
          </Box>
        ),
        width: 60,
      },
    ],
    // Deliberately not sortable: the fixed most-restrictive-first order is what makes the
    // cumulative cutoff readable. A column sort (e.g. alphabetical) would scramble it.
  }), []);

  const handleClose = () => {
    setSelectedIds([]);
    onClose();
  };

  const handleSubmit = () => {
    onSubmit(selectedIds, autoNewMarking);
    handleClose();
  };

  return (
    <SelectListPicker<MarkingDefinitionOutput>
      open={open}
      isLoading={loading}
      onClose={handleClose}
      onSubmit={handleSubmit}
      title={title ?? t('Manage markings for group: {groupName}', { groupName })}
      headerComponent={canAssignMarking ? (
        <Switch
          checked={autoNewMarking}
          onCheckedChange={checked => setAutoNewMarking(checked === true)}
          label={t('Auto assign new markings')}
        />
      ) : undefined}
      values={markingValues}
      elements={elements}
      selectedIds={selectedIds}
      onToggle={toggleMarking}
      getId={element => element.marking_definition_id}
    />
  );
};

export default GroupManageMarkings;
