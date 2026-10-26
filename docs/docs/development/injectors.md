---
last_verified: 2026-10-14
---

# Injector development

This page explains what OpenAEV expects from an Injector: a process that adds new types of Injects to the platform.

To set up a new Injector project (template, `pyoaev` dependency, local development), follow the [Injectors repository README](https://github.com/OpenAEV-Platform/injectors#development). For a working example, read the [HTTP Injector](https://github.com/OpenAEV-Platform/injectors/tree/main/http-query). For a functional overview, see [Injectors](../integrations/injectors/injectors.md).

## How an Injector works with OpenAEV

An Injector talks to OpenAEV through the REST API and a RabbitMQ queue, so you can write it in any language.

1. **Register.** The Injector sends `POST /api/injectors` as a multipart request: an `input` part with its `injector_id`, `injector_name`, `injector_type` and `injector_contracts`, and an optional PNG `icon` part. The response gives the RabbitMQ `connection` details and the queue to `listen` to: `<prefix>_injector_<injector_id>`, where the prefix is `openaev.rabbitmq.prefix` (`openaev` by default). The Injector then appears under **Integrations > Deployed**.
2. **Consume.** OpenAEV pushes each Inject to execute to this queue.
3. **Report.** The Injector sends the result with `POST /api/injects/execution/callback/{injectId}`, with at least `execution_message` and `execution_status`.

## Contracts

A contract lists the fields of an Inject. OpenAEV uses it to build the Inject form in the UI, and the Injector uses it to read the values the user entered. An Injector can declare several contracts.

## What's next?

- [Deploy Injectors](../integrations/injectors/deploy-injectors.md) -- Deploy and configure an Injector
- [Collector development](collectors.md) -- Build a custom Collector
- [REST API](../reference/apis/rest-api.md) -- Authentication and API reference
