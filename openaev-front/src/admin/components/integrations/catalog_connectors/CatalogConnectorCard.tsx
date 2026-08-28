import { GroupsOutlined, HelpCenterOutlined } from '@mui/icons-material';
import { Box, Card, CardActionArea, CardContent, Chip, Stack, SvgIcon, Tooltip, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { LogoFiligranIcon } from 'filigran-icon';
import { type ReactNode } from 'react';
import { Link } from 'react-router';

import { useFormatter } from '../../../../components/i18n';
import { type ConnectorItem, type ConnectorItemType, prettifyUseCase } from './catalog-facets';
import useChipOverflow from './useChipOverflow';

interface Props {
  connector: ConnectorItem;
  /** Right side of the card footer (deploy button, instance status, migrate action...). */
  footerAction?: ReactNode;
}

// Use-case chips shown in the footer-left, exact same anatomy as OpenCTI's
// IngestionCatalogChip: outlined primary, 12px sentence-case label, no icon,
// rgba(0,0,0,0.1) background, 4px radius. Overflow behavior mirrors OpenCTI:
// chips that fit are shown, the last visible one may ellipsize, the rest
// collapses into a "+N" chip - a chip is never clipped mid-label.
// Exported for reuse by the lines view (CatalogConnectorLine).
export const UseCaseChips = ({ useCases }: { useCases: string[] }) => {
  const { containerRef, chipRefs, visibleCount } = useChipOverflow(useCases);

  const hiddenCount = useCases.length - visibleCount;
  const hiddenUseCases = useCases.slice(visibleCount);

  const chipSx = {
    'fontSize': 12,
    'lineHeight': '14px',
    'borderRadius': 1,
    'backgroundColor': 'rgba(0, 0, 0, 0.1)',
    'maxWidth': '100%',
    '& .MuiChip-label': {
      overflow: 'hidden',
      textOverflow: 'ellipsis',
    },
  } as const;

  return (
    <Stack
      ref={containerRef}
      direction="row"
      spacing={1}
      sx={{
        overflow: 'hidden',
        flexWrap: 'nowrap',
        position: 'relative',
        minWidth: 0,
      }}
    >
      {useCases.map((useCase, index) => {
        const isVisible = index < visibleCount;
        const canShrink = index === visibleCount - 1;
        return (
          <Box
            key={useCase}
            ref={(el: HTMLDivElement | null) => {
              chipRefs.current[index] = el;
            }}
            sx={{
              flexShrink: canShrink ? 1 : 0,
              minWidth: canShrink ? 0 : 'auto',
              visibility: isVisible ? 'visible' : 'hidden',
              position: isVisible ? 'relative' : 'absolute',
            }}
          >
            <Tooltip title={prettifyUseCase(useCase)}>
              <Chip
                variant="outlined"
                size="small"
                color="primary"
                label={prettifyUseCase(useCase)}
                sx={chipSx}
              />
            </Tooltip>
          </Box>
        );
      })}
      {hiddenCount > 0 && (
        <Tooltip title={hiddenUseCases.map(prettifyUseCase).join(', ')}>
          <Chip
            variant="outlined"
            size="small"
            color="primary"
            label={`+${hiddenCount}`}
            sx={{
              ...chipSx,
              flexShrink: 0,
            }}
          />
        </Tooltip>
      )}
    </Stack>
  );
};

const CatalogConnectorCard = ({ connector, footerAction }: Props) => {
  const theme = useTheme();
  const { t } = useFormatter();

  const typeLabels: Record<ConnectorItemType, string> = {
    COLLECTOR: t('Collector'),
    INJECTOR: t('Injector'),
    EXECUTOR: t('Executor'),
    SECRETS_PROVIDER: t('Secrets Provider'),
  };

  return (
    // The hover styles live on this wrapper so they also apply when the inner
    // action area is disabled (cards without a detail page). Same hover, fixed
    // height and anatomy as the OpenCTI integrations marketplace card.
    <Box
      data-testid="connector-card"
      sx={{
        'height': '100%',
        '& .MuiCard-root': {
          border: `1px solid ${alpha(theme.palette.text.primary, 0.08)}`,
          transition: 'transform 0.3s ease-in-out, border-color 0.3s ease-in-out, box-shadow 0.3s ease-in-out',
        },
        '&:hover .MuiCard-root': {
          transform: 'translateY(-2px)',
          borderColor: alpha(theme.palette.primary.main, 0.3),
          boxShadow: `0 0 30px ${alpha(theme.palette.primary.main, 0.12)}`,
        },
      }}
    >
      <Card
        variant="outlined"
        sx={{
          height: 280,
          borderRadius: 1,
          display: 'flex',
          // Same card surface as OpenCTI's marketplace (background.secondary):
          // slightly lighter than the page so cards pop against the hero.
          backgroundColor: theme.palette.mode === 'dark' ? '#0c1524' : undefined,
        }}
      >
        <CardActionArea
          // A Link with an empty `to` would still render href="" (surprising
          // navigation for assistive tech / open-in-new-tab); render a plain
          // action area when the card has no detail page.
          {...(connector.detailUrl != null
            ? {
                component: Link,
                to: connector.detailUrl,
              }
            : { disabled: true })}
          sx={{
            display: 'flex',
            alignItems: 'stretch',
          }}
        >
          <CardContent
            sx={{
              display: 'flex',
              flexDirection: 'column',
              gap: 2,
              padding: 3,
              height: '100%',
              width: '100%',
            }}
          >
            <Stack direction="row" gap={1.5} alignItems="flex-start" sx={{ width: '100%' }}>
              <Box
                sx={{
                  width: 56,
                  height: 56,
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
                      width: 44,
                      height: 44,
                      objectFit: 'contain',
                      borderRadius: 4,
                    }}
                  />
                ) : (
                  <HelpCenterOutlined sx={{
                    fontSize: 32,
                    color: 'text.secondary',
                  }}
                  />
                )}
              </Box>
              <Box sx={{
                flex: 1,
                minWidth: 0,
              }}
              >
                <Typography
                  variant="body2"
                  sx={{
                    color: 'primary.main',
                    fontSize: 12,
                    fontWeight: 500,
                    letterSpacing: '0.06em',
                    textTransform: 'uppercase',
                    marginBottom: 0.5,
                  }}
                >
                  {typeLabels[connector.type]}
                </Typography>
                <Tooltip title={connector.title} placement="bottom-start">
                  <Typography
                    sx={{
                      fontSize: 15,
                      fontWeight: 600,
                      lineHeight: 1.35,
                      display: '-webkit-box',
                      WebkitLineClamp: 2,
                      WebkitBoxOrient: 'vertical',
                      overflow: 'hidden',
                      wordBreak: 'break-word',
                    }}
                  >
                    {connector.title}
                  </Typography>
                </Tooltip>
              </Box>
              {/* Support semantics (same as OpenCTI): the verified flag means
                  supported by Filigran, otherwise supported by the community. */}
              <Tooltip title={connector.verified ? t('Supported by Filigran') : t('Supported by Community')}>
                {connector.verified ? (
                  <SvgIcon
                    component={LogoFiligranIcon}
                    inheritViewBox
                    color="primary"
                    sx={{
                      fontSize: 20,
                      flexShrink: 0,
                    }}
                  />
                ) : (
                  <GroupsOutlined
                    color="disabled"
                    sx={{
                      fontSize: 20,
                      flexShrink: 0,
                    }}
                  />
                )}
              </Tooltip>
            </Stack>

            <Box sx={{
              flexGrow: 1,
              overflow: 'hidden',
              width: '100%',
            }}
            >
              {connector.description && (
                <Typography
                  variant="body2"
                  sx={{
                    color: 'text.secondary',
                    lineHeight: 1.5,
                    display: '-webkit-box',
                    WebkitLineClamp: 4,
                    WebkitBoxOrient: 'vertical',
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                  }}
                >
                  {connector.description}
                </Typography>
              )}
            </Box>

            {/* Footer: use-case (category) chips on the left, deploy / status on
                the right - mirrors the OpenCTI CardActions layout. */}
            <div style={{
              display: 'flex',
              alignItems: 'flex-end',
              justifyContent: 'space-between',
              gap: theme.spacing(1),
              width: '100%',
            }}
            >
              <UseCaseChips useCases={connector.useCases} />
              {footerAction && (
                <div style={{ flexShrink: 0 }}>
                  {footerAction}
                </div>
              )}
            </div>
          </CardContent>
        </CardActionArea>
      </Card>
    </Box>
  );
};

export default CatalogConnectorCard;
