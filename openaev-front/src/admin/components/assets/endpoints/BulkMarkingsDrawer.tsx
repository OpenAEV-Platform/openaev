import { Button, Text } from '@filigran/design-system';
import { Box } from '@mui/material';
import { type FunctionComponent, useState } from 'react';

import Drawer from '../../../../components/common/Drawer';
import MarkingField from '../../../../components/fields/MarkingField';
import { useFormatter } from '../../../../components/i18n';
import { useAbility } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';

interface Props {
  onClose: () => void;
  onSubmit: (markingIds: string[]) => Promise<void>;
}

// Replace-the-whole-set, the bulk mirror of the markings field on AssetForm (single-asset edit):
// every selected asset ends up carrying exactly the chosen markings, submitting with nothing
// selected clears every selected asset's markings. Unlike AssetForm, this picker cannot be
// pre-filled from any one row's markings, since the selection can span assets with different
// marking sets - it always starts empty, see bulkReplaceAssetMarkings's doc.
//
// Mounted by the caller only while open ({condition && <BulkMarkingsDrawer ... />}, see
// Drawer.tsx), so there is no `open` prop here - closing unmounts this component, which resets
// its state for free.
const BulkMarkingsDrawer: FunctionComponent<Props> = ({ onClose, onSubmit }) => {
  const { t } = useFormatter();
  const ability = useAbility();
  const [markingIds, setMarkingIds] = useState<string[]>([]);
  const [isSubmitting, setIsSubmitting] = useState(false);

  // An empty submission clears every selected asset's markings server-side, which needs
  // DELETE_MARKING_ASSIGNMENT for any asset in scope that currently carries one (see
  // AssetMarkingsService#assertMarkingDirectionCapability) - this frontend cannot know in advance
  // whether that is the case for every asset in a select-all scope, so a caller without the
  // capability is blocked from submitting empty at all rather than discovering a 403 mid-bulk.
  const canDeleteMarkingAssignment = ability.can(ACTIONS.DELETE, SUBJECTS.MARKING_ASSIGNMENT);
  const emptySelectionBlocked = markingIds.length === 0 && !canDeleteMarkingAssignment;

  const handleClose = () => {
    if (isSubmitting) return;
    onClose();
  };

  const handleSubmit = async () => {
    if (emptySelectionBlocked) return;
    setIsSubmitting(true);
    try {
      await onSubmit(markingIds);
    } catch {
      // Keep the selection so the user can retry; the error is already surfaced globally.
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <Drawer open handleClose={handleClose} title={t('Edit markings')}>
      <Box sx={{
        display: 'flex',
        flexDirection: 'column',
        gap: 2,
        mt: 1,
      }}
      >
        <Text variant="content-base" className="text-default-secondary">
          {t('Every selected asset will end up with exactly these markings, replacing whatever it carried before. Leaving this empty clears all markings.')}
        </Text>
        <MarkingField
          label={t('Markings')}
          fieldValue={markingIds}
          fieldOnChange={setMarkingIds}
          error={emptySelectionBlocked
            ? { type: 'manual', message: t('Select at least one marking, or ask for the Delete marking assignment capability to clear all markings.') }
            : undefined}
        />
        <Box sx={{ textAlign: 'right' }}>
          <Button type="button" variant="destructive" onClick={handleSubmit} loading={isSubmitting} disabled={emptySelectionBlocked}>
            {t('Edit markings')}
          </Button>
        </Box>
      </Box>
    </Drawer>
  );
};

export default BulkMarkingsDrawer;
