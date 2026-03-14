import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, memo } from 'react';

export interface NodeColumnBgData {
  phaseName: string;
  color: string;
  isUtility: boolean;
  colWidth: number;
  colHeight: number;
}

const NodeColumnBg: FunctionComponent<{ data: NodeColumnBgData }> = ({ data }) => {
  const theme = useTheme();

  const bgOpacity = theme.palette.mode === 'dark' ? 0.08 : 0.06;

  return (
    <div
      style={{
        width: data.colWidth,
        height: data.colHeight,
        position: 'relative',
        pointerEvents: 'none',
      }}
    >
      {/* Background fill — low opacity */}
      <div
        style={{
          position: 'absolute',
          inset: 0,
          borderRadius: 8,
          background: data.color,
          opacity: bgOpacity,
          border: `1px ${data.isUtility ? 'dashed' : 'solid'} ${data.color}`,
          borderColor: `${data.color}33`,
        }}
      />
      {/* Phase name — full opacity */}
      <div
        style={{
          position: 'absolute',
          top: 10,
          left: 0,
          right: 0,
          textAlign: 'center',
          color: data.color,
          fontSize: 13,
          fontWeight: 600,
          fontFamily: theme.typography.fontFamily,
          opacity: 0.85,
        }}
      >
        {data.phaseName}
      </div>
    </div>
  );
};

export default memo(NodeColumnBg);
