# Built-in Collectors

This module contains built-in collectors for the OpenAEV platform.

## Available Collectors

### Expectations Expiration Manager

The `ExpectationsExpirationManagerCollector` is responsible for managing the expiration of inject expectations. It runs as a scheduled job that:

- Monitors pending expectations across all simulations
- Automatically marks expectations as failed when they exceed their configured expiration time
- Helps ensure simulations complete in a timely manner

Configuration properties:
- `openaev.collector.expectations-expiration-manager.enable`: Enable/disable the collector
- `openaev.collector.expectations-expiration-manager.interval`: Interval in seconds between checks

### Expectations Vulnerability Manager

The `ExpectationsVulnerabilityManagerCollector` handles vulnerability-related expectations processing.

## Utility Classes

- `CollectorsUtils`: Constants and helper methods for collector integrations (CrowdStrike, Splunk, Microsoft Sentinel, Microsoft Defender)