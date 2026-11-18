import { Card, CardDescription, CardHeader, CardOverview, CardTitle } from '@filigran/design-system';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';
import { Link } from 'react-router';

import CardThumbnail from '../../../../components/common/card/CardThumbnail';
import ItemTags from '../../../../components/ItemTags';
import { securityPlatformTypeLabel } from '../../../../components/securityPlatformType';
import { SECURITY_PLATFORM_BASE_URL } from '../../../../constants/BaseUrls';
import { type SecurityPlatform } from '../../../../utils/api-types';
import { buildTenantApiPath } from '../../../../utils/url-helper';
import SecurityPlatformPopover from './SecurityPlatformPopover';
import isConnectorManaged from './securityPlatformUtils';

interface Props {
  securityPlatform: SecurityPlatform;
  onUpdate: (result: SecurityPlatform) => void;
  onDelete: (result: string) => void;
  openEditOnInit?: boolean;
}

const SecurityPlatformCard: FunctionComponent<Props> = ({
  securityPlatform,
  onUpdate,
  onDelete,
  openEditOnInit,
}) => {
  const theme = useTheme();

  return (
    // Library Card: logo, platform type and name, tags, description. The ⋮
    // menu is the only action, so it is the header's single action and the
    // card has no footer.
    <Card
      data-testid="security-platform-card"
      to={`${SECURITY_PLATFORM_BASE_URL}/${securityPlatform.asset_id}`}
      linkComponent={Link}
    >
      <CardHeader
        icon={(
          <CardThumbnail image={{
            src: buildTenantApiPath(`/api/images/security_platforms/id/${securityPlatform.asset_id}/${theme.palette.mode}?${Date.now()}`),
            alt: securityPlatform.asset_name,
          }}
          />
        )}
        action={(
          <SecurityPlatformPopover
            securityPlatform={{
              ...securityPlatform,
              type: 'static',
            }}
            onUpdate={onUpdate}
            onDelete={onDelete}
            openEditOnInit={openEditOnInit}
            disabled={isConnectorManaged(securityPlatform)}
          />
        )}
      >
        <CardOverview>{securityPlatformTypeLabel(securityPlatform.security_platform_type)}</CardOverview>
        <CardTitle>{securityPlatform.asset_name}</CardTitle>
      </CardHeader>
      {securityPlatform.asset_tags?.length ? (
        // Every tag that fits the card width shows; only the rest collapse into "+N".
        <ItemTags tags={securityPlatform.asset_tags} limit={securityPlatform.asset_tags.length} />
      ) : null}
      {securityPlatform.asset_description && <CardDescription>{securityPlatform.asset_description}</CardDescription>}
    </Card>
  );
};

export default SecurityPlatformCard;
