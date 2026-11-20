#!/bin/sh
# The contract openaev of OpenAEV: the token a collector or an injector calls its API with, the one
# of its admin (OPENAEV_ADMIN_TOKEN of the container). Runs in the OpenAEV container; its enrollment
# has no data. Answers { "token": ... }.
set -eu
printf '{"token":"%s"}' "$OPENAEV_ADMIN_TOKEN"
