export interface LogicAction {
  id: string;
  label: string;
  injectorContract?: string;
}

export interface LogicEvent {
  id: string;
  label: string;
  conditions?: string[];
}
