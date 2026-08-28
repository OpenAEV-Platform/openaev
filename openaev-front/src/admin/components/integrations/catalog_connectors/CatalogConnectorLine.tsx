import { GroupsOutlined, HelpCenterOutlined } from '@mui/icons-material';
import { Box, SvgIcon, Tooltip, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { LogoFiligranIcon } from 'filigran-icon';
import { type ReactNode } from 'react';
import { Link } from 'react-router';

import { useFormatter } from '../../../../components/i18n';
import { type ConnectorItem, type ConnectorItemType } from './catalog-facets';
import { UseCaseChips } from './CatalogConnectorCard';

/**
 * Shared column geometry between the header row and the lines, so every
 * section renders as a proper aligned table (same pattern as the OpenCTI
 * integrations lines view). Widths are percentages of the row so the table
 * always fills the available space; the name column absorbs the rest. Metric
 * columns collapse on small screens instead of squeezing the names.
 */
const COLUMNS = {
  type: {
    width: '9%',
    minWidth: 84,
    display: {
      xs: 'none',
      sm: 'flex',
    },
  },
  description: {
    width: '26%',
    display: {
      xs: 'none',
      lg: 'flex',
    },
  },
  useCases: {
    width: '18%',
    minWidth: 150,
    display: {
      xs: 'none',
      md: 'flex',
    },
  },
  support: {
    width: '7%',
    minWidth: 76,
    display: {
      xs: 'none',
      sm: 'flex',
    },
  },
  action: {
    width: 230,
    display: 'flex',
    justifyContent: 'flex-end',
  },
} as const;

const cellSx = (column: keyof typeof COLUMNS) => ({
  ...COLUMNS[column],
  flexShrink: 0,
  alignItems: 'center',
});

// Column headers rendered once at the top of each section container.
export const CatalogConnectorLinesHeader = () => {
  const theme = useTheme();
  const { t } = useFormatter();
  const headerCellSx = {
    fontSize: 10,
    fontWeight: 600,
    textTransform: 'uppercase',
    letterSpacing: 0.5,
    color: theme.palette.text.secondary,
    lineHeight: 1,
  };
  return (
    <Box
      sx={{
        display: 'flex',
        alignItems: 'center',
        gap: 1.5,
        paddingInline: 1.5,
        paddingBlock: 1,
        backgroundColor: alpha(theme.palette.text.primary, 0.02),
        borderBottom: `1px solid ${alpha(theme.palette.text.primary, 0.08)}`,
      }}
    >
      <Typography
        component="div"
        sx={{
          ...headerCellSx,
          flex: 1,
          minWidth: 0,
        }}
      >
        {t('Name')}
      </Typography>
      <Typography
        component="div"
        sx={{
          ...headerCellSx,
          ...cellSx('type'),
        }}
      >
        {t('Type')}
      </Typography>
      <Typography
        component="div"
        sx={{
          ...headerCellSx,
          ...cellSx('description'),
        }}
      >
        {t('Description')}
      </Typography>
      <Typography
        component="div"
        sx={{
          ...headerCellSx,
          ...cellSx('useCases'),
        }}
      >
        {t('Use cases')}
      </Typography>
      <Typography
        component="div"
        sx={{
          ...headerCellSx,
          ...cellSx('support'),
        }}
      >
        {t('Support')}
      </Typography>
      <Box sx={cellSx('action')} />
    </Box>
  );
};

interface Props {
  connector: ConnectorItem;
  /** Trailing action cell (deploy button, instance status, migrate action...). */
  footerAction?: ReactNode;
}

/**
 * Compact row variant of CatalogConnectorCard for the lines view. Cells share
 * their geometry with CatalogConnectorLinesHeader so rows align.
 */
const CatalogConnectorLine = ({ connector, footerAction }: Props) => {
  const theme = useTheme();
  const { t } = useFormatter();
  const { detailUrl } = connector;

  const typeLabels: Record<ConnectorItemType, string> = {
    COLLECTOR: t('Collector'),
    INJECTOR: t('Injector'),
    EXECUTOR: t('Executor'),
    SECRETS_PROVIDER: t('Secrets Provider'),
  };

  const isClickable = detailUrl != null;

  const rowSx = {
    'display': 'flex',
    'alignItems': 'center',
    'gap': 1.5,
    'paddingInline': 1.5,
    'paddingBlock': 0.75,
    'cursor': isClickable ? 'pointer' : 'default',
    'textDecoration': 'none',
    'color': 'inherit',
    'transition': 'background-color 0.2s ease-in-out',
    '&:hover': isClickable ? { backgroundColor: theme.palette.action.hover } : undefined,
  };

  const cells = (
    <>
      {/* Name column: logo and title. */}
      <Box
        sx={{
          display: 'flex',
          alignItems: 'center',
          gap: 1.5,
          flex: 1,
          minWidth: 0,
        }}
      >
        <Box
          sx={{
            height: 32,
            width: 32,
            flexShrink: 0,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            borderRadius: 1,
            border: `1px solid ${alpha(theme.palette.text.primary, 0.1)}`,
            backgroundColor: alpha(theme.palette.text.primary, 0.04),
          }}
        >
          {connector.logoSrc ? (
            <img
              src={connector.logoSrc}
              alt={connector.title}
              style={{
                width: 24,
                height: 24,
                objectFit: 'contain',
                borderRadius: 3,
              }}
            />
          ) : (
            <HelpCenterOutlined sx={{
              fontSize: 18,
              color: 'text.secondary',
            }}
            />
          )}
        </Box>
        <Tooltip title={connector.title} placement="bottom-start">
          <Typography
            sx={{
              fontSize: 13,
              whiteSpace: 'nowrap',
              overflow: 'hidden',
              textOverflow: 'ellipsis',
            }}
          >
            {connector.title}
          </Typography>
        </Tooltip>
      </Box>
      {/* Type column. */}
      <Box sx={cellSx('type')}>
        <Typography
          sx={{
            fontSize: 12,
            color: 'primary.main',
            whiteSpace: 'nowrap',
            overflow: 'hidden',
            textOverflow: 'ellipsis',
          }}
        >
          {typeLabels[connector.type]}
        </Typography>
      </Box>
      {/* Description column. */}
      <Box sx={cellSx('description')}>
        <Typography
          sx={{
            fontSize: 12,
            color: 'text.secondary',
            whiteSpace: 'nowrap',
            overflow: 'hidden',
            textOverflow: 'ellipsis',
          }}
        >
          {connector.description || '-'}
        </Typography>
      </Box>
      {/* Use cases column: overflow-aware chips ("+N" collapse). */}
      <Box sx={cellSx('useCases')}>
        <UseCaseChips useCases={connector.useCases} />
      </Box>
      {/* Support column: same semantics as the card badge. */}
      <Box sx={cellSx('support')}>
        <Tooltip title={connector.verified ? t('Supported by Filigran') : t('Supported by Community')}>
          {connector.verified ? (
            <SvgIcon
              component={LogoFiligranIcon}
              inheritViewBox
              color="primary"
              sx={{ fontSize: 18 }}
            />
          ) : (
            <GroupsOutlined
              color="disabled"
              sx={{ fontSize: 18 }}
            />
          )}
        </Tooltip>
      </Box>
      {/* Action column: deploy button or instance status. */}
      <Box
        onClick={(event) => {
          // The row may be a real link: also cancel the native anchor
          // navigation for any action child that does not self-protect.
          event.preventDefault();
          event.stopPropagation();
        }}
        sx={cellSx('action')}
      >
        {footerAction}
      </Box>
    </>
  );

  // The row divider lives on the list container (ConnectorMarketplace), not
  // here: `& + &` would rely on every row sharing the same emotion class,
  // which breaks as soon as rows mix clickable and non-clickable styles.
  if (isClickable) {
    // Real router link (not a JS navigate) so ctrl/cmd+click opens a new tab;
    // an anchor is also natively focusable and Enter-activated (keyboard
    // parity with the card variant's CardActionArea).
    return (
      <Box
        data-testid="connector-line"
        component={Link}
        to={detailUrl}
        sx={rowSx}
      >
        {cells}
      </Box>
    );
  }
  return (
    <Box data-testid="connector-line" sx={rowSx}>
      {cells}
    </Box>
  );
};

export default CatalogConnectorLine;
