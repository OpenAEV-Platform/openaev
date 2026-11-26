import { Chip, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { Box } from '@mui/material';
import { makeStyles } from 'tss-react/mui';

import { useFormatter } from '../../../../components/i18n';
import { type InjectorSimple } from '../../../../utils/api-types';
import InjectorIcon from '../../common/injectors/InjectorIcon';

const useStyles = makeStyles()(() => ({
  tooltipTable: {
    'borderCollapse': 'collapse',
    '& th': {
      textAlign: 'left',
      textTransform: 'uppercase',
      fontSize: 10,
      fontWeight: 600,
      opacity: 0.7,
      padding: '2px 12px 4px 0',
    },
    '& td': {
      fontSize: 12,
      padding: '2px 12px 2px 0',
      verticalAlign: 'middle',
    },
  },
  tooltipName: {
    display: 'flex',
    alignItems: 'center',
    gap: 6,
  },
}));

interface Props { sources: InjectorSimple[] | undefined }

const FindingSource = ({ sources }: Props) => {
  const { t } = useFormatter();
  const { classes } = useStyles();
  const maxDisplayedSources = 3;

  const displayedSources = sources?.length ? sources.slice(0, maxDisplayedSources) : null;
  const remainingSources = sources?.length ? sources.slice(maxDisplayedSources, sources.length) : null;

  return (
    <Box sx={{
      display: 'flex',
      flexDirection: 'row',
      gap: 1,
    }}
    >
      {!displayedSources?.length && !remainingSources?.length && '-'}
      {displayedSources?.map(source => (
        <Tooltip key={source.injector_id}>
          <TooltipTrigger asChild>
            <Box sx={{
              display: 'flex',
              flexDirection: 'row',
              alignItems: 'center',
            }}
            >
              <InjectorIcon
                type={source.injector_type}
                style={{
                  height: '24px',
                  width: '24px',
                  borderRadius: '3px',
                }}
              />
            </Box>
          </TooltipTrigger>
          <TooltipContent>{source.injector_type ? t(source.injector_type) : t('Unknown')}</TooltipContent>
        </Tooltip>
      ),
      )}
      {remainingSources && remainingSources.length > 0 && (
        <Tooltip>
          <TooltipTrigger asChild>
            <Chip label={`+${remainingSources.length}`} style={{ flexShrink: 0 }} />
          </TooltipTrigger>
          <TooltipContent>
            <table className={classes.tooltipTable}>
              <tbody>
                {remainingSources.map(source => (
                  <tr key={source.injector_id}>
                    <td>
                      <span className={classes.tooltipName}>
                        <InjectorIcon
                          type={source.injector_type}
                          style={{
                            height: '15px',
                            width: '15px',
                            borderRadius: '3px',
                          }}
                        />
                      </span>
                    </td>
                    <td>{source.injector_type ? t(source.injector_type) : t('Unknown')}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </TooltipContent>
        </Tooltip>
      )}
    </Box>
  );
};

export default FindingSource;
