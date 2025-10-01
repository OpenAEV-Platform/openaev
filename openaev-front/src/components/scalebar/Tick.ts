export interface Tick {
  value: number;
  backgroundColor: string;
  label: string;
}

export interface Scale {
  min: Tick;
  max: Tick;
  ticks: Array<Tick>;
}
