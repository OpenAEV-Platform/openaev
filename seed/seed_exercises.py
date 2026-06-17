#!/usr/bin/env python3
"""
OpenAEV PoC — Demo exercise seeder.
Run AFTER the app is healthy: python3 seed/seed_exercises.py [http://localhost:8080]

Creates demo exercises if they don't already exist, then updates their metadata
(status, dates, name) to match the expected PoC demo state.
"""

import sys, json, time, urllib.request, urllib.error

BASE = sys.argv[1].rstrip('/') if len(sys.argv) > 1 else 'http://localhost:8080'
TOKEN = '7d3259e5-2f02-4fac-a00a-2bad5c589032'

EXERCISES = [
    # (name, status, start_date, end_date)
    ('Finance Dept Credential Theft (5 Endpoints)',      'SCHEDULED', None, None),
    ('APT Mid-Enterprise Campaign (15 Endpoints)',        'SCHEDULED', None, None),
    ('Large Enterprise Full Breach (50 Endpoints)',       'SCHEDULED', None, None),
    ('Finance Department Credential Theft - Run #1',     'FINISHED',  None, None),
    ('APT Mid-Enterprise Campaign - Run #1',              'FINISHED',  None, None),
    ('Large Enterprise Full Breach - Run #1',             'FINISHED',  None, None),
    ('Finance Department Credential Theft - Run #2',     'FINISHED',  None, None),
    ('APT Mid-Enterprise Campaign - Run #2',              'FINISHED',  None, None),
    ('Finance Department Credential Theft - Run #3',     'SCHEDULED', None, None),
    ('APT Mid-Enterprise Campaign - Run #3',              'SCHEDULED', None, None),
    ('APT29 Domain Takeover - Run #1 (Full)',             'FINISHED',  None, None),
    ('APT29 Domain Takeover - Run #2 (Partial)',          'FINISHED',  None, None),
    ('APT29 Scale Test - 3 Endpoints',                   'FINISHED',  None, None),
    ('APT29 Scale Test - 8 Endpoints',                   'FINISHED',  None, None),
    ('APT29 Scale Test - 30 Endpoints',                  'FINISHED',  None, None),
    ('APT29 Scale Test - 50 Endpoints',                  'FINISHED',  None, None),
    ('APT29 Scale Test - 90 Endpoints (47 Active)',      'FINISHED',  None, None),
    ('APT29 Scale Test - 100 Endpoints',                 'FINISHED',  None, None),
]


def api(method, path, body=None):
    url = BASE + path
    data = json.dumps(body).encode() if body else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header('Authorization', f'Bearer {TOKEN}')
    req.add_header('Content-Type', 'application/json')
    try:
        r = urllib.request.urlopen(req, timeout=15)
        raw = r.read()
        return r.status, (json.loads(raw) if raw else {})
    except urllib.error.HTTPError as e:
        raw = e.read().decode()
        return e.code, raw


def wait_healthy(max_wait=120):
    print(f'Waiting for app at {BASE} ...', flush=True)
    for _ in range(max_wait):
        try:
            urllib.request.urlopen(BASE + '/api/me', timeout=3)
            print('App is healthy')
            return True
        except urllib.error.HTTPError as e:
            # 401 means app is up but unauthenticated — that's fine
            if e.code in (401, 403):
                print('App is healthy')
                return True
            time.sleep(1)
        except Exception:
            time.sleep(1)
    print('ERROR: app did not become healthy within', max_wait, 's')
    return False


def get_existing():
    status, resp = api('GET', '/api/exercises')
    if status != 200:
        print('  Could not fetch existing exercises:', resp)
        return {}
    exercises = resp if isinstance(resp, list) else resp.get('exercises', [])
    return {e['exercise_name']: e['exercise_id'] for e in exercises}


def main():
    if not wait_healthy():
        sys.exit(1)

    print('Fetching existing exercises ...')
    existing = get_existing()
    print(f'  Found {len(existing)} existing exercises')

    created = 0
    skipped = 0

    for (name, status, start, end) in EXERCISES:
        if name in existing:
            print(f'  SKIP  {name}')
            skipped += 1
            continue

        s, r = api('POST', '/api/exercises', {
            'exercise_name': name,
            'exercise_mail_from': 'no-reply@openaev.io',
        })
        if s != 200 or not isinstance(r, dict) or 'exercise_id' not in r:
            print(f'  FAIL  create {name!r}: {s} {str(r)[:120]}')
            continue

        eid = r['exercise_id']

        update_body = {
            'exercise_name': name,
            'exercise_mail_from': 'no-reply@openaev.io',
            'exercise_status': status,
        }

        s2, r2 = api('PUT', f'/api/exercises/{eid}', update_body)
        if s2 != 200:
            print(f'  WARN  created {eid} but status update failed: {s2}')
        else:
            print(f'  OK    {name} ({status})')
        created += 1

    print(f'\nDone: {created} created, {skipped} already existed.')


if __name__ == '__main__':
    main()
