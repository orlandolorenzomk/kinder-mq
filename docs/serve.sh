#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PORT=8080

for arg in "$@"; do
  case $arg in
    --port=*)
      PORT="${arg#*=}"
      ;;
    *)
      echo "Unknown argument: $arg"
      echo "Usage: $0 [--port=<port>]"
      exit 1
      ;;
  esac
done

# ── dependency checks ────────────────────────────────────────────────────────

if ! command -v asciidoctor &>/dev/null; then
  echo "Error: asciidoctor not found."
  echo "Install it with:  gem install asciidoctor asciidoctor-diagram"
  exit 1
fi

if ! asciidoctor --list-extensions 2>/dev/null | grep -q diagram && \
   ! gem list 2>/dev/null | grep -q asciidoctor-diagram; then
  echo "Warning: asciidoctor-diagram may not be installed."
  echo "Install it with:  gem install asciidoctor-diagram"
fi

if ! command -v java &>/dev/null; then
  echo "Warning: java not found — PlantUML diagrams may not render."
fi

# ── build ────────────────────────────────────────────────────────────────────

BUILD_DIR="$SCRIPT_DIR/build"
IMAGES_DIR="$BUILD_DIR/images"

mkdir -p "$IMAGES_DIR"

echo "Building documentation..."

asciidoctor \
  -r asciidoctor-diagram \
  -a imagesdir=images \
  -a imagesoutdir="$IMAGES_DIR" \
  -a diagram-cachedir="$BUILD_DIR/.cache" \
  -a source-highlighter=highlight.js \
  -a highlightjs-theme=github \
  -a toc=left \
  -a toclevels=3 \
  -a sectnums \
  -a icons=font \
  -D "$BUILD_DIR" \
  "$SCRIPT_DIR/architecture.adoc"

echo "Build complete → $BUILD_DIR/architecture.html"

# ── serve ────────────────────────────────────────────────────────────────────

echo "Serving at http://localhost:$PORT"
echo "Press Ctrl+C to stop."

if command -v python3 &>/dev/null; then
  python3 -m http.server "$PORT" --directory "$BUILD_DIR"
elif command -v python &>/dev/null; then
  cd "$BUILD_DIR" && python -m SimpleHTTPServer "$PORT"
else
  echo "Error: python3 not found — cannot start HTTP server."
  echo "Open $BUILD_DIR/architecture.html directly in your browser."
  exit 1
fi
