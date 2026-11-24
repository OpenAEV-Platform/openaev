import {InjectorSimple} from "../../../../utils/api-types";
import {Chip, Tooltip, TooltipContent, TooltipTrigger} from "@filigran/design-system";
import {Box} from "@mui/material";
import InjectorIcon from "../../common/injectors/InjectorIcon";
import {useFormatter} from "../../../../components/i18n";
import {truncate} from "../../../../utils/String";
import {makeStyles} from "tss-react/mui";

const useStyles = makeStyles()(() => ({
    inline: {
        display: 'flex',
        alignItems: 'center',
        gap: 4,
        // Respect the enclosing cell width so the first (shrinkable) chip and the
        // "+N" chip both stay inside it instead of the "+N" being clipped.
        minWidth: 0,
        maxWidth: '100%',
    },
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
    tooltipMore: {
        fontSize: 11,
        opacity: 0.7,
        marginTop: 4,
    },
}));

interface Props {
    sources: InjectorSimple[] | undefined;
}

const FindingSource = ({ sources }: Props) => {
    const { t } = useFormatter();
    const { classes } = useStyles();

    const firstSource = sources?.length ? sources[0] : null;
    const remainingSources = sources?.length ? sources.slice(1, sources.length) : null;

    return (
        <Box sx={{
            display: 'flex',
            flexDirection: 'row',
            gap: 1,
        }}>
            {firstSource &&
                <Tooltip>
                    <TooltipTrigger asChild>
                        <Box sx={{
                            display: 'flex',
                            flexDirection: 'row',
                            alignItems: 'center',
                        }}>
                            <InjectorIcon
                                type={firstSource.injector_type}
                                style={{
                                    height: '30px',
                                    width: '30px',
                                    borderRadius: '3px'
                                }}
                            />
                        </Box>
                    </TooltipTrigger>
                    <TooltipContent>{firstSource.injector_type ? t(firstSource.injector_type) : t('Unknown')}</TooltipContent>
                </Tooltip>
            }
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
                                                  borderRadius: '3px'
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