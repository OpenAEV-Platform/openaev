import {
  Button,
  Dialog,
  DialogBody,
  DialogContent,
  DialogFooter,
  DialogTitle,
  Radio,
  RadioGroup,
  Spinner,
  Text,
} from '@filigran/design-system';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../../components/i18n';
import { type PayloadCustomizationUpdateOutput, type ThreatArsenalAction } from '../../../../utils/api-types';
import { CUSTOMIZABLE_FIELD_LABELS, otherChangeLabel } from './fieldUpdates';
import FieldUpdateValue from './FieldUpdateValue';
import useCustomizationUpdate, { type UpdateChoice } from './useCustomizationUpdate';

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
  const { conflicts, news, kept, choices, choose, ready, applying, apply } = useCustomizationUpdate(actionId, update);
  const version = update.update_version;
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
                {conflicts.map((field) => {
                  const label = t(CUSTOMIZABLE_FIELD_LABELS[field.field_name]);
                  return (
                    <div key={field.field_name} style={section}>
                      <Text variant="title-xs">{label}</Text>
                      <RadioGroup
                        aria-label={label}
                        value={choices[field.field_name] ?? ''}
                        onValueChange={choice => choose(field.field_name, choice as UpdateChoice)}
                      >
                        <Radio
                          value="FILIGRAN"
                          label={t('Filigran v{version}', { version })}
                          description={<FieldUpdateValue value={field.field_filigran} />}
                        />
                        <Radio
                          value="YOURS"
                          label={t('Yours')}
                          description={<FieldUpdateValue value={field.field_yours} />}
                        />
                      </RadioGroup>
                    </div>
                  );
                })}
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
          <Button disabled={applying || !ready} onClick={() => apply().then(onUpdated)}>
            {applying ? <Spinner size="sm" /> : t('Update')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
};

export default PayloadUpdateDialog;
