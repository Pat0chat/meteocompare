#!/usr/bin/env python3
"""Reviewed expected status grid; independent of the production algorithm.

Each expected value follows the strict mathematical >50% majority rule:
- native code present on >half of the *real* period -> that condition is known;
- unknown hours require INCOMPLETE unless the entire period is covered;
- no hours -> UNAVAILABLE, not a fabricated code.
This fixture is additive: the immutable v1 historical corpus is not rewritten.
"""
from datetime import date, datetime, timedelta, timezone
from pathlib import Path
from zoneinfo import ZoneInfo
import hashlib
import json
import csv

root=Path(__file__).resolve().parents[1]/'app/src/test/resources/weather_regression/v2'
root.mkdir(parents=True,exist_ok=True)
rows=[]
for zone in ['UTC','Europe/Paris','America/New_York','Asia/Tokyo']:
 for day in ['2026-03-29','2026-10-25','2026-11-13']:
  dt=date.fromisoformat(day)
  n=round((datetime.combine(dt+timedelta(days=1),datetime.min.time(),ZoneInfo(zone)).astimezone(timezone.utc)
    -datetime.combine(dt,datetime.min.time(),ZoneInfo(zone)).astimezone(timezone.utc)).total_seconds()/3600)
  for native in [True,False]:
   for wmo,condition in [(0,'CLEAR'),(61,'RAIN')]:
    for known in range(n+1):
     label='native' if native else 'estimated'
     status=('UNAVAILABLE' if known==0 else
       ('RELIABLE' if native else 'ESTIMATED') if known==n else 'INCOMPLETE')
     winning=condition if known>n/2 else '_'
     expected=(status if known>n/2 or known==0 else 'INCOMPLETE')
     rows.append({
      'id':f'{zone.replace("/","_")}-{day}-{label}-{wmo}-{known:02d}',
      'zone':zone, 'date':day,'source':label,'wmo':wmo,'total_hours':n,
      'known_hours':known,'expected_condition':winning,
      'expected_status':expected,
      'expected_origin': 'NONE' if known<=n/2 else ('HOURLY_DOMINANT' if native else 'SAME_MODEL_VARIABLES')
     })
keys=['id','zone','date','source','wmo','total_hours','known_hours','expected_condition','expected_status','expected_origin']
with (root/'day_status.tsv').open('w',encoding='utf-8',newline='') as f:
 writer=csv.DictWriter(f,fieldnames=keys,delimiter='\t',lineterminator='\n')
 writer.writeheader(); writer.writerows(rows)
manifest={'version':2,'dataset':'condition_decision_status','cases':len(rows),
 'sha256':hashlib.sha256((root/'day_status.tsv').read_bytes()).hexdigest(),
 'designed_expectations':'majority > half UTC duration, native vs inferred, partial, no-data, DST'}
(root/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print('status corpus generated',len(rows),'cases')
