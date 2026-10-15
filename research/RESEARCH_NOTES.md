# Research: Minna no Nihongo vocabulary data (2026-10-14)

Prepared while the 5 design questions are pending — raw word data is needed
regardless of the answers, so this is safe groundwork.

## Source
- Dataset: **MinnaNoDS** (`minna-no-ds.yaml`, ~918KB raw)
  - Original: vitto4/MinnaNoDS ; mirror used: kenkout/minnanods (newest fork)
  - Community-formulated vocabulary list for Minna no Nihongo Shokyu I & II
  - License note in file header: use in conjunction with the books, not as a substitute
  - Local copy: `research/minna-no-ds.yaml`

## Coverage (verified)
- **All 50 lessons present, 2,692 entries total**
- Per-lesson counts: L01 47 · L02 52 · L03 52 · L04 68 · L05 63 · L06 59 · L07 52 ·
  L08 65 · L09 56 · L10 57 · L11 66 · L12 57 · L13 45 · L14 50 · L15 34 · L16 65 ·
  L17 39 · L18 33 · L19 33 · L20 30 · L21 64 · L22 35 · L23 38 · L24 22 · L25 22 ·
  L26 62 · L27 58 · L28 60 · L29 59 · L30 63 · L31 54 · L32 68 · L33 77 · L34 57 ·
  L35 71 · L36 60 · L37 79 · L38 61 · L39 59 · L40 77 · L41 72 · L42 77 · L43 36 ·
  L44 50 · L45 39 · L46 48 · L47 50 · L48 40 · L49 59 · L50 52
- **Correction vs earlier estimate**: the design docs said "~2.156 từ".
  Actual count is **2,692** (the estimate apparently counted main-list words only;
  the dataset also includes expressions/names/counters/suffixes).

## Clean import file
- `research/minna-vocab-50.json` (690KB): one object per word, sorted by (lesson, index):
  `lesson, index, kanji, kana, romaji, pos, kind, meaning_en, meaning_vi (""),
  example_vi (""), editions`
- Fields available: POS tags (n 1317, v5 274, exp 226, adj 182, adv 151, v1 150,
  vs 135, ...; 49 entries lack POS), kind (word/expression/name/counter/
  suffix/prefix/title), English meaning (100% present), edition flags
  ([1,2] = 1769, [1] only = 497, [2] only = 426).
- The dataset does NOT tag lesson sections (chính / tham khảo / cụm từ);
  that tagging will need manual rules or admin tagging at import time.

## Vietnamese meanings + example sentences
- `meaning_vi` / `example_vi` intentionally left EMPTY.
- Plan (unchanged): compile/verify manually per lesson before import —
  no raw machine-translation published.

## Audio (edge-tts) — NOT validated in this sandbox
- `edge-tts` (voice ja-JP-NanamiNeural) was installed and tried with 1 word;
  it **failed**: `ClientConnectorSSLError` to speech.platform.bing.com:443
  (sandbox TLS interception / proxy). It likely works on a normal network
  (user's PC / production build), but that is UNVERIFIED.
- Implication for Q3: pre-generation is still the plan, but the first batch
  should be a small pilot (1 lesson) on a real network before committing
  to all 2,692 files.

## Suggested next steps (once the 5 design answers land)
1. Hand-compile VI meanings for lessons 1–5 as the pilot batch.
2. Pilot edge-tts audio for lesson 1 on a normal network.
3. Import pilot batch → flashcard/quiz flow smoke test.
