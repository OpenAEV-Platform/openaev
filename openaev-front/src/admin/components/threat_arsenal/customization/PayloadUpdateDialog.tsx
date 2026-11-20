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

import ConflictChoice from '../../../../components/common/update/ConflictChoice';
import { useFormatter } from '../../../../components/i18n';
import { type PayloadCustomizationUpdateOutput, type ThreatArsenalAction } from '../../../../utils/api-types';
import { MESSAGING$ } from '../../../../utils/Environment';
import { CUSTOMIZABLE_FIELD_LABELS, fieldText, otherChangeLabel } from './fieldUpdates';
import FieldUpdateValue from './FieldUpdateValue';
import useCustomizationUpdate from './useCustomizationUpdate';

interface Props {
  actionId: string;
  update: PayloadCustomizationUpdateOutput;
  onClose: () => void;
  onUpdated: (action: ThreatArsenalAction) => void;
}

// Taking a newer Filigran version: a choice on each field both changed, the rest combined.
const PayloadUpdateDialog: FunctionComponent<Props> = ({ actionId, update, onClose, onUpdated }) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const { conflicts, news, kept, sides, choose, ready, applying, apply } = useCustomizationUpdate(actionId, update);
  const version = update.update_version;
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
                {conflicts.map(field => (
                  <ConflictChoice
                    key={field.field_name}
                    title={t(CUSTOMIZABLE_FIELD_LABELS[field.field_name])}
                    theirsLabel={t('Filigran v{version}', { version })}
                    mineLabel={t('Yours')}
                    theirs={fieldText(field.field_filigran)}
                    mine={fieldText(field.field_yours)}
                    selected={sides[field.field_name]}
                    onSelect={side => choose(field.field_name, side)}
                  />
                ))}
              </div>
            )}
            <div style={section}>
              <Text variant="title-xs">{t('What\'s new in Filigran v{version}', { version })}</Text>
              {news.map(field => (
                <div key={field.field_name}>
                  <Text variant="content-caption" className="text-default-secondary">{t(CUSTOMIZABLE_FIELD_LABELS[field.field_name])}</Text>
                  <FieldUpdateValue value={field.field_filigran} />
                </div>
              ))}
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
                <Text variant="content-base">{kept.map(field => t(CUSTOMIZABLE_FIELD_LABELS[field.field_name])).join(', ')}</Text>
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
