import { DomainOutlined, PermIdentityOutlined } from '@mui/icons-material';
import { Box, Chip, List, ListItem, ListItemButton, ListItemIcon, ListItemText } from '@mui/material';
import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router';

import type { UserHelper } from '../../../../actions/helper';
import { fetchOrganizationById } from '../../../../actions/security/securityDetail-actions';
import { type TagHelper } from '../../../../actions/tags/tag-helper';
import { fetchUsers } from '../../../../actions/users/User';
import Breadcrumbs from '../../../../components/Breadcrumbs';
import { DetailHero, DetailSections, Field, InformationGrid, Section } from '../../../../components/common/detail/EntityDetailCommon';
import Empty from '../../../../components/Empty';
import ExpandableMarkdown from '../../../../components/ExpandableMarkdown';
import { useFormatter } from '../../../../components/i18n';
import ItemTags from '../../../../components/ItemTags';
import Loader from '../../../../components/Loader';
import { SECURITY_ORGANIZATION_BASE_URL, USER_BASE_URL } from '../../../../constants/BaseUrls';
import { useHelper } from '../../../../store';
import { type Organization, type User } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import { SETTINGS_LABEL } from '../../nav/config/settings.config';
import OrganizationPopover from '../organizations/OrganizationPopover';
import SecurityMenu from '../SecurityMenu';

const OrganizationDetail = () => {
  const { t, fldt } = useFormatter();
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const { organizationId } = useParams() as { organizationId: string };

  const [organization, setOrganization] = useState<Organization | null>(null);
  useEffect(() => {
    fetchOrganizationById(organizationId).then(response => setOrganization(response.data as Organization));
  }, [organizationId]);

  const { usersMap, tagsMap } = useHelper((helper: UserHelper & TagHelper) => ({
    usersMap: helper.getUsersMap(),
    tagsMap: helper.getTagsMap(),
  }));
  useDataLoader(() => {
    dispatch(fetchUsers());
  });

  const members = useMemo(
    () => (Object.values(usersMap) as User[]).filter(user => user.user_organization === organizationId),
    [usersMap, organizationId],
  );

  if (!organization) {
    return <Loader />;
  }

  return (
    <div style={{ display: 'flex' }}>
      <div style={{ flexGrow: 1 }}>
        <Breadcrumbs
          variant="object"
          elements={[
            { label: t(SETTINGS_LABEL) },
            { label: t('Security') },
            {
              label: t('Organizations'),
              link: SECURITY_ORGANIZATION_BASE_URL,
            },
            {
              label: organization.organization_name,
              current: true,
            },
          ]}
        />
        <Box sx={{
          display: 'flex',
          flexDirection: 'column',
          gap: 2,
        }}
        >
          <DetailHero
            icon={DomainOutlined}
            title={organization.organization_name}
            chips={(
              <Chip size="small" variant="outlined" label={t('{count} members', { count: members.length })} sx={{ borderRadius: 1 }} />
            )}
            action={(
              <OrganizationPopover
                organization={organization}
                tagsMap={tagsMap}
                onUpdate={(updated: Organization) => setOrganization(updated)}
                onDelete={() => navigate(SECURITY_ORGANIZATION_BASE_URL)}
              />
            )}
          />

          {/* Identity + members side by side: both sections are short, so
              sharing one grid row keeps the overview compact. */}
          <DetailSections>
            <InformationGrid title={t('Information')}>
              <Field label={t('Description')}>
                <ExpandableMarkdown source={organization.organization_description ?? ''} limit={300} />
              </Field>
              <Field label={t('Tags')}>
                <ItemTags variant="list" tags={organization.organization_tags ?? []} />
              </Field>
              <Field label={t('Creation date')}>{fldt(organization.organization_created_at)}</Field>
              <Field label={t('Update date')}>{fldt(organization.organization_updated_at)}</Field>
            </InformationGrid>
            <Section title={t('Members')}>
              {members.length === 0
                ? <Empty message={t('No member in this organization.')} />
                : (
                    <List disablePadding>
                      {members.map((member) => {
                        const label = [member.user_firstname, member.user_lastname].filter(Boolean).join(' ').trim()
                          || member.user_email;
                        return (
                          <ListItem key={member.user_id} divider disablePadding>
                            <ListItemButton component={Link} to={`${USER_BASE_URL}/${member.user_id}`}>
                              <ListItemIcon sx={{ minWidth: 36 }}><PermIdentityOutlined color="primary" /></ListItemIcon>
                              <ListItemText primary={label} />
                            </ListItemButton>
                          </ListItem>
                        );
                      })}
                    </List>
                  )}
            </Section>
          </DetailSections>
        </Box>
      </div>
      <SecurityMenu />
    </div>
  );
};

export default OrganizationDetail;
