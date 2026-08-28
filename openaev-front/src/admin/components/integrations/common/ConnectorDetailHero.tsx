import { GroupsOutlined, HelpCenterOutlined } from '@mui/icons-material';
import { Chip, SvgIcon } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { LogoFiligranIcon } from 'filigran-icon';
import { type ReactNode } from 'react';

import { DetailHero } from '../../../../components/common/detail/EntityDetailCommon';
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
 * The hero of a connector detail page, built on the shared DetailHero so the
 * geometry, icon box and action sizing match every other entity detail page:
 * logo in the standard icon box, title, support badge (Filigran / community),
 * status and type / use-case / deployment chips, and an actions slot on the
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
    <DetailHero
      iconNode={logoSrc
        ? (
            <img
              src={logoSrc}
              alt={title}
              style={{
                width: 36,
                height: 36,
                objectFit: 'contain',
              }}
            />
          )
        : (
            <HelpCenterOutlined sx={{
              fontSize: 28,
              color: theme.palette.primary.main,
            }}
            />
          )}
      title={title}
      chips={(
        <>
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
        </>
      )}
      action={actions}
    />
  );
};

export default ConnectorDetailHero;
