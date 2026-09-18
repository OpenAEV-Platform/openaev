import { LensOutlined } from '@mui/icons-material';
import { Box } from '@mui/material';
import { type FunctionComponent, useEffect, useMemo, useState } from 'react';

import SelectListPicker, { type SelectListPickerElements } from '../../../../components/common/SelectListPicker';
import { useFormatter } from '../../../../components/i18n';
import { type MarkingDefinitionOutput } from '../../../../utils/api-types';
import useMarkingDefinitions from '../../../../utils/hooks/useMarkingDefinitions';

interface Props {
  initialState: string[];
  open: boolean;
  onClose: () => void;
  onSubmit: (markingIds: string[]) => void;
  groupName?: string;
  title?: string;
}

// Markings are a small, effectively-static set (nine seeded TLP/PAP levels per tenant, see
// useMarkingDefinitions), so unlike GroupManageRoles this picker has no server-side pagination:
// the whole set is loaded once and filtered/sorted client-side by SelectListPicker.
const GroupManageMarkings: FunctionComponent<Props> = ({
  initialState,
  open,
  onClose,
  onSubmit,
  groupName = '',
  title,
}) => {
  const { t } = useFormatter();
  const markingDefinitions = useMarkingDefinitions({ skip: !open });
  const markingValues = useMemo(
    () => Object.values(markingDefinitions).sort((a, b) => a.marking_definition_order - b.marking_definition_order),
    [markingDefinitions],
  );

  const [selectedIds, setSelectedIds] = useState<string[]>([]);

  useEffect(() => {
    if (open) {
      setSelectedIds(initialState);
    }
  }, [open, initialState]);

  const toggleMarking = (markingId: string) => {
    setSelectedIds(prev => (prev.includes(markingId)
      ? prev.filter(id => id !== markingId)
      : [...prev, markingId]));
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
        isSortable: true,
        value: (marking: MarkingDefinitionOutput) => marking.marking_definition_type,
        width: 40,
      },
      {
        field: 'marking_definition_definition',
        label: 'Definition',
        isSortable: true,
        value: (marking: MarkingDefinitionOutput) => (
          <Box sx={{
            display: 'flex',
            alignItems: 'center',
            gap: 0.5,
          }}
          >
            <span>{marking.marking_definition_definition}</span>
          </Box>
        ),
        width: 60,
      },
    ],
  }), []);

  const handleClose = () => {
    setSelectedIds([]);
    onClose();
  };

  const handleSubmit = () => {
    onSubmit(selectedIds);
    handleClose();
  };

  return (
    <SelectListPicker<MarkingDefinitionOutput>
      open={open}
      onClose={handleClose}
      onSubmit={handleSubmit}
      title={title ?? t('Manage markings for group: {groupName}', { groupName })}
      values={markingValues}
      elements={elements}
      selectedIds={selectedIds}
      onToggle={toggleMarking}
      getId={element => element.marking_definition_id}
    />
  );
};

export default GroupManageMarkings;
