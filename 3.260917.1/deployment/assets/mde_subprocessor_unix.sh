#!/bin/sh

if [ -z "$1" ]; then
    echo "OpenAEV subprocessor: no argument received (expected Base64 command as first parameter)" >&2
    exit 1
fi
echo "$1" | base64 -d | sh
