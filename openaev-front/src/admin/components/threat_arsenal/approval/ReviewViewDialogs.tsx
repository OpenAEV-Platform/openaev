import { Button, Dialog, DialogBody, DialogContent, DialogFooter, DialogTitle, Input, Text } from '@filigran/design-system';
import { type FunctionComponent, useEffect, useState } from 'react';

import { useFormatter } from '../../../../components/i18n';
import { type ReviewView } from './reviewViews';

export interface ViewEdit {
  kind: 'rename' | 'delete';
  view: ReviewView;
}

interface Props {
  edit: ViewEdit | null;
  onClose: () => void;
  onRename: (view: ReviewView, label: string) => void;
  onDelete: (view: ReviewView) => void;
}

/**
 * Rename or delete a custom view, from the "Custom views" menu or from the options of the open view.
 * Deleting asks first; the actions the view lists are never changed.
 */
const ReviewViewDialogs: FunctionComponent<Props> = ({ edit, onClose, onRename, onDelete }) => {
  const { t } = useFormatter();
  const [label, setLabel] = useState('');
  useEffect(() => {
    if (edit?.kind === 'rename') setLabel(edit.view.label);
  }, [edit]);

  return (
    <>
      <Dialog open={edit?.kind === 'rename'} onOpenChange={open => !open && onClose()}>
        <DialogContent size="sm">
          <DialogTitle>{t('Rename view')}</DialogTitle>
          <DialogBody>
            <Input
              label={t('Name')}
              value={label}
              onChange={event => setLabel(event.target.value)}
              maxLength={60}
              autoFocus
            />
          </DialogBody>
          <DialogFooter>
            <Button priority="secondary" onClick={onClose}>{t('Cancel')}</Button>
            <Button
              disabled={label.trim() === ''}
              onClick={() => {
                if (edit) onRename(edit.view, label.trim());
                onClose();
              }}
            >
              {t('Save')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={edit?.kind === 'delete'} onOpenChange={open => !open && onClose()}>
        <DialogContent size="sm">
          <DialogTitle>{t('Delete view')}</DialogTitle>
          <DialogBody>
            <Text variant="content-base">{t('Delete the view "{name}"? The actions it lists are not changed.', { name: edit?.view.label ?? '' })}</Text>
          </DialogBody>
          <DialogFooter>
            <Button priority="secondary" onClick={onClose}>{t('Cancel')}</Button>
            <Button
              variant="destructive"
              onClick={() => {
                if (edit) onDelete(edit.view);
                onClose();
              }}
            >
              {t('Delete')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
};

export default ReviewViewDialogs;
