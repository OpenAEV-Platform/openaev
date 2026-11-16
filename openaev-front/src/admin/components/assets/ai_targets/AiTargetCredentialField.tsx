import { Button } from '@filigran/design-system';
import { type FunctionComponent, useEffect, useState } from 'react';

import { findCredentialsByIds } from '../../../../actions/assets/credential-actions';
import { useFormatter } from '../../../../components/i18n';
import { type CredentialOutput } from '../../../../utils/api-types';
import CredentialsPicker from '../credentials/CredentialsPicker';

interface Props {
  value?: string | null;
  onChange: (credentialId: string | null) => void;
  disabled?: boolean;
}

const AiTargetCredentialField: FunctionComponent<Props> = ({ value, onChange, disabled = false }) => {
  const { t } = useFormatter();
  const [open, setOpen] = useState(false);
  const [credentialName, setCredentialName] = useState<string | null>(null);

  useEffect(() => {
    if (!value) {
      setCredentialName(null);
      return;
    }
    findCredentialsByIds([value]).then((result: { data: CredentialOutput[] }) => {
      setCredentialName(result.data[0]?.credential_name ?? null);
    });
  }, [value]);

  return (
    <div style={{ marginTop: 20 }}>
      <div>{t('API token credential')}</div>
      <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginTop: 5 }}>
        <span style={{ flexGrow: 1 }}>{value ? (credentialName ?? '-') : t('No credential')}</span>
        <Button type="button" priority="secondary" onClick={() => setOpen(true)} disabled={disabled}>
          {t('Choose a credential')}
        </Button>
        {value && (
          <Button type="button" priority="secondary" onClick={() => onChange(null)} disabled={disabled}>
            {t('Remove')}
          </Button>
        )}
      </div>
      <CredentialsPicker
        initialState={value ? [value] : []}
        open={open}
        onClose={() => setOpen(false)}
        onSubmit={ids => onChange(ids[0] ?? null)}
        title={t('Choose a credential')}
        multiple={false}
        credentialType="API"
      />
    </div>
  );
};

export default AiTargetCredentialField;
