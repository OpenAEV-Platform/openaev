import { Button, Text } from '@filigran/design-system';
import { Box } from '@mui/material';
import { type FunctionComponent, useState } from 'react';

import Drawer from '../../../../components/common/Drawer';
import MarkingField from '../../../../components/fields/MarkingField';
import { useFormatter } from '../../../../components/i18n';

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
// Mounted by the caller only while open (no `open` prop here, see Drawer.tsx), gated on
// MANAGE_MARKING_ASSIGNMENT by Endpoints.tsx's canBulkEditMarkings - no extra guard needed here.
const BulkMarkingsDrawer: FunctionComponent<Props> = ({ onClose, onSubmit }) => {
  const { t } = useFormatter();
  const [markingIds, setMarkingIds] = useState<string[]>([]);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleClose = () => {
    if (isSubmitting) return;
    onClose();
  };

  const handleSubmit = async () => {
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
        />
        <Box sx={{ textAlign: 'right' }}>
          <Button type="button" variant="destructive" onClick={handleSubmit} loading={isSubmitting}>
            {t('Edit markings')}
          </Button>
        </Box>
      </Box>
    </Drawer>
  );
};

export default BulkMarkingsDrawer;
