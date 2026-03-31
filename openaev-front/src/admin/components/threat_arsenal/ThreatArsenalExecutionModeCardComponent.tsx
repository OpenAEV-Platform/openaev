import {SvgIconComponent} from "@mui/icons-material";
import {Card, CardActionArea, CardContent, Typography} from "@mui/material";
import {useTheme} from "@mui/material/styles";
import {ReactElement} from "react";

interface Props {
  executionMode: {
    icon: ReactElement;
    title: string;
    description: string;
    onClick: () => void;
    disabled: boolean;
  };
}

const ThreatArsenalExecutionModeCardComponent = ({ executionMode }: Props) => {
  const theme = useTheme();

  return (
    <Card
      style={{
        height: 150,
        width: '25vw',
        margin: theme.spacing(1.5),
      }}
    >
      <CardActionArea
        onClick={executionMode.onClick}
        disabled={executionMode.disabled}
        style={{ height: '100%' }}
      >
        <CardContent
          sx={{
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            justifyContent: 'space-between',
            height: '100%',
          }}
        >
          {executionMode.icon}
          <Typography style={{ color: executionMode.disabled ? theme.palette.text?.disabled : "inherit" }}>{executionMode.title}</Typography>
          <Typography style={{ color: executionMode.disabled ? theme.palette.text?.disabled : "inherit" }}>{executionMode.description}</Typography>
        </CardContent>
      </CardActionArea>
    </Card>
  );
};

export default ThreatArsenalExecutionModeCardComponent;