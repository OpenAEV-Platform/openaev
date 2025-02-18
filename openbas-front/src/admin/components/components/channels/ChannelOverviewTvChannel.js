import { Card, CardHeader, Grid, Skeleton, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { makeStyles } from 'tss-react/mui';

const useStyles = makeStyles()(() => ({
  root: {
    flexGrow: 1,
    paddingBottom: 50,
  },
  logo: {
    maxHeight: 200,
    maxWidth: 300,
  },
}));

const ChannelOverviewTvChannel = ({ channel }) => {
  const { classes } = useStyles();
  const theme = useTheme();
  const isDark = theme.palette.mode === 'dark';
  const logo = isDark ? channel.logoDark : channel.logoLight;
  return (
    <div className={classes.root}>
      {logo && channel.channel_mode !== 'title' && (
        <div
          style={{
            margin: '0 auto',
            textAlign: 'center',
            marginBottom: 15,
          }}
        >
          <img
            src={`/api/documents/${logo.document_id}/file`}
            className={classes.logo}
          />
        </div>
      )}
      {channel.channel_mode !== 'logo' && (
        <Typography
          variant="h1"
          style={{
            textAlign: 'center',
            color: isDark
              ? channel.channel_primary_color_dark
              : channel.channel_primary_color_light,
            fontSize: 40,
          }}
        >
          {channel.channel_name}
        </Typography>
      )}
      <Typography
        variant="h2"
        style={{ textAlign: 'center' }}
      >
        {channel.channel_description}
      </Typography>
      <Grid container={true} spacing={3}>
        <Grid item={true} xs={8}>
          <Card sx={{ width: '100%' }}>
            <CardHeader
              avatar={(
                <Skeleton
                  animation={false}
                  variant="circular"
                  width={40}
                  height={40}
                />
              )}
              title={(
                <Skeleton
                  animation={false}
                  height={10}
                  width="80%"
                  style={{ marginBottom: 6 }}
                />
              )}
              subheader={<Skeleton animation={false} height={10} width="40%" />}
            />
            <Skeleton
              sx={{ height: 291 }}
              animation={false}
              variant="rectangular"
            />
          </Card>
        </Grid>
        <Grid item={true} xs={4}>
          <Card sx={{ width: '100%' }}>
            <CardHeader
              avatar={(
                <Skeleton
                  animation={false}
                  variant="circular"
                  width={40}
                  height={40}
                />
              )}
              title={(
                <Skeleton
                  animation={false}
                  height={10}
                  width="80%"
                  style={{ marginBottom: 6 }}
                />
              )}
              subheader={<Skeleton animation={false} height={10} width="40%" />}
            />
            <Skeleton
              sx={{ height: 100 }}
              animation={false}
              variant="rectangular"
            />
          </Card>
          <Card sx={{ width: '100%' }} style={{ marginTop: 20 }}>
            <CardHeader
              avatar={(
                <Skeleton
                  animation={false}
                  variant="circular"
                  width={40}
                  height={40}
                />
              )}
              title={(
                <Skeleton
                  animation={false}
                  height={10}
                  width="80%"
                  style={{ marginBottom: 6 }}
                />
              )}
              subheader={<Skeleton animation={false} height={10} width="40%" />}
            />
            <Skeleton
              sx={{ height: 100 }}
              animation={false}
              variant="rectangular"
            />
          </Card>
        </Grid>
      </Grid>
      <Grid container={true} spacing={3} style={{ marginTop: 0 }}>
        <Grid item={true} xs={4}>
          <Card sx={{ width: '100%' }}>
            <CardHeader
              avatar={(
                <Skeleton
                  animation={false}
                  variant="circular"
                  width={40}
                  height={40}
                />
              )}
              title={(
                <Skeleton
                  animation={false}
                  height={10}
                  width="80%"
                  style={{ marginBottom: 6 }}
                />
              )}
              subheader={<Skeleton animation={false} height={10} width="40%" />}
            />
            <Skeleton
              sx={{ height: 180 }}
              animation={false}
              variant="rectangular"
            />
          </Card>
        </Grid>
        <Grid item={true} xs={4}>
          <Card sx={{ width: '100%' }}>
            <CardHeader
              avatar={(
                <Skeleton
                  animation={false}
                  variant="circular"
                  width={40}
                  height={40}
                />
              )}
              title={(
                <Skeleton
                  animation={false}
                  height={10}
                  width="80%"
                  style={{ marginBottom: 6 }}
                />
              )}
              subheader={<Skeleton animation={false} height={10} width="40%" />}
            />
            <Skeleton
              sx={{ height: 180 }}
              animation={false}
              variant="rectangular"
            />
          </Card>
        </Grid>
        <Grid item={true} xs={4}>
          <Card sx={{ width: '100%' }}>
            <CardHeader
              avatar={(
                <Skeleton
                  animation={false}
                  variant="circular"
                  width={40}
                  height={40}
                />
              )}
              title={(
                <Skeleton
                  animation={false}
                  height={10}
                  width="80%"
                  style={{ marginBottom: 6 }}
                />
              )}
              subheader={<Skeleton animation={false} height={10} width="40%" />}
            />
            <Skeleton
              sx={{ height: 180 }}
              animation={false}
              variant="rectangular"
            />
          </Card>
        </Grid>
      </Grid>
    </div>
  );
};

export default ChannelOverviewTvChannel;
