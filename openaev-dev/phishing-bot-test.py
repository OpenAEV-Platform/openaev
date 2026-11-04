#!/usr/bin/env python3
"""End-to-end check of the phishing bot/human classification, fully local.

Creates a phishing campaign through the REST API (3 recipients), reads the 3 lure emails from the
local Mailpit, then replays one scenario per recipient against the tracking endpoints and prints
what the platform recorded on each tracking row:

  bot      scanner user agent hits the pixel and the page, never confirms
  human    browser user agent, pixel + page, then a confirmation with a realistic dwell
  unknown  browser user agent loads the page but never interacts

Needs the backend (dev profile, SMTP pointed at Mailpit) and the dev containers running. See
openaev-dev/README.md, "Testing the phishing bot detection locally".
"""

import argparse
import json
import re
import shutil
import subprocess
import sys
import time
import urllib.request
import uuid

SCANNER_UA = "Mozilla/5.0 (compatible; BingPreview/1.0b)"
BROWSER_UA = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 Chrome/120.0 Safari/537.36"
# Hits landing within this many seconds of delivery are treated as automated (see
# PhishingTrackingService), so the human and unknown scenarios wait it out first.
AUTOMATION_WINDOW_SECONDS = 10


def call(method, url, body=None, headers=None):
    data = json.dumps(body).encode() if body is not None else None
    request = urllib.request.Request(url, data=data, method=method, headers=headers or {})
    if data is not None:
        request.add_header("Content-Type", "application/json")
    with urllib.request.urlopen(request) as response:
        raw = response.read()
        return json.loads(raw) if raw and raw[:1] in b"{[" else raw


