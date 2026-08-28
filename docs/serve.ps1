# Stop execution if any command fails
$ErrorActionPreference = "Stop"

python -m venv .dev_venv
. .\.dev_venv\Scripts\Activate.ps1

python -m pip install -r requirements.txt

mkdocs serve
