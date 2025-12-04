import { VerifiedOutlined } from '@mui/icons-material';
import { Button, Chip, Tooltip, Typography } from '@mui/material';
import { makeStyles } from 'tss-react/mui';

import colorStyles from '../../../../components/Color';
import ButtonPopover from '../../../../components/common/ButtonPopover';
import { useFormatter } from '../../../../components/i18n';
import { type ConnectorInstance } from '../../../../utils/api-types';
import { type ConnectorMainInfo } from '../common/ConnectorCard';
const useStyles = makeStyles()(theme => ({
  content: {
    display: 'grid',
    gridTemplateColumns: 'auto 1fr',
    columnGap: theme.spacing(2),
    rowGap: theme.spacing(0.5),
    alignItems: 'start',
    width: '100%',
  },
  img: {
    gridRow: 'span 2',
    width: 60,
    height: 60,
    borderRadius: 4,
  },
  firstLine: {
    display: 'flex',
    overflow: 'hidden',
    gap: theme.spacing(2),
  },
  autoMarginLeft: { marginLeft: 'auto' },
  cardTitle: {
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
    maxHeight: '100%',
  },
  pageTitle: {
    whiteSpace: 'normal',
    overflow: 'visible',
    textOverflow: 'unset',
    margin: '0px',
  },
  chipInList: {
    margin: theme.spacing(0.25),
    fontSize: 12,
    height: 20,
    flexShrink: 0,
    justifySelf: 'start',
    textTransform: 'uppercase',
    width: 'auto',
    borderRadius: 4,
  },
  chipVerified: {
    padding: theme.spacing(2),
    fontSize: 12,
    height: 20,
    textTransform: 'uppercase',
    width: 'auto',
    borderRadius: 4,
  },
  verifiedOutlined: {
    position: 'absolute',
    top: 10,
    right: 10,
  },
}));

type ConnectorHeaderProps = {
  connector: ConnectorMainInfo;
  detailsTitle?: boolean;
  instanceCurrentStatus?: ConnectorInstance['connector_instance_current_status'];
  showDeployButton?: boolean;
  showUpdateButton?: boolean;
  showUpdateStatusButton?: boolean;
  onDeployBtnClick?: () => void;
};

const ConnectorTitle = ({
  connector,
  detailsTitle = false,
  instanceCurrentStatus,
  showDeployButton = false,
  showUpdateButton = false,
  showUpdateStatusButton = false,
  onDeployBtnClick = () => {},
}: ConnectorHeaderProps) => {
  // Standard hooks
  const { classes } = useStyles();
  const { t } = useFormatter();

  return (
    <div className={classes.content}>
      <img
        src={connector.connectorLogoUrl}
        alt={connector.connectorLogoName}
        className={classes.img}
      />
      <div className={classes.firstLine}>
        <Tooltip title={connector.connectorName}>
          <Typography
            variant="h1"
            className={detailsTitle ? classes.pageTitle : classes.cardTitle}
          >
            {connector.connectorName}
          </Typography>
        </Tooltip>

        {connector.isVerified && detailsTitle && (
          <>
            <Chip
              variant="filled"
              className={classes.chipVerified}
              style={colorStyles.green}
              icon={<VerifiedOutlined color="success" />}
              label={t('Verified')}
            />
            { instanceCurrentStatus && (
              <Chip
                variant="filled"
                className={classes.chipVerified}
                style={instanceCurrentStatus == 'started' ? colorStyles.green : colorStyles.red}
                label={instanceCurrentStatus == 'started' ? t('Started') : t('Stopped')}
              />
            )}
            {showUpdateButton && (
              <ButtonPopover
                className={classes.autoMarginLeft}
                entries={[{
                  label: 'delete',
                  action: () => console.log('test'),
                  userRight: true, // TODO
                }, {
                  label: 'update',
                  action: () => console.log('test'),
                  userRight: true, // TODO
                }]}
                variant="toggle"
              />
            )}
            {showDeployButton && (
              <Button
                className={classes.autoMarginLeft}
                variant="contained"
                color="primary"
                size="small"
                onClick={onDeployBtnClick}
              >
                {t('Deploy')}
              </Button>
            )}
            {showUpdateStatusButton && (
              <Button
                className={!showUpdateButton ? classes.autoMarginLeft : ''}
                variant="outlined"
                color={instanceCurrentStatus == 'started' ? 'error' : 'success'}
                size="small"
                onClick={onDeployBtnClick}
              >
                {instanceCurrentStatus == 'started' ? t('Stop') : t('Start')}
              </Button>
            )}
          </>
        )}
      </div>

      <div>
        <Chip
          variant="outlined"
          className={classes.chipInList}
          color="primary"
          label={connector.connectorType}
        />
        {connector.connectorUseCases && connector.connectorUseCases.map((useCase: string) => (
          <Chip
            key={useCase}
            variant="outlined"
            className={classes.chipInList}
            color="default"
            label={useCase}
          />
        ))}
      </div>
      {connector.isVerified && (
        <Tooltip title={t('Verified')} className={classes.verifiedOutlined}>
          <VerifiedOutlined color="success" />
        </Tooltip>
      )}
    </div>
  );
};

