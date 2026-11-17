import { Alert, Button, Dialog, DialogBody, DialogContent, DialogFooter, DialogTitle, Input, Text } from '@filigran/design-system';
import { type FunctionComponent, useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router';

import { type UserHelper } from '../../../actions/helper';
import { useFormatter } from '../../../components/i18n';
import { useHelper } from '../../../store';
import { type CustomView, type CustomViewFilters, NEW_VIEW_PARAM, sameViewFilters, useCustomViews, viewPath } from './customViews';

interface Props {
  /** The open custom view; none on the Active tab, whose filters are never saved. */
  view?: CustomView;
  /** The filters and search on screen. */
  current: CustomViewFilters;
  /** Back to the view's saved filters. */
  onDiscard: () => void;
}

/**
 * Under the tabs of the Active list: when the filters of an open custom view changed, says so with the
 * ways out (save them in the view, save them as a new view, or discard them). Also the "New custom
 * view" dialog, opened from the Custom views menu: the new view keeps what is on screen.
 */
const ThreatArsenalCustomViewControls: FunctionComponent<Props> = ({ view, current, onDiscard }) => {
  const { t } = useFormatter();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const me = useHelper((helper: UserHelper) => helper.getMe())?.user_id as string | undefined;
  const views = useCustomViews(me);
  const [naming, setNaming] = useState(false);
  const [name, setName] = useState('');

  const openNaming = () => {
    setName('');
    setNaming(true);
  };
  // "New custom view" from the tab bar lands here with the dialog open.
  useEffect(() => {
    if (searchParams.get(NEW_VIEW_PARAM)) {
      openNaming();
      searchParams.delete(NEW_VIEW_PARAM);
      setSearchParams(searchParams, { replace: true });
    }
  }, [searchParams]);

  const submitName = () => {
    const added = views.add(name.trim(), current);
    setNaming(false);
    navigate(viewPath(added.id));
  };

  const changed = !!view && !sameViewFilters(current, view.filters);

  return (
    <>
      {changed && (
        // FDS-WORKAROUND: Alert title/description are <p> with browser margins, which push the title below
        // its icon — remove when the library resets them — see DS Alert
        <div className="custom-view-unsaved-alert">
          <style>{'.custom-view-unsaved-alert p { margin: 0; }'}</style>
          <Alert
            severity="info"
            title={t('You changed the filters of {name}', { name: view.label })}
            description={t('Save them in this view, or keep this view as it is and save them as a new one.')}
            action={(
              <div style={{
                display: 'flex',
                gap: 8,
                flexWrap: 'wrap',
              }}
              >
                <Button size="sm" priority="tertiary" onClick={onDiscard}>{t('Discard')}</Button>
                <Button size="sm" priority="tertiary" onClick={openNaming}>{t('Save as new view')}</Button>
                <Button size="sm" priority="secondary" onClick={() => views.saveFilters(view.id, current)}>{t('Save changes')}</Button>
              </div>
            )}
          />
        </div>
      )}
      <Dialog open={naming} onOpenChange={open => !open && setNaming(false)}>
        <DialogContent size="sm">
          <DialogTitle>{t('New custom view')}</DialogTitle>
          <DialogBody>
            <Text variant="content-compact" className="text-default-secondary">
              {t('Keeps the current search and filters as a view of your own. It opens from Custom views, or as a tab. Only you see it.')}
            </Text>
            <Input
              label={t('Name')}
              value={name}
              onChange={event => setName(event.target.value)}
              maxLength={60}
              autoFocus
            />
          </DialogBody>
          <DialogFooter>
            <Button priority="secondary" onClick={() => setNaming(false)}>{t('Cancel')}</Button>
            <Button disabled={name.trim() === ''} onClick={submitName}>{t('Save')}</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
};

export default ThreatArsenalCustomViewControls;
