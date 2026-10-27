import {
  Alert,
  Button,
  Chip,
  Dialog,
  DialogBody,
  DialogContent,
  DialogFooter,
  DialogTitle,
  Radio,
  RadioGroup,
  Text,
  Textarea,
} from '@filigran/design-system';
import { type FunctionComponent, useEffect, useState } from 'react';

import {
  fetchVariantRebasePreview,
  rebaseVariant,
  type VariantBlock,
  type VariantBlockChoice,
  type VariantChoice,
  type VariantRebasePreview,
  type VariantSaveOutput,
} from '../../../../actions/payloads/payload-variant-actions';
import { useFormatter } from '../../../../components/i18n';
import { MESSAGING$ } from '../../../../utils/Environment';
import { fromText, toText, VARIANT_FIELD_LABELS } from './variantFields';

interface Props {
  identityId: string;
  onClose: () => void;
  onRebased: (output: VariantSaveOutput) => void;
}

const BLOCK_LABELS: Record<VariantBlock, string> = {
  EXECUTION: 'Execution',
  PLATFORMS: 'Platforms',
};

// Rebase on a newer catalogue version: each block side by side, a choice for each conflict.
const PayloadRebaseDialog: FunctionComponent<Props> = ({ identityId, onClose, onRebased }) => {
  const { t } = useFormatter();
  const [preview, setPreview] = useState<VariantRebasePreview | null>(null);
  const [choices, setChoices] = useState<Partial<Record<VariantBlock, VariantChoice>>>({});
  const [edits, setEdits] = useState<Record<string, string>>({});
  const [applying, setApplying] = useState(false);

  useEffect(() => {
    fetchVariantRebasePreview(identityId).then((response: { data: VariantRebasePreview }) => {
      setPreview(response.data);
      const initial: Record<string, string> = {};
      response.data.blocks.forEach(block => Object.entries(block.mine).forEach(([field, value]) => {
        initial[field] = toText(field, value);
      }));
      setEdits(initial);
    });
  }, [identityId]);

  const conflicts = preview?.blocks.filter(block => block.conflict) ?? [];
  const ready = conflicts.every(block => choices[block.block]);

  const apply = () => {
    if (!preview) {
      return;
    }
    const payload: Partial<Record<VariantBlock, VariantBlockChoice>> = {};
    try {
      conflicts.forEach((block) => {
        const choice = choices[block.block] as VariantChoice;
        payload[block.block] = choice === 'EDIT'
          ? {
              choice,
              values: Object.fromEntries(Object.keys(block.mine).map(field => [field, fromText(field, edits[field] ?? '')])),
            }
          : { choice };
      });
    } catch {
      MESSAGING$.notifyError(t('A value is not valid JSON'));
      return;
    }
    setApplying(true);
    rebaseVariant(identityId, preview.target_payload_id, payload)
      .then((response: { data: VariantSaveOutput }) => onRebased(response.data))
      .finally(() => setApplying(false));
  };

  const column = (title: string, values: Record<string, unknown>) => (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      gap: 8,
      minWidth: 0,
    }}
    >
      <Text variant="content-caption">{title}</Text>
      {Object.entries(values).map(([field, value]) => (
        <Textarea key={field} label={t(VARIANT_FIELD_LABELS[field] ?? field)} value={toText(field, value)} readOnly rows={3} />
      ))}
    </div>
  );

  return (
    <Dialog open onOpenChange={open => !open && onClose()}>
      <DialogContent size="lg">
        <DialogTitle>
          {preview
            ? t('Rebase v{current} on v{target}', {
                current: preview.current_version,
                target: preview.target_version,
              })
            : t('Rebase')}
        </DialogTitle>
        <DialogBody>
          {!preview ? <Text variant="content-base">{t('Loading')}</Text> : (
            <div style={{
              display: 'flex',
              flexDirection: 'column',
              gap: 16,
            }}
            >
              {preview.missing_variables.length > 0 && (
                <Alert severity="warning" title={`${t('Variables without an argument')}: ${preview.missing_variables.join(', ')}`} />
              )}
              {preview.blocks.map(block => (
                <div
                  key={block.block}
                  style={{
                    display: 'flex',
                    flexDirection: 'column',
                    gap: 8,
                  }}
                >
                  <div style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 8,
                  }}
                  >
                    <Text variant="title-xs">{t(BLOCK_LABELS[block.block])}</Text>
                    {block.catalog_changed && <Chip label={t('Changed by Filigran')} />}
                    {block.variant_changed && <Chip label={t('Customized')} />}
                    {block.conflict && <Chip label={t('Conflict')} severity="high" />}
                  </div>
                  <div style={{
                    display: 'grid',
                    gridTemplateColumns: '1fr 1fr 1fr',
                    gap: 12,
                  }}
                  >
                    {column(t('Base version v{version}', { version: preview.base_version }), block.base)}
                    {column(t('Filigran version v{version}', { version: preview.target_version }), block.catalog)}
                    {column(t('Mine'), block.mine)}
                  </div>
                  {block.conflict && (
                    <RadioGroup
                      orientation="horizontal"
                      value={choices[block.block] ?? ''}
                      onValueChange={value => setChoices(previous => ({
                        ...previous,
                        [block.block]: value as VariantChoice,
                      }))}
                    >
                      <Radio value="KEEP_MINE" label={t('Keep mine')} />
                      <Radio value="TAKE_CATALOG" label={t('Take Filigran\'s')} />
                      <Radio value="EDIT" label={t('Edit')} />
                    </RadioGroup>
                  )}
                  {choices[block.block] === 'EDIT' && Object.keys(block.mine).map(field => (
                    <Textarea
                      key={field}
                      label={t(VARIANT_FIELD_LABELS[field] ?? field)}
                      value={edits[field] ?? ''}
                      onChange={event => setEdits(previous => ({
                        ...previous,
                        [field]: event.target.value,
                      }))}
                      rows={4}
                    />
                  ))}
                </div>
              ))}
            </div>
          )}
        </DialogBody>
        <DialogFooter>
          <Button priority="secondary" onClick={onClose}>{t('Cancel')}</Button>
          <Button onClick={apply} disabled={!preview || !ready || applying}>{t('Rebase')}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
};

export default PayloadRebaseDialog;
