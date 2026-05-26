# Third-Party Notices

This project is prepared for public GitHub distribution under the Apache
License 2.0. Bundled third-party assets and reference material are documented
here so redistributors can audit source and license compatibility.

## Bundled Assets

### Glide English Word List

- File: `app/src/main/assets/glide_words_en.txt`
- Notice file: `app/src/main/assets/glide_words_en_NOTICE.txt`
- Source: https://github.com/words/subtlex-word-frequencies
- License: ISC
- Use: filtered to lowercase alphabetic words from 2 to 24 characters, keeping
  the first 12,000 unique entries in spoken-English frequency order.

## Reference Projects Not Vendored

The following projects informed planning only. Their source code is not copied,
vendored, or linked in this repository in the current implementation.

- FlorisBoard NLP: https://github.com/florisboard/nlp
- AOSP LatinIME: https://android.googlesource.com/platform/packages/inputmethods/LatinIME
- AnySoftKeyboard: https://github.com/AnySoftKeyboard/AnySoftKeyboard
- Presage: https://presage.sourceforge.io/

If code, models, dictionaries, or generated artifacts from any reference
project are added later, update this file before publishing a release.
