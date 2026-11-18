import { Chip, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { GroupsOutlined, HelpCenterOutlined } from '@mui/icons-material';
import { Box, Stack, SvgIcon } from '@mui/material';
import { LogoFiligranIcon } from 'filigran-icon';

import { useFormatter } from '../../../../components/i18n';
import { type ConnectorItem, prettifyUseCase } from './catalog-facets';

// What the connector card and the connector list both show.

export const ConnectorLogo = ({ connector }: { connector: ConnectorItem }) => (connector.logoSrc
  ? <img src={connector.logoSrc} alt={connector.title} />
  : <HelpCenterOutlined color="disabled" />);

// Support as OpenCTI: verified means supported by Filigran.
export const ConnectorSupportIcon = ({ connector }: { connector: ConnectorItem }) => {
  const { t } = useFormatter();
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <span className="inline-flex">
          {connector.verified
            ? <SvgIcon component={LogoFiligranIcon} inheritViewBox color="primary" fontSize="small" />
            : <GroupsOutlined color="action" fontSize="small" />}
        </span>
      </TooltipTrigger>
      <TooltipContent>{connector.verified ? t('Supported by Filigran') : t('Supported by Community')}</TooltipContent>
    </Tooltip>
  );
};

export const CollapsedUseCaseChips = ({ useCases }: { useCases: string[] }) => {
  if (useCases.length === 0) return null;
  const [first, ...others] = useCases.map(prettifyUseCase);
  return (
    <Stack
      direction="row"
      spacing={1}
      sx={{
        flex: '1 1 0',
        minWidth: 'min-content',
      }}
    >
      <Box sx={{ display: 'flex' }}>
        <Tooltip>
          <TooltipTrigger asChild>
            <span className="inline-flex" style={{ maxWidth: '100%' }}>
              <Chip label={first} style={{ maxWidth: '100%' }} />
            </span>
          </TooltipTrigger>
          {first && <TooltipContent>{first}</TooltipContent>}
        </Tooltip>
      </Box>
      {others.length > 0 && (
        <Box sx={{ flexShrink: 0 }}>
          <Tooltip>
            <TooltipTrigger asChild>
              <span className="inline-flex">
                <Chip label={`+${others.length}`} />
              </span>
            </TooltipTrigger>
            <TooltipContent>{others.join(', ')}</TooltipContent>
          </Tooltip>
        </Box>
      )}
    </Stack>
  );
};
