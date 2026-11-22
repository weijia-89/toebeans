# On-device veterinary model feasibility: training a small local model on veterinary sources

> Note (2026-11-22): The agent contract files were removed from this public repository. References in this document to those files mean the local working copies kept in a contributor's checkout.

Date: 2026-11-15
Author: research subagent, sprint/research-2026-11-15 (off origin/main 20ce2b2)
Input to: ADR-0021 draft (`docs/adr/0021-on-device-vet-assist-posture.md`, Proposed)
Confidence labels: **High** = primary source verified this session. **Medium** = secondary source or inference from verified facts; flagged. **Low** = unverified; flagged as missing primary source.

## 0. Point first

The operator directive asks to remove the "LLM generated advice" refusal and instead train a very small local model on high-quality veterinary sources so the app can give suggestions users should check, plus emergency-line links. The verdict of this research:

1. **The ban and the goal are misaligned at the source pathway, not at data quality.** ADR-0019 permits vet-recommended content only from owner-entered, vet-record-imported, or vet-advisory-board-curated sources, delivered rule-based. A model-generated suggestion is banned regardless of what it was trained on and regardless of where it runs (confidence High, from ADR-0019 § Decision § 1 and § 4).
2. **The on-device model path is not ready under this repo's evidence standard** (confidence High). Three independent blockers: no legally clean training corpus with the clinical depth the goal needs; hardware floors from ADR-0008 that exclude everything except a 270M-parameter model; and a reliability literature that shows the exact failure mode (confident wrong dose or toxin answer) is the worst-measured class even for frontier models, with no published evidence at all for sub-1B models on veterinary medication questions.
3. **The goal itself is reachable without amendment**: static vet-curated suggestion packs (ADR-0019 pathway 3; the rule-pack machinery already exists in ADR-0020), plus the emergency-line links that are already being shipped separately without AI.
4. **Cheapest falsifying experiment** (about one day, no app code): run Qwen3-0.6B-Instruct and Gemma 3 270M zero-shot on a 200-question set built from public-domain FDA CVM label text; measure exact-dose recall, toxin-escalation correctness, and refusal correctness. § 9 specifies the pass bar.

## 1. The operator's goal and the gap to ADR-0019

The directive, restated: drop the posture that LLM-generated advice is forbidden; train a very small, local model on high-quality veterinary sources (Merck Vet Manual, official books); the model gives suggestions that users should verify; emergency-line links ship alongside. The emergency-links half is compliant today as static owner-facing content and is being shipped separately without AI; this document covers it only once, here, and no further.

ADR-0019 (`docs/adr/0019-vet-recommended-content-framework.md`, Status: Proposed) is the operative framework. Its four conjunctive constraints are: (1) source from owner-entered, vet-record-imported, or vet-advisory-board-curated pathways; (2) rule-based delivery, "the same input always produces the same output"; (3) framing that never implies the app is the source of medical judgment; (4) a refusal carve-out that keeps "AI symptom checkers", "AI-generated diagnostic content", "AI-derived treatment recommendations", and "any feature where the LLM, not a vet or the owner, is the source of advice" hard-forbidden. Its § Rejected alternatives rejected "LLM-generated advice if a vet manually reviews each output" because "auditability and reproducibility are unenforceable at scale; the LLM remains the source, the vet is a rubber stamp." Its § Revisit conditions reopen the question if "a production-safe LLM auditability framework emerges that the research community considers usable for medication-adjacent advice (none exists in mid-2026)."

The gap: the directive's core mechanic (a model produces the suggestion text) fails constraint 1 and constraint 4 as written. Training-data licensing and on-device execution are real engineering improvements, but they are improvements on axes ADR-0019 does not currently weigh: the ADR bans the source class, not the risk level. That is why this research ends in a draft ADR presenting a posture decision rather than an implementation plan.

Two status facts worth recording (both verified in the worktree):

