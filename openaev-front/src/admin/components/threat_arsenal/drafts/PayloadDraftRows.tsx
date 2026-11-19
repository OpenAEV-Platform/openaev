import { Button, Chip, Dialog, DialogBody, DialogContent, DialogFooter, DialogTitle, Text } from '@filigran/design-system';
import { type FunctionComponent, useState } from 'react';

import { FDS_GROUP_ROW_STYLE, FDS_TABLE_FRAME_STYLE } from '../../../../components/common/fdsTableSpec';
import { useFormatter } from '../../../../components/i18n';
import { type PayloadDraftOutput } from '../../../../utils/api-types';

interface Props {
  drafts: PayloadDraftOutput[];
  canEdit: boolean;
  onEdit: (draft: PayloadDraftOutput) => void;
  onDiscard: (draft: PayloadDraftOutput) => Promise<void>;
}

/**
 * Payload drafts at the top of the Versions tab, above the published versions: not usable, no
 * number yet, named after the version they started from.
 */
const PayloadDraftRows: FunctionComponent<Props> = ({ drafts, canEdit, onEdit, onDiscard }) => {
  const { t, nsdt } = useFormatter();
  const [discarding, setDiscarding] = useState<PayloadDraftOutput | null>(null);
  if (drafts.length === 0) return null;
  return (
    <>
      <div style={FDS_TABLE_FRAME_STYLE} aria-label={t('Drafts')}>
        {drafts.map(draft => (
          <div key={draft.draft_id} style={FDS_GROUP_ROW_STYLE}>
            <Text variant="content-base-bold">
              {draft.draft_rework
                ? `v${draft.draft_source_version ?? '-'}`
                : t('Draft from v{version}', { version: draft.draft_source_version ?? '-' })}
            </Text>
            <Chip label={t('Draft')} />
            <Text variant="content-compact" className="text-default-secondary">
              {draft.draft_updated_by
                ? t('Saved by {name}, {date}', {
                    name: draft.draft_updated_by,
                    date: nsdt(draft.draft_updated_at),
                  })
                : t('Saved {date}', { date: nsdt(draft.draft_updated_at) })}
            </Text>
            <div style={{ flex: 1 }} />
            {canEdit && (
              <>
                <Button size="sm" priority="tertiary" onClick={() => setDiscarding(draft)}>{t('Discard')}</Button>
                <Button size="sm" priority="secondary" onClick={() => onEdit(draft)}>{t('Edit draft')}</Button>
              </>
            )}
          </div>
        ))}
      </div>
      <Dialog open={discarding !== null} onOpenChange={open => !open && setDiscarding(null)}>
        <DialogContent size="sm">
          <DialogTitle>{t('Discard draft')}</DialogTitle>
          <DialogBody>
            <Text variant="content-base">{t('Discard this draft? No version changes.')}</Text>
          </DialogBody>
          <DialogFooter>
            <Button priority="secondary" onClick={() => setDiscarding(null)}>{t('Cancel')}</Button>
            <Button
              variant="destructive"
              onClick={() => {
                if (discarding) onDiscard(discarding).then(() => setDiscarding(null));
              }}
            >
              {t('Discard')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
};

export default PayloadDraftRows;