export default ConnectorTitle;
// const useStyles = makeStyles()(theme => ({
//   content: {
//     display: 'grid',
//     gridTemplateColumns: 'auto auto 1fr',
//     gridTemplateRows: 'auto auto',
//     columnGap: theme.spacing(2),
//     rowGap: theme.spacing(0.5),
//     alignItems: 'start',
//   },
//   img: {
//     gridRow: 'span 2',
//     width: 60,
//     height: 60,
//     borderRadius: 4,
//   },
//   cardTitle: {
//     gridColumn: 'span 2',
//     whiteSpace: 'nowrap',
//     overflow: 'hidden',
//     textOverflow: 'ellipsis',
//     maxHeight: '100%',
//   },
//   pageTitle: {
//     whiteSpace: 'normal',
//     overflow: 'visible',
//     textOverflow: 'unset',
//     margin: '0px',
//   },
//   chipInList: {
//     margin: theme.spacing(0.25),
//     fontSize: 12,
//     height: 20,
//     flexShrink: 0,
//     justifySelf: 'start',
//     textTransform: 'uppercase',
//     width: 'auto',
//     borderRadius: 4,
//   },
//   chipVerified: {
//     padding: theme.spacing(2),
//     fontSize: 12,
//     height: 20,
//     textTransform: 'uppercase',
//     width: 'auto',
//     borderRadius: 4,
//   },
//   verifiedOutlined: {
//     position: 'absolute',
//     top: 10,
//     right: 10,
//   },
// }));
//
// type ConnectorHeaderProps = {
//   connector: ConnectorMainInfo;
//   detailsTitle?: boolean;
//   instanceCurrentStatus: ConnectorInstance['connector_instance_current_status'];
//   showDeployButton?: boolean;
//   onDeployBtnClick?: () => void;
// };
//
// const ConnectorTitle = ({
//   connector,
//   detailsTitle = false,
//   instanceCurrentStatus,
//   showDeployButton = false,
//   onDeployBtnClick = () => {},
// }: ConnectorHeaderProps) => {
//   // Standard hooks
//   const { classes } = useStyles();
//   const { t } = useFormatter();
//
//   return (
//     <div className={classes.content}>
//       <img
//         src={connector.connectorLogoUrl}
//         alt={connector.connectorLogoName}
//         className={classes.img}
//       />
//       <Tooltip title={connector.connectorName}>
//         <Typography
//           variant="h1"
//           className={detailsTitle ? classes.pageTitle : classes.cardTitle}
//         >
//           {connector.connectorName}
//         </Typography>
//       </Tooltip>
//
//       {connector.isVerified && detailsTitle && (
//         <div>
//           <Chip
//             variant="filled"
//             className={classes.chipVerified}
//             style={colorStyles.green}
//             icon={<VerifiedOutlined color="success" />}
//             label={t('Verified')}
//           />
//           { instanceCurrentStatus == 'stopped' && (
//             <Chip
//               variant="filled"
//               style={colorStyles.red}
//               icon={<VerifiedOutlined color="success" />}
//               label={t('Verified')}
//             />
//           )}
//           {showDeployButton && (
//             <Button
//               className={classes.deployBtn}
//               variant="contained"
//               color="primary"
//               size="small"
//               onClick={onDeployBtnClick}
//             >
//               {t('Deploy')}
//             </Button>
//           )}
//         </div>
//       )}
//
//       <div>
//         <Chip
//           variant="outlined"
//           className={classes.chipInList}
//           color="primary"
//           label={connector.connectorType}
//         />
//         {connector.connectorUseCases && connector.connectorUseCases.map((useCase: string) => (
//           <Chip
//             key={useCase}
//             variant="outlined"
//             className={classes.chipInList}
//             color="default"
//             label={useCase}
//           />
//         ))}
//       </div>
//       {connector.isVerified && (
//         <Tooltip title={t('Verified')} className={classes.verifiedOutlined}>
//           <VerifiedOutlined color="success" />
//         </Tooltip>
//       )}
//     </div>
//   );
// };
//
// export default ConnectorTitle;
