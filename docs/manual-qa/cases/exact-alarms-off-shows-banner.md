# Exact alarms off shows a Today banner

**Source:** PR #105, test plan case Exact alarms off. PR #106 adds the same setup on API 31 or 32 and expects the banner to stay until exact alarms are turned on. Neither PR is merged on `origin/main` at `9169d6ff94de`.

## Steps

1. On API 31 or 32, turn Alarms and reminders off for toebeans.
2. Open Today.

## Expected result

A sage banner about doses firing up to 10 minutes late. PR #106: the banner stays until exact alarms are turned on.
