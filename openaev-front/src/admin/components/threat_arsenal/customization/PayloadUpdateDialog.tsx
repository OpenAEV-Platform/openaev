import {
  Button,
  Dialog,
  DialogBody,
  DialogContent,
  DialogFooter,
  DialogTitle,
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
  const filigranVersion = t('Filigran version v{version}', { version });
  const label = (field: PayloadFieldUpdateOutput) => t(CUSTOMIZABLE_FIELD_LABELS[field.field_name]);
  const submit = () => apply()
    .then((action) => {
      MESSAGING$.notifySuccess(t('Updated to Filigran v{version}', { version }));
      onUpdated(action);
    })
    // The call already tells the user it failed: the dialog stays open to try again.
    .catch(() => undefined);
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
                <Text variant="content-base">{t('Filigran changed things you edited. Pick which version to keep for each.')}</Text>
                {conflicts.map(({ block, fields }) => (
                  <ConflictChoice
                    key={block}
                    title={fields.map(label).join(', ')}
                    theirsLabel={filigranVersion}
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
              {update.update_release_notes.map(note => (
                <div key={note.release_version}>
                  <Text variant="content-caption" className="text-default-secondary">{`v${note.release_version}`}</Text>
                  <Text variant="content-base" style={{ whiteSpace: 'pre-line' }}>{note.release_changelog}</Text>
                </div>
              ))}
              {news.length > 0 && (
                <ChangeDiff
                  rows={news.map(field => ({
                    id: field.field_name,
                    field: label(field),
                    before: fieldText(field.field_name, field.field_base),
                    after: fieldText(field.field_name, field.field_filigran),
                  }))}
                  beforeLabel={t('Filigran version v{version}', { version: baseVersion })}
                  afterLabel={filigranVersion}
                />
              )}
              {update.update_other_changes.length > 0 && (
                <Text variant="content-base">
                  {`${t('Also changed')}: ${update.update_other_changes.map(key => t(otherChangeLabel(key))).join(', ')}`}
                </Text>
              )}
              {news.length === 0 && update.update_other_changes.length === 0 && (
                <Text variant="content-caption" className="text-default-secondary">{t('No change in the fields you can customize.')}</Text>
              )}
            </div>
            {kept.length > 0 && (
              <div style={section}>
                <Text variant="title-xs">{t('Your edits — the update keeps them')}</Text>
                {kept.map(field => <Text key={field.field_name} variant="content-base">{label(field)}</Text>)}
              </div>
            )}
          </div>
        </DialogBody>
        <DialogFooter>
          <Button priority="secondary" onClick={onClose}>{t('Cancel')}</Button>
          <Button loading={applying} disabled={!ready} onClick={submit}>{t('Update')}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
};

export default PayloadUpdateDialog;
