"""Render the in-app privacy-policy asset as a standalone page for public hosting."""

from html import escape
from pathlib import Path
import re


ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "app/src/main/assets/privacy_policy.txt"
DESTINATION = ROOT / "docs/privacy-policy.html"


def main() -> None:
    blocks = re.split(r"\n\s*\n", SOURCE.read_text(encoding="utf-8").strip())
    body = []
    for block in blocks:
        if block.startswith("# "):
            tag = "h1" if not body else "h2"
            body.append(f"<{tag}>{escape(block[2:])}</{tag}>")
        else:
            content = escape(block).replace("\n", "<br>\n")
            content = content.replace(
                "injamamul2k@gmail.com",
                '<a href="mailto:injamamul2k@gmail.com">injamamul2k@gmail.com</a>',
            )
            body.append(f"<p>{content}</p>")
    html = """<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="color-scheme" content="light dark">
<title>Miowser privacy policy</title>
<style>
  body { max-width: 46rem; margin: 0 auto; padding: 2rem 1.25rem 4rem;
         font: 1rem/1.7 system-ui, sans-serif; color: #20212b; background: #faf9ff; }
  h1, h2 { line-height: 1.25; color: #262b49; }
  h1 { font-size: 2rem; } h2 { margin-top: 2rem; font-size: 1.25rem; }
  a { color: #354d92; }
  @media (prefers-color-scheme: dark) {
    body { color: #e4e2ef; background: #15151d; }
    h1, h2 { color: #f3f0ff; } a { color: #b5c5ff; }
  }
</style>
</head>
<body>
""" + "\n".join(body) + "\n</body>\n</html>\n"
    DESTINATION.write_text(html, encoding="utf-8")


if __name__ == "__main__":
    main()
