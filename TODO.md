X - Branch for AP-SEEN from change-sns
X - Update Schema in README.
X - Update TTL var names.
X - Update app output that uses table names w/ TTL.

X - Update table schemas for change and seen.

X - Figure out schema issue by which we can retrieve full container history
Store each item in change in SQS ingestion.


Upsert option to store each item in seen.

Only store seen if matches newest item in change.

Tests
Use Faradays ensure-table at startup to ensure tables exist
PR


Finish tech. design of WRT in GDoc
Branch from PR for WRT from AP-SEEN
Update Schema in README.

Make sure this works: Draft updates for same user in 2 tabs.
