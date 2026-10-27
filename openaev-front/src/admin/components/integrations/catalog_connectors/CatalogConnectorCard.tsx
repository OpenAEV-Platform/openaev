import { Chip, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { HelpCenterOutlined } from '@mui/icons-material';
import { Box, Stack } from '@mui/material';
import { type ReactNode } from 'react';

import MarketplaceCard from '../../../../components/common/marketplace/MarketplaceCard';
import usePlatformVersion from '../../../../components/common/marketplace/usePlatformVersion';
import { useFormatter } from '../../../../components/i18n';
import { type ConnectorItem, type ConnectorItemType, prettifyUseCase } from './catalog-facets';
import useChipOverflow from './useChipOverflow';

interface Props {
  connector: ConnectorItem;
  /** Right side of the card footer (deploy button, instance status, migrate action...). */
  footerAction?: ReactNode;
}

// Use-case chips for the footer-left; exported for the lines view.
// Chips that fit are shown, the rest collapse into "+N" — never clipped mid-label.
export const UseCaseChips = ({ useCases }: { useCases: string[] }) => {
  const { containerRef, chipRefs, overflowRef, visibleCount } = useChipOverflow(useCases);

  const hiddenCount = useCases.length - visibleCount;
  // Only what the "+N" stands for: the chips already shown are not repeated.
  const hiddenUseCases = useCases.slice(visibleCount).map(prettifyUseCase).join(', ');

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
        return (
          <Box
            key={useCase}
            ref={(el: HTMLDivElement | null) => {
              chipRefs.current[index] = el;
            }}
            // Fixed-size chips, so the overflow count is measurable.
            sx={{
              flexShrink: 0,
              maxWidth: '100%',
              minWidth: 0,
              visibility: isVisible ? 'visible' : 'hidden',
              position: isVisible ? 'relative' : 'absolute',
            }}
          >
            <Tooltip>
              <TooltipTrigger asChild>
                <Chip
                  label={prettifyUseCase(useCase)}
                  severity="info"
                  style={{ maxWidth: '100%' }}
                />
              </TooltipTrigger>
              {prettifyUseCase(useCase) && <TooltipContent>{prettifyUseCase(useCase)}</TooltipContent>}
            </Tooltip>
          </Box>
        );
      })}
      {/* Always mounted, so its width is known before anything is hidden; it is
          only taken out of the flow while there is nothing to count. */}
      <Box
        ref={overflowRef}
        sx={{
          flexShrink: 0,
          visibility: hiddenCount > 0 ? 'visible' : 'hidden',
          position: hiddenCount > 0 ? 'relative' : 'absolute',
        }}
      >
        <Tooltip>
          <TooltipTrigger asChild>
            <Chip label={`+${Math.max(hiddenCount, 1)}`} severity="info" />
          </TooltipTrigger>
          {hiddenUseCases && <TooltipContent>{hiddenUseCases}</TooltipContent>}
        </Tooltip>
      </Box>
    </Stack>
  );
};

const CollapsedUseCaseChips = ({ useCases }: { useCases: string[] }) => {
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
            <Chip label={first} severity="info" style={{ maxWidth: '100%' }} />
          </TooltipTrigger>
          {first && <TooltipContent>{first}</TooltipContent>}
        </Tooltip>
      </Box>
      {others.length > 0 && (
        <Box sx={{ flexShrink: 0 }}>
          <Tooltip>
            <TooltipTrigger asChild>
              <Chip label={`+${others.length}`} severity="info" />
            </TooltipTrigger>
            <TooltipContent>{others.join(', ')}</TooltipContent>
          </Tooltip>
        </Box>
      )}
    </Stack>
  );
};

const CatalogConnectorCard = ({ connector, footerAction }: Props) => {
  const { t, nsd } = useFormatter();
  const platformVersion = usePlatformVersion();
  const typeLabels: Record<ConnectorItemType, string> = {
    COLLECTOR: t('Collector'),
    INJECTOR: t('Injector'),
    EXECUTOR: t('Executor'),
    SECRETS_PROVIDER: t('Secrets Provider'),
  };
  return (
    <MarketplaceCard
      testId="connector-card"
      icon={connector.logoSrc
        ? <img src={connector.logoSrc} alt={connector.title} />
        : <HelpCenterOutlined sx={{ color: 'text.secondary' }} />}
      overline={typeLabels[connector.type]}
      title={connector.title}
      // Support as OpenCTI: verified means supported by Filigran.
      subtitle={connector.verified ? `${t('by')} Filigran` : `${t('by')} ${t('Community')}`}
      description={connector.description}
      chips={<CollapsedUseCaseChips useCases={connector.useCases} />}
      platforms={[]}
      footerRight={footerAction}
      caption={[
        // No image to deploy: the connector ships with the platform.
        // Tags such as "rolling" are not version numbers.
        connector.version ? `${/^\d/.test(connector.version) ? 'v' : `${t('Version')}: `}${connector.version}` : `${t('Built-in')}${platformVersion ? ` · v${platformVersion}` : ''}`,
        connector.lastVerifiedAt ? `${t('Verified')} ${nsd(connector.lastVerifiedAt)}` : null,
      ].filter(Boolean).join(' · ') || undefined}
      linkTo={connector.detailUrl}
    />
  );
};

export default CatalogConnectorCard;
