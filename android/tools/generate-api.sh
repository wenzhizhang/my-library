#!/usr/bin/env bash
# Regenerates the OpenAPI client into app/src/main/java/top/dingfengbo/mylibrary/api.
#
# The generated code is committed: it is not produced during a normal build, so the build needs
# no JVM code generator and can be reproduced offline. Re-run this script whenever
# backend/spec/openapi.yaml changes, then review the diff like any other source change.
#
# Never hand-edit anything under top/dingfengbo/mylibrary/api — the next run overwrites it.
set -euo pipefail

GENERATOR_VERSION="7.25.0"
GENERATOR_JAR="${GENERATOR_JAR:-$HOME/toolchain/openapi-generator-cli.jar}"

ANDROID_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SPEC="${SPEC:-$ANDROID_DIR/../backend/spec/openapi.yaml}"
OUT_DIR="$(mktemp -d)"
trap 'rm -rf "$OUT_DIR"' EXIT

if [[ ! -f "$GENERATOR_JAR" ]]; then
  echo "Generator jar not found at $GENERATOR_JAR" >&2
  echo "Fetch it with:" >&2
  echo "  curl -L -o $GENERATOR_JAR https://repo1.maven.org/maven2/org/openapitools/openapi-generator-cli/$GENERATOR_VERSION/openapi-generator-cli-$GENERATOR_VERSION.jar" >&2
  exit 1
fi

java -jar "$GENERATOR_JAR" generate \
  -g kotlin \
  -i "$SPEC" \
  -o "$OUT_DIR" \
  -p library=jvm-retrofit2 \
  -p serializationLibrary=kotlinx_serialization \
  -p dateLibrary=string \
  -p useCoroutines=true \
  -p useResponseAsReturnType=false \
  -p packageName=top.dingfengbo.mylibrary.api \
  -p sourceFolder=src/main/kotlin \
  -p omitGradleWrapper=true \
  -p enumPropertyNaming=UPPERCASE \
  --global-property=apis,models,supportingFiles,apiDocs=false,modelDocs=false,apiTests=false,modelTests=false

API_DIR="$ANDROID_DIR/app/src/main/java/top/dingfengbo/mylibrary/api"
rm -rf "$API_DIR"
mkdir -p "$API_DIR"
cp -r "$OUT_DIR/src/main/kotlin/top/dingfengbo/mylibrary/api/." "$API_DIR/"

# Generator quirk: query-parameter enums are emitted as
#     enum class SortByApiBooksGet(val value: kotlin.String) { @SerialName("title") TITLE("title") }
# Retrofit sends a query enum via toString(), so the wire value became "TITLE" and the backend fell
# back to its default ordering (the spec says unknown sort_by values order by id). Give every such
# enum a toString() that returns the API value, which is what the `value` property is for.
python3 - "$API_DIR" <<'PATCH'
import re, sys
from pathlib import Path

root = Path(sys.argv[1])
pattern = re.compile(r'(enum class \w+\(val value: kotlin\.String\) \{)([^{}]*)(\})', re.S)
patched = 0
for file in root.rglob("*.kt"):
    text = file.read_text(encoding="utf-8")
    new_text, count = pattern.subn(
        r'\1\2;\n        override fun toString(): String = value\n    \3', text
    )
    if count:
        file.write_text(new_text, encoding="utf-8")
        patched += count
print(f"Patched {patched} query-parameter enum(s) to serialise their API value")
PATCH

echo "Regenerated $(find "$API_DIR" -name '*.kt' | wc -l) files into $API_DIR"
