import { type Verdict } from './remediationMitigationStore';

// PROTOTYPE - nothing is sent. What the screens know about the finding when they ask for a suggestion.
// The content of the request itself is never shown to the user.
export interface RequestContext {
  findingType?: string;
  verdict?: {
    name: 'prevention' | 'detection' | 'vulnerability';
    value: Verdict;
  };
}
