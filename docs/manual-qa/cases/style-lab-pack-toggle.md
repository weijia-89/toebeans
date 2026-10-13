# Style lab pack toggle

**Source:** PR #53 (`c5799da44f75`), style lab visual sign-off. Same check is printed by `scripts/manual_qa_boot.sh --open-style-lab` in commit `2953b2918848`.

## Steps

1. Open `docs/style-lab/index.html` in a browser.
2. Toggle terracotta-warm, sage-calm, and high-contrast.
3. Read `docs/style-lab/tokens-snapshot.json` alongside the page.
4. Do not edit the Chosen line in `docs/style-lab/DECISIONS.md` until intentional sign-off.

## Expected result

Colors update without console errors. The boot script's pass line is that CSS updates when variant packs are toggled.
