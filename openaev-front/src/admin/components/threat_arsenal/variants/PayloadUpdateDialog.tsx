import { Alert, Button, Dialog, DialogBody, DialogContent, DialogFooter, DialogTitle, Text } from '@filigran/design-system';
import { type FunctionComponent, useState } from 'react';

import {
  rebaseVariant,
  type VariantBlock,
  type VariantChoice,
  type VariantRebasePreview,
  type VariantSaveOutput,
} from '../../../../actions/payloads/payload-variant-actions';
import ConflictChoice from '../../../../components/common/update/ConflictChoice';
import { useFormatter } from '../../../../components/i18n';
import PayloadWhatsNew from './PayloadWhatsNew';
import { changedFields, keptChanges, VARIANT_FIELD_LABELS } from './variantFields';
import VariantFieldValue from './VariantFieldValue';

interface Props {
  identityId: string;
  baseActionId?: string;
  preview: VariantRebasePreview;
  onClose: () => void;
  onUpdated: (output: VariantSaveOutput) => void;
}

// Updating to a newer Filigran version: what it brings, a side for each conflict, the rest combined.
const PayloadUpdateDialog: FunctionComponent<Props> = ({ identityId, baseActionId, preview, onClose, onUpdated }) => {
  const { t } = useFormatter();
  const [choices, setChoices] = useState<Partial<Record<VariantBlock, VariantChoice>>>({});
  const [applying, setApplying] = useState(false);
  const label = (field: string) => t(VARIANT_FIELD_LABELS[field] ?? field);

  const conflicts = preview.blocks.filter(block => block.conflict);
  const kept = keptChanges(preview).map(field => t(field));

  const apply = () => {
    setApplying(true);
    rebaseVariant(identityId, preview.target_payload_id, Object.fromEntries(conflicts.map(block => [block.block, { choice: choices[block.block] }])))
      .then((response: { data: VariantSaveOutput }) => onUpdated(response.data))
      .finally(() => setApplying(false));
  };

  return (
    <Dialog open onOpenChange={open => !open && onClose()}>
      <DialogContent size="lg">
        <DialogTitle>{t('Update to Filigran v{version}', { version: preview.target_version })}</DialogTitle>
        <DialogBody>
          <div style={{
            display: 'flex',
            flexDirection: 'column',
            gap: 20,
          }}
          >
            {conflicts.length > 0 && (
              <Text variant="content-base">{t('Filigran changed what you customized. Keep one version for each conflict; the rest is combined.')}</Text>
            )}
            {preview.missing_variables.length > 0 && (
              <Alert severity="warning" title={`${t('Variables without an argument')}: ${preview.missing_variables.join(', ')}`} />
            )}
            {conflicts.map((block) => {
              const fields = [...new Set([...changedFields(block, 'catalog'), ...changedFields(block, 'mine')])];
              const values = (side: Record<string, unknown>, other: Record<string, unknown>) => (
                <div style={{
                  display: 'flex',
                  flexDirection: 'column',
                  gap: 8,
                }}
                >
                  {fields.map(field => (
                    <div key={field}>
                      {fields.length > 1 && <Text variant="content-caption">{label(field)}</Text>}
                      <VariantFieldValue value={side[field]} other={other[field]} />
                    </div>
                  ))}
                </div>
              );
              const selected = choices[block.block];
              return (
                <ConflictChoice
                  key={block.block}
                  title={fields.map(label).join(', ')}
                  theirsLabel={t('Filigran v{version}', { version: preview.target_version })}
                  mineLabel={t('Yours')}
                  theirs={values(block.catalog, block.mine)}
                  mine={values(block.mine, block.catalog)}
                  selected={selected && (selected === 'TAKE_CATALOG' ? 'theirs' : 'mine')}
                  onSelect={side => setChoices(previous => ({
                    ...previous,
                    [block.block]: side === 'theirs' ? 'TAKE_CATALOG' : 'KEEP_MINE',
                  }))}
                />
              );
            })}
            <PayloadWhatsNew baseActionId={baseActionId} preview={preview} />
            {kept.length > 0 && (
              <div style={{
                display: 'flex',
                flexDirection: 'column',
                gap: 4,
              }}
              >
                <Text variant="title-xs">{t('Your changes, kept')}</Text>
                {kept.map(line => <Text key={line} variant="content-base">{line}</Text>)}
              </div>
            )}
          </div>
        </DialogBody>
        <DialogFooter>
          <Button priority="secondary" onClick={onClose}>{t('Cancel')}</Button>
          <Button onClick={apply} disabled={applying || conflicts.some(block => !choices[block.block])}>{t('Update')}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
};

export default PayloadUpdateDialog;
