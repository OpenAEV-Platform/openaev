import {
  Button,
  Dialog,
  DialogBody,
  DialogContent,
  DialogFooter,
  DialogTitle,
  Spinner,
  Text,
} from '@filigran/design-system';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

import ChangeDiff from '../../../../components/common/update/ChangeDiff';
import ConflictChoice from '../../../../components/common/update/ConflictChoice';
import { useFormatter } from '../../../../components/i18n';
import { type PayloadCustomizationUpdateOutput, type PayloadFieldUpdateOutput, type ThreatArsenalAction } from '../../../../utils/api-types';
import { MESSAGING$ } from '../../../../utils/Environment';
import { blockText, CUSTOMIZABLE_FIELD_LABELS, fieldText, otherChangeLabel } from './fieldUpdates';
import useCustomizationUpdate from './useCustomizationUpdate';

interface Props {
  actionId: string;
  // The Filigran version the copy is built from.
  baseVersion: string;
  update: PayloadCustomizationUpdateOutput;
  onClose: () => void;
  onUpdated: (action: ThreatArsenalAction) => void;
}

// Taking a newer Filigran version: a choice on each block both changed, the rest combined.
const PayloadUpdateDialog: FunctionComponent<Props> = ({ actionId, baseVersion, update, onClose, onUpdated }) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const { conflicts, news, kept, sides, choose, ready, applying, apply } = useCustomizationUpdate(actionId, update);
  const version = update.update_version;
  const label = (field: PayloadFieldUpdateOutput) => t(CUSTOMIZABLE_FIELD_LABELS[field.field_name]);
  const updated = (action: ThreatArsenalAction) => {
    MESSAGING$.notifySuccess(t('Updated to Filigran v{version}', { version }));
    onUpdated(action);
  };
  const section = {
    display: 'flex',
    flexDirection: 'column',
    gap: theme.spacing(1),
  } as const;

  return (
    <Dialog open onOpenChange={open => !open && onClose()}>
      <DialogContent size="lg">
        <DialogTitle>{t('Update to Filigran v{version}', { version })}</DialogTitle>
        <DialogBody>
          <div style={{
            ...section,
            gap: theme.spacing(3),
          }}
          >
            {conflicts.length > 0 && (
              <div style={section}>
                <Text variant="content-base">{t('Filigran changed what you changed too. Choose the value to keep for each.')}</Text>
                {conflicts.map(({ block, fields }) => (
                  <ConflictChoice
                    key={block}
                    title={fields.map(label).join(', ')}
                    theirsLabel={t('Filigran v{version}', { version })}
                    mineLabel={t('Yours')}
                    theirs={blockText(fields, field => field.field_filigran, label)}
                    mine={blockText(fields, field => field.field_yours, label)}
                    selected={sides[block]}
                    onSelect={side => choose(block, side)}
                  />
                ))}
              </div>
            )}
            <div style={section}>
              <Text variant="title-xs">{t('What\'s new in Filigran v{version}', { version })}</Text>
              {news.length > 0 && (
                <ChangeDiff
                  rows={news.map(field => ({
                    id: field.field_name,
                    field: label(field),
                    before: fieldText(field.field_base),
                    after: fieldText(field.field_filigran),
                  }))}
                  beforeLabel={t('Filigran v{version}', { version: baseVersion })}
                  afterLabel={t('Filigran v{version}', { version })}
                />
              )}
              {update.update_other_changes.length > 0 && (
                <Text variant="content-base">
                  {`${t('Also changed')}: ${update.update_other_changes.map(key => t(otherChangeLabel(key))).join(', ')}`}
                </Text>
              )}
              {news.length === 0 && update.update_other_changes.length === 0 && (
                <Text variant="content-caption" className="text-default-secondary">{t('Nothing else changed.')}</Text>
              )}
            </div>
            {kept.length > 0 && (
              <div style={section}>
                <Text variant="title-xs">{t('Your changes, kept')}</Text>
                <Text variant="content-base">{kept.map(label).join(', ')}</Text>
              </div>
            )}
          </div>
        </DialogBody>
        <DialogFooter>
          <Button priority="secondary" onClick={onClose}>{t('Cancel')}</Button>
          <Button disabled={applying || !ready} onClick={() => apply().then(updated)}>
            {applying ? <Spinner size="sm" /> : t('Update')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
};

export default PayloadUpdateDialog;
