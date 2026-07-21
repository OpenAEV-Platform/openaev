import { GroupsOutlined, HelpCenterOutlined } from '@mui/icons-material';
import { Chip, Paper, SvgIcon, Tooltip, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { LogoFiligranIcon } from 'filigran-icon';
import { type ReactNode } from 'react';

import { useFormatter } from '../../../../components/i18n';
import { type ConnectorItemType, prettifyUseCase } from '../catalog_connectors/catalog-facets';
import useCaseIcon from '../catalog_connectors/use-case-icons';

interface Props {
  title: string;
  logoSrc?: string;
  type?: ConnectorItemType;
  useCases?: string[];
  verified?: boolean;
  external?: boolean;
  /** Instance status chip (deployed connector pages). */
  statusChip?: ReactNode;
  /** Right-side actions (deploy / migrate / start-stop / popover). */
  actions?: ReactNode;
}

/**
 * The marketplace-grade hero of a connector detail page: framed logo on an
 * accent-tinted band, title with support badge (Filigran / community) and
 * status, type / use-case / deployment chips, and an actions slot on the
 * right. The full description lives in the Overview card below the hero (same
 * layout as OpenCTI), keeping the hero compact.
 */
const ConnectorDetailHero = ({
  title,
  logoSrc,
  type,
  useCases = [],
  verified = false,
  external,
  statusChip,
  actions,
}: Props) => {
  const theme = useTheme();
  const { t } = useFormatter();
  const accent = theme.palette.primary.main;

  const typeLabels: Record<ConnectorItemType, string> = {
    COLLECTOR: t('Collector'),
    INJECTOR: t('Injector'),
    EXECUTOR: t('Executor'),
    SECRETS_PROVIDER: t('Secrets Provider'),
  };

  const chipSx = {
    fontSize: 11,
    height: 20,
    textTransform: 'uppercase',
    borderRadius: 1,
  };

  return (
    <Paper
      variant="outlined"
      sx={{
        display: 'flex',
        alignItems: 'center',
        gap: 2,
        padding: 2,
        borderRadius: 1,
        background: `linear-gradient(135deg, ${alpha(accent, 0.08)}, transparent 60%)`,
      }}
    >
      <div style={{
        width: 64,
        height: 64,
        flexShrink: 0,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        border: `1px solid ${theme.palette.divider}`,
        borderRadius: theme.shape.borderRadius,
        backgroundColor: theme.palette.background.default,
      }}
      >
        {logoSrc ? (
          <img
            src={logoSrc}
            alt={title}
            style={{
              width: 44,
              height: 44,
              objectFit: 'contain',
            }}
          />
        ) : (
          <HelpCenterOutlined sx={{
            fontSize: 32,
            color: 'text.secondary',
          }}
          />
        )}
      </div>
      <div style={{
        flex: 1,
        minWidth: 0,
        display: 'flex',
        flexDirection: 'column',
        gap: theme.spacing(0.75),
      }}
      >
        <div style={{
          display: 'flex',
          alignItems: 'center',
          gap: theme.spacing(1.5),
          minWidth: 0,
        }}
        >
          <Tooltip title={title}>
            <Typography
              variant="h1"
              sx={{
                margin: 0,
                whiteSpace: 'nowrap',
                overflow: 'hidden',
                textOverflow: 'ellipsis',
              }}
            >
              {title}
            </Typography>
          </Tooltip>
          {/* Support semantics (same as OpenCTI): the verified flag means
              supported by Filigran, otherwise supported by the community. */}
          {verified ? (
            <Chip
              variant="outlined"
              color="primary"
              size="small"
              sx={chipSx}
              icon={<SvgIcon component={LogoFiligranIcon} inheritViewBox sx={{ fontSize: 12 }} />}
              label={t('Supported by Filigran')}
            />
          ) : (
            <Chip
              variant="outlined"
              color="default"
              size="small"
              sx={chipSx}
              icon={<GroupsOutlined sx={{ fontSize: 14 }} />}
              label={t('Supported by Community')}
            />
          )}
          {statusChip}
        </div>
        <div style={{
          display: 'flex',
          alignItems: 'center',
          flexWrap: 'wrap',
          gap: theme.spacing(0.5),
        }}
        >
          {type && (
            <Chip
              variant="outlined"
              color="primary"
              size="small"
              sx={chipSx}
              label={typeLabels[type]}
            />
          )}
          {useCases.map((useCase) => {
            const UseCaseIcon = useCaseIcon(useCase);
            return (
              <Chip
                key={useCase}
                variant="outlined"
                color="default"
                size="small"
                sx={chipSx}
                icon={<UseCaseIcon sx={{ fontSize: 12 }} />}
                label={prettifyUseCase(useCase)}
              />
            );
          })}
          {external != null && (
            <Chip
              variant="outlined"
              color="default"
              size="small"
              sx={chipSx}
              label={external ? t('External') : t('Built-in')}
            />
          )}
        </div>
      </div>
      {actions && (
        <div style={{
          display: 'flex',
          alignItems: 'center',
          gap: theme.spacing(1),
          flexShrink: 0,
        }}
        >
          {actions}
        </div>
      )}
    </Paper>
  );
};

export default ConnectorDetailHero;
