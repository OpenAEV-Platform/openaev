import argparse
import logging
import os
import re
import time
from datetime import date

import requests
from OAEV_utils.release_utils import check_release, closeRelease, download_url

logging.basicConfig(encoding="utf-8", level=logging.INFO)

parser = argparse.ArgumentParser("release")
parser.add_argument(
    "branch_platform", help="The new version number of the release.", type=str
)
parser.add_argument(
    "previous_version", help="The previous version number of the release.", type=str
)
parser.add_argument(
    "new_version", help="The new version number of the release.", type=str
)
parser.add_argument(
    "github_token", help="The github token to use for the release note.", type=str
)
parser.add_argument(
    "--dev", help="Flag to prevent pushing the release.", action="store_false"
)
args = parser.parse_args()

previous_version = args.previous_version
new_version = args.new_version
branch_platform = args.branch_platform
github_token = args.github_token

os.environ["DRONE_COMMIT_AUTHOR"] = "Filigran-Automation"
os.environ["GIT_AUTHOR_NAME"] = "Filigran Automation"
os.environ["GIT_AUTHOR_EMAIL"] = "automation@filigran.io"
os.environ["GIT_COMMITTER_NAME"] = "Filigran Automation"
os.environ["GIT_COMMITTER_EMAIL"] = "automation@filigran.io"

# Platform
logging.info("[platform] Starting the release")

# Pom
with open(
    "pom.xml",
    "r",
) as file:
    filedata = file.read()
filedata = re.sub(
    r"<groupId>io.openaev</groupId>(\s+)<artifactId>(.*)</artifactId>(\s+)<version>"
    + previous_version
    + "</version>",
    r"<groupId>io.openaev</groupId>\1<artifactId>\2</artifactId>\3<version>"
    + new_version
    + "</version>",
    filedata,
)
with open(
    "pom.xml",
    "w",
) as file:
    file.write(filedata)
with open(
    "./openaev-api/pom.xml",
    "r",
) as file:
    filedata = file.read()
filedata = re.sub(
    r"<groupId>io.openaev</groupId>(\s+)<artifactId>(.*)</artifactId>(\s+)<version>"
    + previous_version
    + "</version>",
    r"<groupId>io.openaev</groupId>\1<artifactId>\2</artifactId>\3<version>"
    + new_version
    + "</version>",
    filedata,
)
with open(
    "./openaev-api/pom.xml",
    "w",
) as file:
    file.write(filedata)
with open(
    "./openaev-framework/pom.xml",
    "r",
) as file:
    filedata = file.read()
filedata = re.sub(
    r"<groupId>io.openaev</groupId>(\s+)<artifactId>(.*)</artifactId>(\s+)<version>"
    + previous_version
    + "</version>",
    r"<groupId>io.openaev</groupId>\1<artifactId>\2</artifactId>\3<version>"
    + new_version
    + "</version>",
    filedata,
)
with open(
    "./openaev-framework/pom.xml",
    "w",
) as file:
    file.write(filedata)
with open(
    "./openaev-model/pom.xml",
    "r",
) as file:
    filedata = file.read()
filedata = re.sub(
    r"<groupId>io.openaev</groupId>(\s+)<artifactId>(.*)</artifactId>(\s+)<version>"
    + previous_version
    + "</version>",
    r"<groupId>io.openaev</groupId>\1<artifactId>\2</artifactId>\3<version>"
    + new_version
    + "</version>",
    filedata,
)
with open(
    "./openaev-model/pom.xml",
    "w",
) as file:
    file.write(filedata)

# Package.json
with open("./openaev-front/package.json", "r") as file:
    filedata = file.read()
filedata = filedata.replace(
    '"version": "' + previous_version + '"', '"version": "' + new_version + '"'
)
with open("./openaev-front/package.json", "w") as file:
    file.write(filedata)

logging.info("[platform] Pushing to " + branch_platform)
os.system(
    'git commit -a -m "[all] Release '
    + new_version
    + '" > /dev/null 2>&1 && git push origin '
    + branch_platform
    + " > /dev/null 2>&1"
)

