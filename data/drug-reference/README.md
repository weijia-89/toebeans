# Drug reference entries

One JSON file per medication reference item, in `entries/`. Text and approval live in the
same file so an item can never ship without its provenance.

Authority: [`../../docs/adr/0019-vet-recommended-content-framework.md`](../../docs/adr/0019-vet-recommended-content-framework.md),
Amendment 2026-11-16.

## Required shape

```json
{
  "medication": "Methimazole",
  "kind": "summary",
  "text": "Used to manage an overactive thyroid in cats. Usually given with food.",
  "source": {
    "publisher": "Merck Veterinary Manual",
    "url": "https://www.merckvetmanual.com/...",
    "retrieved": "2026-11-16"
  },
  "approval": {
    "approver": "Wei Jia",
    "date": "2026-11-16",
    "decision": "approved",
    "rationale": "Read the source page in full; feeding note matches."
  }
}
```

`kind` is `summary` or `verbatim`. Nothing else parses.

## The five rules

1. A named human read the source page in full and approved this item before it entered the
   database. Approval is per item, never per batch.
2. Editing an approved summary makes it unapproved until it is read and approved again.
3. Summaries never carry dose numbers. A number followed by a dose unit fails the gate. The
   dose stays whatever the vet prescribed.
4. Every displayed item shows the source link.
5. `verbatim` reproduction needs a licence for the publisher text. Until that licence decision
   lands, only `summary` entries ship.

## Enforcement

```bash
bash scripts/test_drug_reference_approval.sh .
bash scripts/test_test_drug_reference_approval.sh .
```

Both run in `scripts/verify_before_push.sh`. The gate fails the build on a missing approval
block, a decision that is not `approved`, a malformed date, a missing source URL, an unknown
kind, or a dose number inside a summary.

The gate cannot check whether the approver read carefully. A rationale of "looks fine" is a
review failure even though the gate accepts the string.
