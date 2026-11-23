# ADR-0021: On-device veterinary assist posture

> Note (2026-11-22): The agent contract files were removed from this public repository. References in this document to those files mean the local working copies kept in a contributor's checkout.

Date: 2026-11-15
Status: REJECTED, not planned. Decision recorded 2026-11-22.

## Decision

The app will not generate veterinary advice, triage, dose guidance, or treatment suggestions with a language model, on-device or otherwise. The no-AI-features refusal stands. ADR-0019 is unchanged.

## Rationale

A cited Palamedes dossier was read before this decision. It found no validation meeting the near-zero toxin-and-dose error bar the earlier draft set. It also found professional guidance that requires a licensed veterinarian in the loop and informed consent when AI enters clinical decisions, conditions an owner-facing app cannot satisfy. The dossier is kept out of the public repository as decision evidence. It is not app content.

## Consequences

- No model-generated content enters the product.
- The ADR-0019 source constraint and the README no-AI claim stay intact.
- Owner-entered, vet-curated, vet-record-imported, and rule-based content remain permitted under ADR-0019.
- Any future reversal needs a new ADR plus an explicit human approval record naming the approver, date, scope, sources read, rationale, and accepted falsifier.
