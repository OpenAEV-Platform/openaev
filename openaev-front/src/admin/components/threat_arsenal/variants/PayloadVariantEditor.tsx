import { Button, Text } from '@filigran/design-system';
import { type FunctionComponent, useEffect, useMemo, useState } from 'react';

import {
  fetchPayloadVariant,
  fetchVariantRebasePreview,
  type PayloadVariant,
  updatePayloadOverrides,
  type VariantRebasePreview,
  type VariantSaveOutput,
} from '../../../../actions/payloads/payload-variant-actions';
import { fetchThreatArsenalAction } from '../../../../actions/threat_arsenals/threatArsenal-actions';
import Drawer from '../../../../components/common/Drawer';
import { useFormatter } from '../../../../components/i18n';
import { type ThreatArsenalActionFullOutput } from '../../../../utils/api-types';
import { MESSAGING$ } from '../../../../utils/Environment';
import { buildInitialValues, type ThreatArsenalActionFormValues } from '../actionFormValues';
import PayloadRevocationBanner from '../PayloadRevocationBanner';
import ThreatArsenalActionForm from '../ThreatArsenalActionForm';
import SnapshotRemediationProvider from '../utils/SnapshotRemediationProvider';
import PayloadUpdateDialog from './PayloadUpdateDialog';
import { fromFormValues, sameValue, toFormValues } from './variantFields';

interface Props {
  identityId: string;
  onClose: () => void;
  onSaved: (output: VariantSaveOutput) => void;
}

// The action form, where only the fields a variant may change stay editable; saved as overrides.
const PayloadVariantEditor: FunctionComponent<Props> = ({ identityId, onClose, onSaved }) => {
  const { t, tPick } = useFormatter();
  const [variant, setVariant] = useState<PayloadVariant | null>(null);
  const [action, setAction] = useState<ThreatArsenalActionFullOutput | null>(null);
  const [updatePreview, setUpdatePreview] = useState<VariantRebasePreview | null>(null);
  const [updating, setUpdating] = useState(false);

  const load = () => fetchPayloadVariant(identityId).then(async (response: { data: PayloadVariant }) => {
    if (response.data.variant_action_id) {
      const fetched = await fetchThreatArsenalAction(response.data.variant_action_id);
      setAction(fetched.data as ThreatArsenalActionFullOutput);
    }
    setVariant(response.data);
  });
  useEffect(() => {
    load();
  }, [identityId]);

  const name = action ? tPick(action.action_labels) : '';
  const baseValues = useMemo(() => (variant ? toFormValues(variant.base_values) : undefined), [variant]);

  const updated = (output: VariantSaveOutput, preview: VariantRebasePreview) => {
    MESSAGING$.notifySuccess(t('Updated to Filigran v{version}', { version: preview.target_version }));
    setUpdatePreview(null);
    onSaved(output);
    load();
  };

  const update = () => {
    setUpdating(true);
    fetchVariantRebasePreview(identityId)
      .then(({ data }: { data: VariantRebasePreview }) => setUpdatePreview(data))
      .finally(() => setUpdating(false));
  };

  const save = (data: ThreatArsenalActionFormValues) => {
    if (!variant) {
      return Promise.resolve();
    }
    const values = fromFormValues(data);
    const overrides = Object.fromEntries(variant.variant_customizable_fields
      .filter(field => !sameValue(values[field], variant.base_values[field]))
      .map(field => [field, values[field]]));
    return updatePayloadOverrides(identityId, overrides, data.action_tags ?? []).then((response: { data: VariantSaveOutput }) => {
      // A new version on a revoked base inherits the revocation again.
      if (response.data.new_version && response.data.action?.action_payload?.payload_revoked) {
        MESSAGING$.notifyError(t('This version stays revoked through its Filigran base v{version}: lift the revocation or update the copy.', { version: variant.base_version }));
      }
      onSaved(response.data);
      onClose();
    });
  };

  const newerVersion = variant?.conflict_target_version ?? variant?.newer_base_version;

  return (
    <Drawer open handleClose={onClose} title={name ? `${t('Customize')} · ${name}` : t('Customize')}>
      <>
        {variant && action && (
          <div style={{
            display: 'flex',
            flexDirection: 'column',
            gap: 16,
          }}
          >
            <div style={{
              display: 'flex',
              alignItems: 'center',
              gap: 8,
            }}
            >
              <Text variant="content-base">
                {[
                  t('Based on v{version}', { version: variant.base_version }),
                  newerVersion ? t('Filigran v{version} is available', { version: newerVersion }) : null,
                ].filter(Boolean).join(' · ')}
              </Text>
              {newerVersion && (
                <Button size="sm" onClick={update} disabled={updating}>{t('Update')}</Button>
              )}
            </div>
            <PayloadRevocationBanner key={variant.variant_payload_id} payloadId={variant.variant_payload_id} />
            <SnapshotRemediationProvider>
              <ThreatArsenalActionForm
                key={variant.variant_version}
                editing
                baseValues={baseValues}
                initialValues={{
                  ...buildInitialValues(action, name),
                  payload_id: variant.variant_payload_id,
                }}
                onSubmit={save}
                handleClose={onClose}
              />
            </SnapshotRemediationProvider>
          </div>
        )}
        {updatePreview && (
          <PayloadUpdateDialog
            identityId={identityId}
            baseActionId={variant?.base_action_id}
            preview={updatePreview}
            onClose={() => setUpdatePreview(null)}
            onUpdated={output => updated(output, updatePreview)}
          />
        )}
      </>
    </Drawer>
  );
};

export default PayloadVariantEditor;
