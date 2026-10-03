import { Paper } from '@filigran/design-system';
import { makeStyles } from 'tss-react/mui';

import ConnectorPage from '../common/ConnectorPage';
import InjectorContracts from './InjectorContracts';

const useStyles = makeStyles()(theme => ({
  paperConnector: {
    marginTop: theme.spacing(3),
    height: '100%',
  },
}));

const InjectorPage = () => {
  const { classes } = useStyles();

  // Always render the shared connector hero (like collectors/executors) with the
  // injector contracts below it. Built-in injectors have no catalog entry, but the
  // hero derives its title/type/description from the injector itself, so they must
  // no longer fall back to a bare contracts list without a header.
  return (
    // `padding={0}` below does NOT render 0, and that is deliberate. `paper` is
    // a global product class in static/css/index.css carrying `padding: 20px`,
    // and it is UNLAYERED — the library's utilities live in Tailwind's
    // `utilities` layer, and unlayered CSS beats layered CSS whatever the
    // specificity or the source order. Measured: `.paper` alone 20px, `p-0`
    // alone 0px, both together 20px in either order. The surface renders the
    // 20px it rendered before the migration — iso, but by accident. The prop is
    // the loser of that cascade, not the winner: do not read it as the padding,
    // and do not "fix" it by changing the prop. Only the class decides.
    // PAPER-GAP-INVENTORY §5.9.
    <ConnectorPage
      extraInfoComponent={(
        <Paper padding={0} className={`paper ${classes.paperConnector}`}>
          <InjectorContracts />
        </Paper>
      )}
    />
  );
};

export default InjectorPage;
