import { Button } from '@mui/material';
import { type ReactNode } from 'react';

import { DetailHero } from '../../../../components/common/detail/EntityDetailCommon';
import { useFormatter } from '../../../../components/i18n';

interface Props {
  /** Node rendered inside the hero icon box. */
  icon: ReactNode;
  /** Small uppercase label above the title. */
  overline: string;
  title: string;
  /** Id of the editor form the Save button submits (form lives in the left pane). */
  formId: string;
  onCancel: () => void;
  canSave: boolean;
  saving: boolean;
  saveLabel: string;
}

/**
 * Hero header for the full-page phishing editors. Reuses the shared DetailHero
 * so a create/edit page looks like every entity detail page in the app, with
 * Cancel / Save actions in the top-right. Save is a real submit button bound to
 * the editor form by id, so it works even though the form lives in the left
 * pane and the button sits in the hero.
 */
const PhishingEditorHero = ({ icon, overline, title, formId, onCancel, canSave, saving, saveLabel }: Props) => {
  const { t } = useFormatter();
  return (
    <DetailHero
      iconNode={icon}
      overline={overline}
      title={title}
      action={(
        <>
          <Button variant="outlined" color="primary" onClick={onCancel} disabled={saving}>
            {t('Cancel')}
          </Button>
          <Button type="submit" form={formId} variant="contained" color="primary" disabled={!canSave}>
            {saveLabel}
          </Button>
        </>
      )}
    />
  );
};

export default PhishingEditorHero;
