import { Box } from '@mui/material';
import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router';

import { fetchCredential } from '../../../../actions/assets/credential-actions';
import Breadcrumbs from '../../../../components/Breadcrumbs';
import {
  DetailHero,
  DetailSections, Field,
  InformationGrid,
} from '../../../../components/common/detail/EntityDetailCommon';
import ExpandableMarkdown from '../../../../components/ExpandableMarkdown';
import { useFormatter } from '../../../../components/i18n';
import ItemTags from '../../../../components/ItemTags';
import Loader from '../../../../components/Loader';
import NotFound from '../../../../components/NotFound';
import { type CredentialFullOutput, type CredentialOutput } from '../../../../utils/api-types';
import { humanizeEnum } from '../asset-categories';
import AssetCategoryIcon from '../AssetCategoryIcon';
import CredentialPopover from './CredentialPopover';
import CredentialStatusChip from './CredentialStatusChip';
import convertCredentialFullOutputToCredentialInput, { type CredentialFormInitialValues } from './credentialUtils';

const CredentialDetailPage = () => {
  const { t, fldt } = useFormatter();
  const { credentialId } = useParams() as { credentialId?: string };
  const navigate = useNavigate();

  const [credential, setCredential] = useState<CredentialFullOutput | null>(null);
  const [loading, setLoading] = useState(true);

  const resolveCredentialInitialValues = async (): Promise<CredentialFormInitialValues> => {
    if (!credential) {
      throw new Error('Credential details are not loaded');
    }

    return convertCredentialFullOutputToCredentialInput(credential);
  };

  useEffect(() => {
    setLoading(true);
    fetchCredential(credentialId ?? '')
      .then((result: { data: CredentialFullOutput }) => setCredential(result.data))
      .catch(() => setCredential(null))
      .finally(() => setLoading(false));
  }, [credentialId]);

  if (loading) {
    return <Loader />;
  }

  if (!credential) {
    return <NotFound />;
  }

  return (
    <Box sx={{
      display: 'flex',
      flexDirection: 'column',
      gap: 2,
      paddingBottom: 5,
    }}
    >
      <Breadcrumbs
        variant="object"
        elements={[
          {
            label: t('Credentials'),
            link: '/admin/credentials',
          },
          {
            label: credential.credential_name,
            current: true,
          },
        ]}
      />
      <DetailHero
        iconNode={(
          <AssetCategoryIcon
            scope="credential"
            category={credential?.credential_type ?? null}
            color="primary"
          />
        )}
        title={credential.credential_name}
        action={(
          <CredentialPopover
            credentialId={credential.credential_id}
            credentialName={credential.credential_name}
            resolveInitialValues={resolveCredentialInitialValues}
            onUpdate={(updated: CredentialOutput) => {
              setCredential(current => (current
                ? {
                    ...current,
                    ...updated,
                  }
                : current));
            }}
            onDelete={() => {
              navigate('/admin/credentials');
            }}
          />
        )}
      />
      <DetailSections>
        <InformationGrid title={t('Credential Information')}>
          <Field label={t('Description')}>
            {credential?.credential_description
              ? <ExpandableMarkdown source={credential.credential_description} limit={300} />
              : '-'}
          </Field>

          <Field label={t('Category')}>
            <Box sx={{
              display: 'flex',
              alignItems: 'center',
              gap: 1,
            }}
            >
              <AssetCategoryIcon
                scope="credential"
                category={credential?.credential_type ?? null}
                color="primary"
              />
              <span>
                {t(humanizeEnum(credential.credential_type))}
              </span>
            </Box>
          </Field>
          <Field label={t('Auth method')}>{humanizeEnum(credential.credential_auth_method)}</Field>

          <Box sx={{
            display: 'grid',
            alignItems: 'center',
            gap: 2,
          }}
          >
            <Field label={t('Status')}>
              <CredentialStatusChip status={credential.credential_status} variant="list" />
            </Field>
            <Field label={t('Created by')}>{credential.credential_created_by?.user_name || '-'}</Field>
            <Field label={t('Creation date')}>{fldt(credential?.credential_created_at)}</Field>
            <Field label={t('Last verified')}>{fldt(credential?.credential_last_verified_at)}</Field>
          </Box>

          <Field label={t('Tags')}>
            <ItemTags variant="list" tags={credential?.credential_tags_ids} />
          </Field>
        </InformationGrid>
      </DetailSections>
    </Box>
  );
};

export default CredentialDetailPage;