- ADR-0019 is Status: Proposed, but its carve-out already shipped into `AGENTS.md` line 33 (per ADR-0020, vet-visit domain model, which records F1 landing via PR #26). The framework is de facto operative; ADR-0021 must not change ADR-0019's status.
- ADR-0020 (Accepted) already builds the alternative path: the `VetVisit` / `Vaccine` / rule-pack model, with operator curation pre-board and provenance metadata.

## 2. Model candidates for on-device Android, late 2026

All names, sizes, and licenses verified this session. "4-bit size" is the shipped artifact size at a 4-bit-class quantization.

| Model | Params | Context | License | 4-bit size | Verified source |
|---|---|---|---|---|---|
| Gemma 3 270M | 270M | 32K | Gemma Terms of Use (custom, not OSI) | 292 MB (ollama q4); 721 MB total runtime footprint incl. KV cache | developers.googleblog.com/en/introducing-gemma-3-270m ; ollama.com/library/gemma3 ; aclanthology.org/2026.aacl-short.1.pdf (Table 4) |
| Gemma 3 1B | 1B | 32K | Gemma Terms of Use (custom, not OSI) | 806 MB (ollama q4); 2,125 MB runtime footprint | ai.google.dev/gemma/docs/get_started ; ollama.com/library/gemma3 ; aclanthology.org/2026.aacl-short.1.pdf |
| Gemma 4 E2B | 2.3B effective (5.1B with embeddings) | 128K | Apache 2.0 (first Gemma generation under Apache) | 2.9 GB (Q4_0, Google's table) | deepmind.google/models/gemma/gemma-4 ; ai.google.dev/gemma/docs/core/model_card_4 ; vdf.ai/blog/gemma-4-on-premises-deployment |
| Qwen3 0.6B | 0.6B | 32K | Apache 2.0 | 1,361 MB runtime at 16-bit (weights 1,137 MB); 4-bit weight roughly one quarter of that (estimate, flagged) | huggingface.co/Qwen/Qwen3-0.6B (LICENSE: apache-2.0) ; aclanthology.org/2026.aacl-short.1.pdf (Table 4) |
| Qwen3 1.7B | 1.7B | 32K | Apache 2.0 | ~1.1 to 1.2 GB at 4-bit (estimate, flagged) | huggingface.co/Qwen/Qwen3-1.7B (LICENSE: apache-2.0) |
| SmolLM3 3B | 3B | 128K (YaRN) | Apache 2.0 | 1.92 GB (Q4_K_M GGUF) | huggingface.co/HuggingFaceTB/SmolLM3-3B ; huggingface.co/ggml-org/SmolLM3-3B-GGUF |
| Phi-4-mini | 3.8B | 128K | MIT | ~2.5 GB (Q4_K_M, secondary source) | huggingface.co/microsoft/Phi-4-mini-instruct (LICENSE: MIT) |
| Llama 3.2 1B | 1B | 128K | Llama 3.2 Community License (not OSI; 700M MAU clause; naming and attribution; EU multimodal carve-out) | not measured here | llama.meta.com/llama3_2/license ; github.com/meta-llama/llama-models/blob/main/models/llama3_2/LICENSE |
| LFM2 350M / 700M | 350M / 700M | 32K | LFM Open License v1.0 (Apache 2.0 based, with a USD 10M revenue threshold on commercial use) | ~0.3 to 0.5 GB at 4-bit (estimate, flagged) | huggingface.co/LiquidAI/LFM2-1.2B (model table) ; docs.liquid.ai/lfm/help/model-license |

Language coverage, per each vendor's model card: Gemma 3 claims 140+ languages; Qwen3 claims 100+; SmolLM3 is natively strong in 6 (en, fr, es, de, it, pt); Phi-4-mini lists about 23; Llama 3.2 lists 8. For a US-first app this matters little; for future localization it favors Gemma/Qwen.

License reading for an AGPL, F-Droid-distributed app (confidence High, from the license texts):

- **Clean (Apache 2.0 / MIT):** Qwen3 (all sizes), SmolLM3, Phi-4-mini, and Gemma 4. Apache 2.0 and MIT impose no use-restriction flow-down and no naming or badge duties beyond standard notices.
- **Not clean for F-Droid:** Gemma 3 (270M and 1B) ships under the Gemma Terms of Use, which Google's own terms page states covers only the models in its appendix, while Gemma 4 was moved to Apache 2.0 (ai.google.dev/gemma/terms; ai.google.dev/gemma/docs/core/model_card_4). The Gemma terms carry a Prohibited Use Policy incorporated by reference (see § 4 for why that matters specifically here). Llama 3.2 carries naming, attribution, and threshold clauses that fail the Open Source Definition (OSI said so for Llama 3.x in February 2025). LFM2 carries a revenue-threshold field-of-use restriction.
- **Practical consequence:** the two models best fitted to the ADR-0008 hardware floor (Gemma 3 270M and 1B) are precisely the two with the least clean license. The cleanest licenses (Qwen3 0.6B, SmolLM3) start at sizes that strain the floor.

Realistic quality for constrained veterinary QA (confidence High that the small size is a hard knowledge limit; Medium on the exact numbers for any specific model):

- Microsoft's own Phi-4-mini model card states small models "simply do not have the capacity to store too much factual knowledge, therefore, users may experience factual incorrectness" (huggingface.co/microsoft/Phi-4-mini-instruct). That is the vendor speaking about a 3.8B model; a 0.6B model is strictly weaker on stored facts.
- Liquid AI's LFM2 model card says of its own on-device family: "we do not recommend using them for tasks that are knowledge-intensive" (huggingface.co/LiquidAI/LFM2-1.2B).
- Google positions Gemma 3 270M for "a high-volume, well-defined task... sentiment analysis, entity extraction, query routing, unstructured to structured text processing", not open-ended knowledge QA (developers.googleblog.com/en/introducing-gemma-3-270m).
- **Missing primary source (flagged Low):** no public benchmark measures any sub-1B model on veterinary medication QA. Every veterinary LLM evaluation found this session uses frontier cloud models (§ 5). The extrapolation "small models are worse" is directionally safe but unquantified for this exact task.

## 3. On-device runtimes

| Runtime | License / state (late 2026) | Android integration | Offline? | Verified source |
|---|---|---|---|---|
| MediaPipe LLM Inference (Google AI Edge) | Maintenance-only mode; Google directs new work to LiteRT-LM | Maven AAR (`com.google.mediapipe:tasks-genai:0.10.27`); easiest integration; CPU + OpenGL ES GPU; "optimized for high-end Android devices, such as Pixel 8 and Samsung S23 or later, and does not reliably support device emulators" | Yes; inference reads a local `.task`/`.litertlm` file | developers.google.com/edge/mediapipe/solutions/genai/llm_inference (and /android) |
| LiteRT-LM | Apache 2.0; v0.18.1 (Oct 2026); Google's active runtime | Kotlin API; GPU and NPU acceleration; `.litertlm` format | Yes | ai.google.dev/edge (LiteRT-LM docs); github.com/google-ai-edge/LiteRT-LM |
| llama.cpp | MIT; most active project | No official AAR; JNI wrapper required; GGUF format; CPU NEON path reliable; Vulkan on Adreno reported 15-16x slower than CPU in one documented case; ~4 MB runtime binary | Yes | github.com/ggml-org/llama.cpp ; community comparison reports (flagged Medium) |
| ONNX Runtime GenAI | MIT; v0.17.0 | No Java AAR for the genai layer; ONNX export needed; many-file model packages; "stayed experiments for me" in one practitioner write-up | Yes | github.com/microsoft/onnxruntime-genai |
| MLC-LLM | Apache 2.0 | Hardest toolchain (TVM compile per target); thermal throttling ended runs after 6 inferences on a Galaxy S24 Ultra in one documented test | Yes | llm.mlc.ai/docs/deploy/android.html ; community PoC report (flagged Medium) |

Offline confirmation (confidence High): every runtime above loads model weights from local storage and runs inference with no network. The ADR-0003 local-only posture is technically satisfiable. The real no-network risk is the dependency graph: prebuilt native AARs with Google-only transitive dependencies would trip the `scripts/test_no_network.sh` fitness function and F-Droid's from-source-build expectations (a practical F-Droid packaging analysis of exactly this stack flags LiteRT-LM's Bazel-built AAR as "the largest blocker" and onnxruntime's prebuilt `.so` files as needing source builds; github.com/dineshsoudagar/local-llms-on-android/blob/main/docs/store/FDROID.md, flagged Medium as it is a planning document, not F-Droid policy).

RAM floor evidence (confidence High for the measurements, from aclanthology.org/2026.aacl-short.1.pdf and one practitioner PoC):

- Gemma 3 1B int4 on a Pixel 7a via MediaPipe: ~1.9 GB RAM in use, cold start 3.2 to 4.0 s, and the PoC's own conclusion on 1B quality for its task was NO-GO (repetition loops, fabricated data, and no repetition-penalty control in MediaPipe).
- Gemma 3 270M total footprint 721 MB (weights 511 MB at 16-bit plus KV cache 192 MB); Qwen3 0.6B totals 1,361 MB at 16-bit.
- ADR-0008's floor device is 2 GB RAM (Android Go) or 3 GB, with a per-app heap budget of ~96 to 192 MB (native model memory sits outside the Java heap, but the OS low-memory killer does not care). A 1.9 GB resident model on a 2 GB device is not viable. A 721 MB resident model is marginal on 3 GB and hostile on 2 GB. MediaPipe's own docs say its LLM path is built for Pixel 8 / S23 class, which is two tiers above the ADR-0008 target.

## 4. Training-data licensing reality (the critical section)

### 4.1 Merck Vet Manual (MSD): free reading is not a training license

Verified directly from Merck's own pages (confidence High):

- FAQ: "Since the website is free, can I do whatever I want with the content? No. You may share content or post links to content in The Vet Manual. You should assume that everything you see or read on this website is copyrighted and you may not distribute, modify, transmit, reuse, repost, or use the content of the site for public or commercial purposes, including the text, images, audio, or video, without written permission." (merckvetmanual.com/resourcespages/faqs)
- A permissions page exists for reuse requests (merckvetmanual.com/resourcespages/permissions; contact merckmanualpermissions@merck.com). No blanket training license is offered anywhere on the site.
- The disclaimer is AI-aware: it names "ARTIFICIAL INTELLIGENCE ('AI') FEATURES" as part of the covered content, and warns that "Use of AI... to summarize portions of or all the Content or generate any additional derivative content using portions of the Content may also introduce errors" (merckvetmanual.com/en-us/resourcespages/disclaimer). Merck itself ships an AI-enhanced search; that Merck may use its own content with its own AI says nothing about a third party's right to do so.
- Nothing in ingesting the text into a local training pipeline is authorized by "free web reading". Bulk download for training is reuse of the content without written permission under the site's own terms.

### 4.2 Major veterinary textbooks: commercial copyright

Plumb's Veterinary Drug Handbook, Ettinger's Textbook of Veterinary Internal Medicine, and the BSAVA Guide are commercial works held by their publishers and professional associations, sold or gated behind membership. None publishes a license permitting ingestion into a training pipeline (confidence High that no such license exists publicly; confidence Medium on publisher-by-publisher details, which were not individually verified this session and are flagged as missing primary sources if this path is ever pursued).

### 4.3 Professional associations

Verified from the associations' own terms (confidence High):

- AAHA: "All AAHA Guidelines are AAHA copyrighted and are the sole opinion of AAHA and its respective Guidelines' taskforces" (aaha.org/resources/what-are-aaha-guidelines).
- AVMA website terms: a "personal, revocable, limited" license conditioned on "solely for your noncommercial personal use", with derivative works, copying, distribution, and "incorporat[ion] into any information retrieval system" expressly forbidden (avma.org/terms-use).
- AVMA journals: non-open-access JAVMA articles are copyrighted, permission required; AJVR is gold open access under CC BY-NC since June 2022; pre-2022 archival AJVR is all-rights-reserved (avmajournals.avma.org/page/AVMA-journals-reprints-and-permissions).

One discrepancy to record: ADR-0020 § B line 61 describes the AAHA and AAFP vaccination guidelines as "public-domain professional guidelines published by the respective associations". AAHA's own copyright statement contradicts "public-domain". The accepted ADR's design survives anyway, because the rule-pack approach cites guideline facts and ships short operator-curated entries with provenance, rather than reproducing guideline text wholesale. Flagged for the F5b curation session: verify each citation's terms at curation time, and do not copy guideline prose into packs.

### 4.4 What is actually usable

- **US government works (17 U.S.C. § 105: no domestic copyright in works of the US government).** FDA CVM's own educational pages (for example its animal-health literacy and pet-food pages), CVM guidance documents, the Green Book of approved animal drugs, USDA APHIS pet-health and disease pages, and CDC zoonotic-disease pages (Healthy Pets, Healthy People) are public domain in the US. Cite: law.cornell.edu/uscode/text/17/105; fda.gov/about-fda/fda-organization/center-veterinary-medicine (confidence High for agency-authored web content). Two caveats: works authored by contractors can carry copyright; and the approved drug-label corpus is sponsor-submitted regulatory text whose copyright posture is murkier (confidence Medium; treat labels as usable facts-with-attribution, not as bulk-copiable text).
- **Openly licensed veterinary OER** (verified through university library guides and the books' own license statements): Veterinary Preventive Medicine (UMN, Root Kustritz, CC BY-NC 4.0, open.lib.umn.edu/vetprevmed); eClinPath (Cornell, CC BY-NC-SA 4.0); Anatomy and Physiology of Animals (CC BY-NC-SA); Veterinary Histology (CC BY-NC); Clinical Veterinary Diagnostic Laboratory (CC BY-NC); Veterinary Epidemiology (CC BY-NC-ND, Virginia Tech digitization). Source index: guides.lib.vt.edu/oer/vetmed and guides.lib.vt.edu/vetmed/OpenTexts.
- **NC and ND analysis** (confidence High on what the licenses say; the shipped-weights question is a legal judgment, flagged Medium): NC (noncommercial) permits use that is not "primarily intended for or directed towards commercial advantage". A free F-Droid app is arguably noncommercial, but NC on content that ends up inside shipped model weights is a field-of-use restriction that conflicts with AGPL distribution freedoms and would need an operator ruling plus a legal read. ND (no derivatives) almost certainly bars producing training text from the work. Practical posture: build from public domain and CC BY / CC BY-SA; treat CC BY-NC as operator-ruling-required; treat ND as unusable.
- **Land-grant extension publications:** the Virginia Tech OER guide's own framing is "free online (mostly not openly licensed)". Free to read is not free to reuse. Do not assume extension content is usable without checking each publication's statement.

### 4.5 Synthetic data distilled from copyrighted sources: the risk analysis

Three distinct risks stack (confidence High that each risk exists as described; whether they combine to liability is a legal question, flagged for counsel):

1. **Training-side copyright.** The US Copyright Office's Part 3 report (pre-publication, May 2025) concludes training on copyrighted works can be prima facie infringement, that fair use is case-by-case, that "the use of entire works appears to be practically necessary for some forms of training" but "ordinarily weighs against fair use", that "unlawful access, such as pirating works or circumventing paywalls, will weigh strongly against a finding of fair use", and that "where licensing options exist or are likely to be feasible, this consideration will disfavor fair use" (copyright.gov/ai; analysis at mayerbrown.com/en/insights/publications/2025/05). The one direct federal holding this session's search surfaced, Bartz v. Anthropic, 787 F. Supp. 3d 1007 (N.D. Cal. 2025), held training on legally acquired copies was fair use while training on pirated copies was not (copyright.gov/fair-use/summaries/Bartz-v-Anthropic-PBC-787-F-Supp-3d-1007-ND-Cal-2025.pdf). Scraping Merck's site after its terms forbid reuse is a fair-use posture closer to the unlawful-access branch than to the licensed-market branch.
2. **Teacher-model terms.** If a Gemma-family model is the teacher that paraphrases Merck text into QA pairs, the Gemma Prohibited Use Policy binds the operator: it forbids using Gemma or its derivatives to "generate any content... that infringes... rights in copyrighted content" (clause 1), forbids "engagement in the illegal or unlicensed practice of any vocation... including... medical" (clause 2.2) and "misleading claims of expertise or capability made particularly in sensitive areas (e.g. health...)" and "unauthorized or unlicensed practice of any profession including... medical/health" (clauses 3.3 and 3.6) (ai.google.dev/gemma/prohibited_use_policy, fetched this session). A Gemma-distilled veterinary-advice model sits in visible tension with at least clauses 1, 2.2, 3.3, and 3.6. Qwen3 and other Apache/MIT teachers carry no output-use restrictions (Apache 2.0 grants with no such field limits), so the teacher-terms risk is specific to Gemma and Llama teachers, not to open-license teachers.
3. **Output-side memorization.** The USCO report stresses "effective safeguards to avoid 'memorized' works and prevent infringing outputs" (copyright.gov/ai). A small model finetuned heavily on a narrow corpus is the memorization-heavy regime; verbatim emission of Merck text from the shipped weights is the nightmare case, and § 5's literature shows plausibility and correctness are unrelated, so the operator cannot eyeball it away.

### 4.6 Conclusion on legally clean paths

For an AGPL app distributed through F-Droid, the only training-data paths that are clean today:

1. US government public-domain sources (FDA CVM, USDA APHIS, CDC), agency-authored pages preferred.
2. CC BY / CC BY-SA veterinary content, of which the verified set is small and mostly non-clinical ( preventive medicine, diagnostic lab technique, anatomy).
3. Fully synthetic QA pairs authored or verified by qualified humans (operator plus veterinary reviewers), citing public sources, with no bulk ingestion of copyrighted text.

Distilling from Merck, Plumb's, Ettinger, or BSAVA is not clean under the sources' current terms, fails the USCO lawful-access guidance, and (for Gemma/Llama teachers) fails teacher-model terms. This is the single most load-bearing finding of the research: the directive's premise ("train on Merck Vet Manual and official books") is unavailable as stated, independent of every other blocker.

## 5. Fine-tuning and evaluation

### 5.1 Method

QLoRA (Dettmers et al., 2023, arXiv:2305.14314) finetunes a 4-bit quantized base model with low-rank adapters; sub-1B models finetune on a single consumer GPU or a free Colab tier. Training happens offline on a PC, never on-device; the merged weights ship. MediaPipe supports loading LoRA adapters on the GPU backend (developers.google.com/edge/mediapipe/solutions/genai/llm_inference), but shipping merged weights is simpler and avoids the LoRA-on-GPU-only constraint. No method uncertainty here; the uncertainty is data (§ 4) and quality (§ 5.3).

### 5.2 What a toebeans evaluation suite would measure

1. **Exact-dose recall:** for N common US drugs, ask "what is the labeled dose of X for dogs?" and compare against public-domain FDA label text. Wrong numeric answers are hard failures.
2. **Toxin-escalation:** "my dog ate X" for a known toxin list. Correct behavior is immediate escalation ("call a poison hotline / emergency vet now"), never a home-remedy dose. Measure failure-to-escalate rate.
3. **Refusal correctness:** diagnosis requests, dose-change requests, drug-interaction questions must produce the "ask your veterinarian" refusal, not an answer. Measure both refusal rate and wrong-refusal rate.
4. **Consistency:** same prompt, k samples; measure answer variance (DoseBench's protocol; arXiv:2606.04262).
5. **Adversarial fabrication:** embed a false detail in the prompt and measure whether the model elaborates on it (the Nature Communications Medicine protocol; DOI s43856-025-01021-3).

### 5.3 What the literature says about small-model reliability

All of the following are verified citations; note that every veterinary and medication study found uses frontier cloud models or 7B-class models, never sub-1B (flagged as the key missing primary source):

- A blinded multi-expert veterinary study of 10 LLMs (6 generalist, 4 veterinary-specialized) on 10 real cases found performance "equivalent to that of a final-year student", generalists beating specialists, and "hallucinations were consistently identified"; the authors' stated worry is that prose quality exceeds clinical substance and "may foster overconfidence in users", concluding these tools "call for rigorous supervision" (ScienceDirect S2214567226000633, Comparative evaluation of generalist and veterinary specialized LLMs, 2026).
- A JAVMA diagnostic-accuracy study of feline metacarpal/metatarsal fracture triage found accuracy from 84.9% (best) down to 32.9%, with two of five frontier models at 0% sensitivity, and "a systematic risk of undertriage" (avmajournals.avma.org, javma.26.03.0227).
- On veterinary board-style questions, the best frontier models score ~90% and weaker ones ~65% (Frontiers in Veterinary Science, 10.3389/fvets.2025.1616566).
- Hallucination rates in medical QA benchmarks run "from 15% to over 40% depending on task complexity and model size", and "procedural knowledge and dosage calculations show particularly high error rates" (systematic review, itm-conferences.org/articles/itmconf/pdf/2026/04/itmconf_atcids2026_03005.pdf).
- The headline failure mode is measurable: a 70B model answering questions over provided medical-textbook passages hallucinated in 19.7% of answers while clinicians rated 98.8% of its answers maximally plausible; "the generated content almost always seems to be plausible but has an almost one in five chance of being hallucinated" (arXiv:2603.09986, Quantifying Hallucinations in Large Language Models on Medical Textbooks).
- A distilled reasoning model performed worst of six tested models under adversarial hallucination attacks, with overall rates of 50-82% and prompt mitigation only halving them (Nature Communications Medicine, s43856-025-01021-3). Distillation from a large teacher into a small student did not preserve safety behavior.
- On OTC dosing specifically, DoseBench (81 curated acetaminophen/ibuprofen scenarios, 1,620 responses) found "models frequently struggle with rolling-window reasoning... stable or confident-looking responses can still violate dosing constraints" (arXiv:2606.04262).
- Small open medical models finetuned for the domain score far below frontier models on medication benchmarks: the ACL 2025 ChiDrug benchmark shows the small open medical models it tests averaging 23-41% across medication tasks versus ~70% for GPT-4o (aclanthology.org/2025.acl-short.91.pdf). Domain finetuning at small scale does not close the gap.

**The named failure mode, stated plainly:** a confident wrong answer about a toxin or a dose. The literature says this is the worst-measured class even at frontier scale, that plausibility does not predict correctness, and that smaller and distilled models do worse. AGENTS.md § Operator wisdom sets the stake: the reminder-firing path is medication-critical and "a bug here can affect a real animal's health". The evaluation bar for any shipped model would be a near-zero wrong-answer rate on toxin and dose items plus near-perfect escalation and refusal. Nothing published demonstrates that bar for any model, let alone a sub-1B model on budget Android.

## 6. Product constraints

- **APK size:** ADR-0008 sets a cumulative APK weight target of 8 MB and names "embedding large binary assets" as an anti-pattern. A 4-bit Gemma 3 1B is 806 MB; even the 270M is 292 MB, roughly 36x the whole-app target. On ADR-0008 floor devices (32 GB storage, 1-4 GB free) an 800 MB model is a fifth to most of the user's free space.
- **RAM/thermal:** § 3 showed the 1B needs ~1.9 GB resident; the ADR-0008 floor is 2 GB total. MLC-LLM thermally ended runs after 6 inferences on a Galaxy S24 Ultra in one documented PoC; budget SoCs (Helio G37 class) have less thermal headroom, not more.
- **ADR-0008 explicitly lists "ML-on-device" among the features that are "off the table or require explicit ADR override."** An on-device model therefore needs an ADR-0008 override regardless of the ADR-0019 question.
- **ADR-0003 (local-first, no cloud)** forbids runtime model download. Google's recommended deployment path for MediaPipe models is to host the model on a server and download at runtime ("The model is too large to be bundled in an APK", developers.google.com/edge/mediapipe/solutions/genai/llm_inference/android), and Google's "Play for On-Device AI" beta manages model downloads over the network (android-developers.googleblog.com, Google I/O 2025 post). Both are excluded for toebeans: the model must ship inside the APK/AAB or through a signed offline channel.
- **F-Droid (verified current state, confidence High):** F-Droid has no formal policy on on-device AI as of September 2026; the community thread "Does F-Droid have a formal policy on libre AI?" (forum.f-droid.org/t/does-f-droid-have-a-formal-policy-on-libre-ai) shows an active moderator position that "If the AI model is not under a free license then it's covered by NonFreeAsset" (linsui), which would flag Gemma-terms and Llama-license models as anti-features; the project is considering a Debian-style AI policy (September 2026 reports). Sibling store IzzyOnDroid currently does not allow AI apps (forum citation of its inclusion policy). Practical packaging notes from a published F-Droid targeting analysis: prebuilt native AARs must be rebuilt from source, ABI splits are expected for large native libraries, and models bundled in the APK are reviewed like any other asset (github.com/dineshsoudagar/local-llms-on-android/blob/main/docs/store/FDROID.md, flagged Medium).

Net: bundling is physically possible only at the 270M class, with an Apache/MIT-licensed model (Qwen3 0.6B is the closest fit), against an explicit ADR-0008 override, with from-source runtime builds for F-Droid. Each of those is a real cost; together they describe a fragile build.

## 7. Do "users should check with a vet" disclaimers fix ADR-0019 compliance?

**Steelman for amending (the strongest case FOR the on-device model):**

- A fixed, versioned, on-device artifact is more auditable than any cloud API: no vendor drift, no network, no silent model swap. ADR-0019's auditability complaint was written against hosted generative systems; weights pinned in-repo with a published eval report are a different object.
- The eval-suite-as-merge-gate (§ 5.2) is a genuine reproducibility mechanism: the artifact that ships is tested, versioned, and its metrics are public. Nothing in the consumer LLM ecosystem today offers more.
- Framing can be enforced mechanically: a fixed "Draft suggestion. Verify with your veterinarian." prefix, a read-only surface, and no path from a suggestion into a reminder or dose field means the model never touches the medication-critical path.
- ADR-0019's own revisit condition invites exactly this inquiry, and the operator directive is a legitimate exercise of it.

**Steelman for keeping the ban (the strongest case AGAINST):**

- ADR-0019 § Rejected alternatives already considered "LLM + vet review" and rejected it because "the LLM remains the source, the vet is a rubber stamp, and the user cannot tell the difference." An eval suite has the same shape: it samples the model's behavior; it does not review the output the next user will receive. A published 5% hallucination rate is a declared defect rate for medical content, and § 5 shows the defect class with the highest rate is dosages.
- The delivery constraint requires "the same input always produces the same output". Sampled generation fails this by construction; temperature 0 makes it approximate, not guaranteed.
- The framing constraint forbids "anything that implies the app is the source of medical judgment". A "draft suggestion" from the app is still the app suggesting; the disclaimer does not relocate authorship, it only relabels it.
- The source constraint names pathways, not quality tiers. "Trained on Merck" is not one of the three pathways, and § 4 showed it is not even available legally.
- The revisit condition requires "a production-safe LLM auditability framework... the research community considers usable for medication-adjacent advice". The 2026 literature's own conclusion is the opposite of that: it prescribes clinician oversight ("rigorous supervision"), which a consumer app cannot supply at the moment of use.

**What evidence WOULD change the posture (the concrete revisit bar):** a published, independently replicated framework showing, on medication-adjacent consumer QA, (a) a wrong-answer rate on toxin and dose items indistinguishable from zero, (b) bit-identical outputs for identical inputs under a deterministic decoding contract, and (c) refusal behavior that survives adversarial fabrication tests. Additionally, a training-data license from a rights holder at terms compatible with AGPL redistribution of the finetuned weights. Absent those, the ban stands; the static-pack path in § 8 delivers what the goal actually needs.

## 8. Alternatives that reach the operator's goal without the amendment

### 8.1 Static vet-curated suggestion packs (ADR-0019 pathway 3; the ADR-0020 precedent)

The rule-pack machinery already exists: `Vaccine` reference rows plus `(species, age_range, regulatory_region) -> reminder` packs with provenance metadata, shipped in-app, updated by app release (ADR-0020 § B and § C). The same shape carries "qualified suggestions": per-condition monitoring overlays ("for pets with diagnosed CKD, [board] recommends weight check monthly, bloodwork quarterly"), "questions to ask your vet" checklists, and toxin emergency cards ("if your pet ate X: call [poison line] now"), each entry carrying `curator` and `source_citations`. Gated on Followup F2 (the advisory-board ADR), with operator curation as the interim path exactly as ADR-0020 § B already established for vaccines. Combined with the emergency-line links shipping separately, this covers the operator's intent: trustworthy, qualified, check-with-your-vet guidance with zero inference.

### 8.2 RAG with verbatim quotes and citations: why it still fails the current ADR

Mechanism: bundle a licensed public-domain corpus in the APK; at question time, retrieve passages and have the model compose an answer out of quoted spans with citations. Why ADR-0019 as written still forbids it:

1. **Source constraint:** the answer is model-assembled; it is not owner-entered, vet-record-imported, or board-curated. The model chooses which quotes to include and what connective text to write.
2. **Delivery constraint:** "the same input always produces the same output" fails. Retrieval ranking is an inference, and sampled quote-selection varies run to run. Constrained decoding narrows but does not eliminate this.
3. **Framing constraint:** presenting model-selected quotes implies the app judged their relevance to this pet's situation, which is medical judgment by proxy.
4. **Refusal carve-out:** this feature answers "is this dose safe?"-class questions with generated text, which is named as forbidden.

A side note from the copyright side, for symmetry: the USCO report observes that RAG outputs that summarize retrieved works rather than linking to them are less likely to be fair use (mayerbrown.com analysis of Part 3, citing the report). RAG is the first candidate to reconsider if Option B is selected, because verbatim-quote-only output plus deterministic post-verification is the closest any LLM architecture gets to rule-based delivery. That is a change-of-posture conversation, not a current option.

### 8.3 Hybrid "model drafts offline, humans curate, ship as static pack"

An LLM (any capable cloud or desktop model) drafts candidate pack entries in the operator's build workflow; a human curator verifies every line against its cited source and signs the pack; the app ships only the static, verified pack. The LLM never runs on-device, never runs at runtime, and is not part of the shipped artifact. Auditability is preserved because the shipped object is static text with provenance, exactly the ADR-0020 F5b known-answer-test pattern. Honest caveat: ADR-0019 does not currently define where "LLM used in the curation toolchain, human-verifies every line" lands; a strict reading ("any LLM touch = LLM-derived content") bans even this; a purposive reading (the ban targets untraceable content in the shipped app) permits it. This line is precisely what the operator should state explicitly, and ADR-0021 proposes wording for it in both options.

## 9. Recommendation

**Verdict: the on-device model path is not ready (confidence High).** Ranked reasons: (1) the premise's training corpus (Merck, textbooks) is legally unavailable as stated, and the legally clean corpus is too thin for clinical depth; (2) the ADR-0008 hardware floor excludes every candidate except a 270M-class model, and the best-fitting models by license (Qwen3 0.6B) strain that floor; (3) the reliability literature measures the exact feared failure mode as the worst class at frontier scale, worsens with smaller and distilled models, and has no sub-1B veterinary evidence at all; (4) F-Droid packaging adds an anti-feature-flag and from-source-build burden; (5) the posture ban stands unless the operator affirmatively amends it, and the revisit condition's evidence bar is not met by anything published.

**Recommended path:** Option A in ADR-0021. Ship static curated suggestion packs (§ 8.1) plus the emergency-line links already in flight. If the operator wants the hybrid drafting aid (§ 8.3), adopt its clarifying sentence in the contract files via the ADR-0021 followup so the toolchain line is explicit.

**Cheapest falsifying experiment for the operator (spec, about one day, no app code, no posture change):**

1. Build a 200-question evaluation set from public-domain sources only: 100 exact-dose questions keyed to FDA CVM label text (agency-authored pages preferred), 60 toxin-escalation questions ("my dog ate chocolate/grapes/ibuprofen/xylitol/..."), 40 out-of-scope questions requiring refusal.
2. Run Qwen3-0.6B-Instruct (Apache 2.0) and Gemma 3 270M (flag its terms) zero-shot, 3 seeds each, on a laptop; then QLoRA-finetune Qwen3-0.6B on ~5k synthetic pairs authored from the public-domain sources and re-run.
3. Pass bar for taking the amendment seriously: 100% toxin-escalation correctness, zero wrong doses in any top-1 answer, refusal correctness at or above 95% on all runs, for both the base and finetuned models.
4. Expected outcome (prediction, falsifiable): the finetuned 0.6B model passes refusal and escalation but fails exact-dose recall; the base 0.6B fails at least two of the three. If the prediction is wrong and the bar is met, that is the first piece of positive evidence for Option B, and the operator should then commission the licensing and hardware studies. If the prediction holds, the premise of the directive is falsified for the cost of one weekend and the static-pack path stands on evidence.

## Uncertainty register

- No sub-1B model has any published veterinary or medication-QA evaluation (missing primary source; the § 5 extrapolation is inference from 7B-class and frontier evidence).
- Qwen3 4-bit artifact sizes and Llama 3.2 1B quantized sizes were not measured this session; § 2 marks those cells as estimates.
- Publisher-level copyright details for Plumb's, Ettinger, and BSAVA were not individually verified; the § 4.2 claim is about the category, and a per-title check is required before any use.
- The copyright status of sponsor-submitted FDA drug-label text is unresolved in this document; agency-authored pages are the safe corpus.
- Whether NC-licensed content inside shipped model weights is compatible with AGPL distribution is a legal judgment, not resolved here; treated as operator-ruling-required.
- The F-Droid packaging analysis cited is a third-party planning document, not F-Droid policy; F-Droid's own policy page should be re-checked at packaging time.
- The Anthropic fair-use holding is a single district-court decision; it is cited as the current state of one data point, not as settled law.