logging.info("[platform] Tagging")
os.system("git tag " + new_version + " && git push --tags > /dev/null 2>&1")

logging.info(
    "[platform] Tag pushed! Checking that the release has been done ..."
)

check_release(
    "https://hub.docker.com/v2/repositories/openaev/platform/tags?page_size=1000",
    new_version,
)

logging.info("[platform] Generating release")
os.system("gren release > /dev/null 2>&1")

# Modify the release note
logging.info("[platform] Getting the current release note")
release = requests.get(
    "https://api.github.com/repos/OpenAEV-Platform/openaev/releases/latest",
    headers={
        "Accept": "application/vnd.github+json",
        "Authorization": "Bearer " + github_token,
        "X-GitHub-Api-Version": "2022-11-28",
    },
)
release_data = release.json()
release_body = release_data["body"]

logging.info("[platform] Generating the new release note")
github_release_note = requests.post(
    "https://api.github.com/repos/OpenAEV-Platform/openaev/releases/generate-notes",
    headers={
        "Accept": "application/vnd.github+json",
        "Authorization": "Bearer " + github_token,
        "X-GitHub-Api-Version": "2022-11-28",
    },
    json={"tag_name": new_version, "previous_tag_name": previous_version},
)
github_release_note_data = github_release_note.json()
github_release_note_data_body = github_release_note_data["body"]
if "Full Changelog" not in release_body:
    new_release_note = (
        release_body
        + "\n"
        + github_release_note_data_body.replace(
            "## What's Changed", "#### Pull Requests:\n"
        ).replace("## New Contributors", "#### New Contributors:\n")
    )
else:
    new_release_note = release_body

logging.info("[platform] Updating the release")
requests.patch(
    "https://api.github.com/repos/OpenAEV-Platform/openaev/releases/"
    + str(release_data["id"]),
    headers={
        "Accept": "application/vnd.github+json",
        "Authorization": "Bearer " + github_token,
        "X-GitHub-Api-Version": "2022-11-28",
    },
    json={"body": new_release_note},
)

logging.info("[platform] Uploading release artifacts...")

logging.info("[platform] Downloading latest builds")
today = date.today()
timestamp = str(today.year) + "{:02d}".format(today.month) + "{:02d}".format(today.day)
download_url(
    "https://filigran.jfrog.io/artifactory/openaev/openaev-" + timestamp + ".tar.gz",
    save_path="openaev-release-" + new_version + ".tar.gz",
)
download_url(
    "https://filigran.jfrog.io/artifactory/openaev/openaev-"
    + timestamp
    + "_musl.tar.gz",
    save_path="openaev-release-" + new_version + "_musl.tar.gz",
)

logging.info("[platform] Updating the release")
with open("openaev-release-" + new_version + ".tar.gz", "rb") as f:
    data = f.read()
requests.post(
    "https://uploads.github.com/repos/OpenAEV-Platform/openaev/releases/"
    + str(release_data["id"])
    + "/assets?name=openaev-release-"
    + new_version
    + ".tar.gz",
    headers={
        "Accept": "application/vnd.github+json",
        "Authorization": "Bearer " + github_token,
        "X-GitHub-Api-Version": "2022-11-28",
        "Content-Type": "application/octet-stream",
    },
    data=data,
)
with open("openaev-release-" + new_version + "_musl.tar.gz", "rb") as f:
    data = f.read()
requests.post(
    "https://uploads.github.com/repos/OpenAEV-Platform/openaev/releases/"
    + str(release_data["id"])
    + "/assets?name=openaev-release-"
    + new_version
    + "_musl.tar.gz",
    headers={
        "Accept": "application/vnd.github+json",
        "Authorization": "Bearer " + github_token,
        "X-GitHub-Api-Version": "2022-11-28",
        "Content-Type": "application/octet-stream",
    },
    data=data,
)

closeRelease(
    "https://api.github.com/repos/OpenAEV-Platform/openaev", new_version, github_token
)

logging.info("[platform] Release done!")
