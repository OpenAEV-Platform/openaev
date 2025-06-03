#!/bin/sh -xe
python3 -m venv .dev_venv
. .dev_venv/bin/activate

python3 -m pip install -r requirements.txt

mkdocs serve