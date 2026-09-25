import {
  Combobox,
  ComboboxChips,
  ComboboxClear,
  ComboboxContent,
  ComboboxControls,
  ComboboxField,
  ComboboxInput,
  ComboboxTrigger,
} from '@filigran/design-system';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useContext, useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import { type ChannelsHelper } from '../../../../actions/channels/channel-helper';
import { useFormatter } from '../../../../components/i18n';
import { useHelper } from '../../../../store';
import { type Channel } from '../../../../utils/api-types';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import { ArticleContext } from '../../common/Context';
import ChannelIcon from './ChannelIcon';
import { type ChannelOption } from './ChannelOption';

const useStyles = makeStyles()(() => ({
  icon: {
    paddingTop: 4,
    display: 'inline-block',
  },
  text: {
    display: 'inline-block',
    flexGrow: 1,
    marginLeft: 10,
  },
}));

interface Props {
  onChannelsChange: (value: ChannelOption[]) => void;
  onClearChannels?: () => void;
  fullWidth?: boolean;
}

interface ChannelTransformed {
  id: string;
  label: string;
  color: string;
  type: string;
}

const ChannelsFilter: FunctionComponent<Props> = (props) => {
  const { classes } = useStyles();
  const { t } = useFormatter();
  // The library Combobox is always controlled ("there is no uncontrolled mode"),
  // so the field's own selection now lives here — it is what the uncontrolled
  // MUI Autocomplete used to keep internally.
  const [selected, setSelected] = useState<ChannelTransformed[]>([]);
  const theme = useTheme();
  const { fetchChannels } = useContext(ArticleContext);

  useDataLoader(() => fetchChannels());

  const { channels } = useHelper((helper: ChannelsHelper) => ({ channels: helper.getChannels() }));
  const { onChannelsChange, onClearChannels = () => { }, fullWidth } = props;

  // A channel kind carries no severity, so its colour comes from the library's
  // categorical ramp rather than from feedback or brand. The hue names belong to
  // a sibling product's taxonomy and mean nothing here — they were picked for
  // perceptual distance from the values these four replaced, and unlike those
  // they resolve per mode.
  const channelColor = (type?: string) => {
    switch (type) {
      case 'newspaper':
        return theme.palette.designSystem.entities.victimology;
      case 'microblogging':
        return theme.palette.designSystem.entities.location;
      case 'tv':
        return theme.palette.designSystem.entities.allThreats;
      default:
        return theme.palette.designSystem.entities.cases;
    }
  };
  const channelTransform = (n: Channel) => ({
    id: n.channel_id,
    label: n.channel_name,
    color: channelColor(n.channel_type),
    type: n.channel_type,
  });
  const channelsOptions: ChannelTransformed[] = channels.map(channelTransform);
  return (
    <div style={{
      display: 'flex',
      float: 'right',
    }}
    >
      <div style={{ width: fullWidth ? '100%' : 250 }}>
        <Combobox<ChannelTransformed>
          labelPosition="none"
          multiple
          openOnFocus
          options={channelsOptions}
          value={selected}
          onValueChange={(value) => {
            setSelected(value as ChannelTransformed[]);
            // MUI reported a `clear` reason here. Both handlers at the only call
            // site reduce to the same state update, so an empty selection can
            // take the clear path and the prop stays wired.
            const next = value as ChannelTransformed[];
            if (next.length === 0) onClearChannels();
            else onChannelsChange(next);
          }}
          getOptionLabel={option => option.label}
          isOptionEqualToValue={(option, value) => value === undefined || option.id === value.id}
          renderOption={option => (
            <>
              {/* The tint is derived from the channel type and stays on the glyph. */}
              <div className={classes.icon} style={{ color: option.color }}>
                <ChannelIcon type={option.type} />
              </div>
              <div className={classes.text}>{option.label}</div>
            </>
          )}
        >
          <ComboboxField>
            <ComboboxChips />
            <ComboboxInput
              placeholder={t('Channels')}
              aria-label={t('Channels')}
            />
            <ComboboxControls>
              <ComboboxClear />
              <ComboboxTrigger />
            </ComboboxControls>
          </ComboboxField>
          <ComboboxContent />
        </Combobox>
      </div>
    </div>
  );
};

export default ChannelsFilter;
