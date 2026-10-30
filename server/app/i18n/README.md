# Server extraction i18n

Per-language resources for the AI recipe extraction (`app/extraction.py` → `load_i18n`).
One JSON file per content language; `en.json` is the source/fallback.

Each file:

| key | meaning |
|---|---|
| `text_instructions` | how to read the recipe text (qwen2.5vl) — **engineering, review changes carefully** |
| `text_format` | the JSON the text answer must have — the parser relies on it |
| `box_instructions` | how to find the dish photos |
| `box_format` | the JSON + coordinate rules for the photo boxes — the cropper relies on it |
| `duration_hours` / `duration_minutes` | words that mark hours/minutes in prep/cook times |
| `units` | recognized measuring units (kept verbatim; never converted) |
| `category_aliases` | maps language words to the universal category keys (`pasta/meat/…`) |

Loading: `load_i18n("xx")` reads `xx.json`, falling back to `en.json` for unknown languages.
`units`, `duration_*` and `category_aliases` are **unioned with English**, so universal tokens
(g, ml, "min", the category keys) always match. The prompt parts use the language's own text, else
English. Each prompt is `<instructions>\n\n<format>`; a server owner can replace the *instructions*
per language in the admin console (**Prompts**), while the format is always appended as built here.

## Adding a language (e.g. Japanese)

Copy `en.json` to `ja.json` and translate the values. No code change needed — the app sends the
household content language with each scan and the loader picks the matching file.

## Weblate

Add a **second Weblate component** for the server, format **JSON file**, file mask
`server/app/i18n/*.json`, source language file `server/app/i18n/en.json`. Translators then add new
languages here just like the Android strings. Note: `text_instructions` and the `*_format` keys are
engineering text — gate them behind review.
