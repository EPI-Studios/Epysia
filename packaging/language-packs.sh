#!/usr/bin/env bash
set -euo pipefail

version="${VERSION:?VERSION is required}"
repository="${GITHUB_REPOSITORY:-EPI-Studios/Epysia}"
release_url="https://github.com/$repository/releases/download/v$version"
destination="dist/language-packs.json"
mkdir -p dist

kotlin_pack="dist/epysia-lang-kotlin-$version.jar"
kotlin_runtime="dist/epysia-lang-kotlin-runtime-$version.jar"
python_pack="dist/epysia-lang-python-$version.zip"

cat > "$destination" <<JSON
{
  "formatVersion": 1,
  "packs": [
    {
      "id": "kotlin",
      "name": "Kotlin",
      "description": "Kotlin scripts, with the compiler, highlighting and completion.",
      "version": "$version",
      "asset": "$(basename "$kotlin_pack")",
      "url": "$release_url/$(basename "$kotlin_pack")",
      "sizeBytes": $(stat -c%s "$kotlin_pack"),
      "sha256": "$(sha256sum "$kotlin_pack" | cut -d' ' -f1)",
      "runtimeAsset": "$(basename "$kotlin_runtime")",
      "runtimeUrl": "$release_url/$(basename "$kotlin_runtime")",
      "runtimeSha256": "$(sha256sum "$kotlin_runtime" | cut -d' ' -f1)"
    },
    {
      "id": "python",
      "name": "Python",
      "description": "Python scripts on GraalPy, with highlighting and completion.",
      "version": "$version",
      "asset": "$(basename "$python_pack")",
      "url": "$release_url/$(basename "$python_pack")",
      "sizeBytes": $(stat -c%s "$python_pack"),
      "sha256": "$(sha256sum "$python_pack" | cut -d' ' -f1)"
    }
  ]
}
JSON

echo "wrote $destination"
