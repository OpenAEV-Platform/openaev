import { Button, Text, Textarea } from '@filigran/design-system';
import { type FunctionComponent, useEffect, useState } from 'react';

import {
  fetchPayloadVariant,
  type PayloadVariant,
  updatePayloadOverrides,
  type VariantSaveOutput,
} from '../../../../actions/payloads/payload-variant-actions';
import Drawer from '../../../../components/common/Drawer';
import { useFormatter } from '../../../../components/i18n';
import { MESSAGING$ } from '../../../../utils/Environment';
import PayloadRebaseDialog from './PayloadRebaseDialog';
import { fromText, sameValue, toText, VARIANT_FIELD_LABELS } from './variantFields';

interface Props {
  identityId: string;
  onClose: () => void;
  onSaved: (output: VariantSaveOutput) => void;
}

// The fields a variant may change, each next to the catalogue value it overrides.
const PayloadVariantEditor: FunctionComponent<Props> = ({ identityId, onClose, onSaved }) => {
  const { t } = useFormatter();
  const [variant, setVariant] = useState<PayloadVariant | null>(null);
  const [drafts, setDrafts] = useState<Record<string, string>>({});
  const [saving, setSaving] = useState(false);
  const [rebasing, setRebasing] = useState(false);

  const load = () => fetchPayloadVariant(identityId).then((response: { data: PayloadVariant }) => {
    setVariant(response.data);
    setDrafts(Object.fromEntries(response.data.variant_customizable_fields.map(field => [field, toText(field, response.data.current_values[field])])));
  });
  useEffect(() => {
    load();
  }, [identityId]);

  const save = () => {
    if (!variant) {
      return;
    }
    const overrides: Record<string, unknown> = {};
    for (const field of variant.variant_customizable_fields) {
      let value: unknown;
      try {
        value = fromText(field, drafts[field] ?? '');
      } catch {
        MESSAGING$.notifyError(t('{field} is not valid JSON', { field: t(VARIANT_FIELD_LABELS[field] ?? field) }));
        return;
      }
      if (!sameValue(value, variant.base_values[field])) {
        overrides[field] = value;
      }
    }
    setSaving(true);
    updatePayloadOverrides(identityId, overrides)
      .then((response: { data: VariantSaveOutput }) => {
        onSaved(response.data);
        onClose();
      })
      .finally(() => setSaving(false));
  };

  return (
    <Drawer open handleClose={onClose} title={t('Customize')}>
      <>
        {!variant ? <></> : (
          <div style={{
            display: 'flex',
            flexDirection: 'column',
            gap: 16,
          }}
          >
            <Text variant="content-base">
              {t('Based on {externalId} v{version}', {
                externalId: variant.base_external_id ?? '',
                version: variant.base_version,
              })}
            </Text>
            {(variant.newer_base_version || variant.conflict_target_version) && (
              <div style={{
                display: 'flex',
                alignItems: 'center',
                gap: 8,
              }}
              >
                <Text variant="content-base">
                  {variant.conflict_target_version
                    ? t('v{version} needs a choice before it can be merged', { version: variant.conflict_target_version })
                    : t('A new base is available: v{version}', { version: variant.newer_base_version ?? '' })}
                </Text>
                <Button size="sm" priority="secondary" onClick={() => setRebasing(true)}>{t('Preview and rebase')}</Button>
              </div>
            )}
            {variant.variant_customizable_fields.map(field => (
              <div
                key={field}
                style={{
                  display: 'grid',
                  gridTemplateColumns: '1fr 1fr',
                  gap: 12,
                }}
              >
                <Textarea
                  label={`${t(VARIANT_FIELD_LABELS[field] ?? field)} · ${t('Filigran')}`}
                  value={toText(field, variant.base_values[field])}
                  readOnly
                  rows={4}
                />
                <Textarea
                  label={`${t(VARIANT_FIELD_LABELS[field] ?? field)} · ${t('Customized')}`}
                  value={drafts[field] ?? ''}
                  onChange={event => setDrafts(previous => ({
                    ...previous,
                    [field]: event.target.value,
                  }))}
                  rows={4}
                />
              </div>
            ))}
            <div style={{
              display: 'flex',
              justifyContent: 'flex-end',
              gap: 8,
            }}
            >
              <Button priority="secondary" onClick={onClose}>{t('Cancel')}</Button>
              <Button onClick={save} disabled={saving}>{t('Save')}</Button>
            </div>
          </div>
        )}
        {rebasing && variant && (
          <PayloadRebaseDialog
            identityId={identityId}
            onClose={() => setRebasing(false)}
            onRebased={(output) => {
              setRebasing(false);
              onSaved(output);
              load();
            }}
          />
        )}
      </>
    </Drawer>
  );
};

export default PayloadVariantEditor;