def container_runtime():
    for name in ("podman", "docker"):
        path = shutil.which(name) or shutil.which(name, path="/opt/podman/bin")
        if path:
            return path
    sys.exit("neither podman nor docker found")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawTextHelpFormatter)
    parser.add_argument("--api", default="http://localhost:8080")
    parser.add_argument("--mailpit", default="http://localhost:8025")
    parser.add_argument("--token", default="8e22852f-0e2a-407c-942d-59f1fc4db829",
                        help="admin API token (default: the one of application-dev.properties.example)")
    parser.add_argument("--db-container", default="openaev-dev-pgsql")
    args = parser.parse_args()

    auth = {"Authorization": f"Bearer {args.token}"}
    run = uuid.uuid4().hex[:6]

    def api(method, path, body=None):
        return call(method, args.api + path, body, auth)

    print("== Setting up the campaign")
    call("DELETE", f"{args.mailpit}/api/v1/messages")
    template = api("POST", "/api/phishing/email-templates", {
        "phishing_email_template_name": f"bot-test-lure-{run}",
        "phishing_email_template_subject": "Action required",
        "phishing_email_template_html_body": '<p>Please <a href="{{phishing_url}}">sign in</a></p>',
        "phishing_email_template_from_email": "it@corp.test",
        "phishing_email_template_from_name": "IT",
        "phishing_email_template_add_tracking_pixel": True,
    })
    page = api("POST", "/api/phishing/landing-pages", {
        "phishing_landing_page_name": f"bot-test-page-{run}",
        "phishing_landing_page_html": '<form><input name="username"><input name="password" type="password"></form>',
        "phishing_landing_page_css": "",
    })
    scenarios = ["bot", "human", "unknown"]
    emails = {s: f"{s}-{run}@corp.test" for s in scenarios}
    user_ids = [
        api("POST", "/api/players", {"user_email": emails[s], "user_firstname": s, "user_lastname": run})["user_id"]
        for s in scenarios
    ]
    team = api("POST", "/api/teams", {"team_name": f"bot-test-team-{run}"})
    api("PUT", f"/api/teams/{team['team_id']}/players", {"team_users": user_ids})

    def manual(name, order):
        return {"expectation_type": "MANUAL", "expectation_name": name, "expectation_score": 100.0,
                "expectation_expectation_group": False, "expectation_expiration_time": 86400,
                "expectation_order": order}

    inject = api("POST", "/api/atomic-testings", {
        "inject_title": f"bot-test-{run}",
        "inject_injector_contract": page["phishing_landing_page_id"],
        "inject_content": {
            "emailTemplate": template["phishing_email_template_id"],
            "expectations": [manual("Email not opened", 1), manual("Link not clicked", 2),
                             manual("Credentials not submitted", 3)],
        },
        "inject_teams": [team["team_id"]],
    })
    api("POST", f"/api/atomic-testings/{inject['inject_id']}/launch")

    print("== Waiting for the 3 lure emails in Mailpit")
    tokens = {}
    deadline = time.time() + 180
    while len(tokens) < 3 and time.time() < deadline:
        for message in call("GET", f"{args.mailpit}/api/v1/messages")["messages"]:
            recipient = message["To"][0]["Address"]
            for scenario, email in emails.items():
                if recipient == email and scenario not in tokens:
                    html = call("GET", f"{args.mailpit}/api/v1/message/{message['ID']}")["HTML"]
                    match = re.search(r"/api/hosted/o/([A-Za-z0-9_-]+)", html)
                    if match:
                        tokens[scenario] = match.group(1)
        time.sleep(2)
    if len(tokens) < 3:
        sys.exit(f"only {len(tokens)}/3 emails arrived: is the backend SMTP pointed at Mailpit?")

    print(f"== Waiting {AUTOMATION_WINDOW_SECONDS + 2}s so a human-looking hit is not 'right after delivery'")
    time.sleep(AUTOMATION_WINDOW_SECONDS + 2)

    def hit(path, user_agent, body=None):
        headers = {"User-Agent": user_agent}
        return call("POST" if body is not None else "GET", f"{args.api}/api/hosted/{path}", body, headers)

    print("== Replaying the scenarios")
    # bot: pixel + page right away, scanner signature, no confirmation
    hit(f"o/{tokens['bot']}", SCANNER_UA)
    hit(f"page/{tokens['bot']}", SCANNER_UA)
    # human: pixel + page, then a confirmation after a realistic dwell
    hit(f"o/{tokens['human']}", BROWSER_UA)
    hit(f"page/{tokens['human']}", BROWSER_UA)
    time.sleep(2.3)
    hit(f"confirm/{tokens['human']}", BROWSER_UA, {"dwell_ms": 2300, "webdriver": False})
    # unknown: page loaded by a browser-looking client that never interacts
    hit(f"page/{tokens['unknown']}", BROWSER_UA)

    print("== What the platform recorded (phishing_results)")
    columns = ["phishing_result_opened_at", "phishing_result_clicked_at"]
    expected = {
        "bot": (False, False),
        "human": (True, True),
        "unknown": (False, False),
    }
    labels = ["user opened", "user clicked"]
    failed = False
    for scenario in scenarios:
        query = f"SELECT {', '.join(c + ' IS NOT NULL' for c in columns)} FROM phishing_results " \
                f"WHERE phishing_result_token = '{tokens[scenario]}'"
        out = subprocess.run(
            [container_runtime(), "exec", args.db_container, "psql", "-U", "openaev", "-d", "openaev",
             "-t", "-A", "-F", ",", "-c", query],
            check=True, capture_output=True, text=True).stdout.strip()
        actual = tuple(value == "t" for value in out.split(","))
        ok = actual == expected[scenario]
        failed = failed or not ok
        shown = "  ".join(f"{label}={'yes' if value else 'no '}" for label, value in zip(labels, actual))
        print(f"  {scenario:8} {shown}   {'OK' if ok else 'UNEXPECTED, expected ' + str(expected[scenario])}")
    print("\nThe bot hits are logged by the backend (\"Phishing automated ... ignored for scoring\").")
    print("Mailpit UI: " + args.mailpit)
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
