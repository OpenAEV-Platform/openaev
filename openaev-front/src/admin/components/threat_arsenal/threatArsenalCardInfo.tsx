import { ExtensionOutlined, LocalOfferOutlined, PersonOutlineOutlined, ScheduleOutlined, SourceOutlined } from '@mui/icons-material';

// Icons of the card meta row, one per kind of fact, the same on Active, Available and review cards.
const size = { fontSize: 16 };
export const CARD_INFO_ICONS = {
  version: <LocalOfferOutlined sx={size} />,
  source: <SourceOutlined sx={size} />,
  person: <PersonOutlineOutlined sx={size} />,
  provider: <ExtensionOutlined sx={size} />,
  date: <ScheduleOutlined sx={size} />,
};
