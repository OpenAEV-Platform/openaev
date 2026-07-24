// Built-in injectors and collectors have no catalog entry, so their catalog
// short description is empty. These fallback descriptions (keyed by the
// injector/collector type) give every built-in connector a proper description
// on the catalog card and the detail hero. Values are passed through t() at the
// call site so they can be translated.
const BUILTIN_CONNECTOR_DESCRIPTIONS: Record<string, string> = {
  // Injectors
  openaev_manual: 'Log manual actions carried out outside the platform so they are tracked in a scenario.',
  openaev_email: 'Send emails to your targets to run phishing and awareness simulations.',
  openaev_channel: 'Publish media pressure articles on channels to simulate information operations.',
  openaev_challenge: 'Deliver challenges to players to test their detection and response reflexes.',
  openaev_implant: 'Execute payloads on endpoints through the built-in OpenAEV agent.',
  // Executors
  openaev_agent: 'Run injects directly on your endpoints through the native OpenAEV agent, without any third-party tool.',
  // Collectors
  openaev_fake_detector: 'Automatically expire inject expectations left unfilled past their time window.',
  openaev_expectations_vulnerability_manager: 'Score vulnerability expectations against detected findings to track exposure.',
};

/** Returns the fallback description for a built-in connector type, or undefined. */
const builtinConnectorDescription = (type: string | undefined): string | undefined => (
  type ? BUILTIN_CONNECTOR_DESCRIPTIONS[type] : undefined
);

export default builtinConnectorDescription;
