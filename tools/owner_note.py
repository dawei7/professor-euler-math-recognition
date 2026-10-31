"""Finds a canvas note of the Windows app's default profile by its title and cuts its sheet into lines (read only).

Usage: python -I tools/owner_note.py <title> [<out dir>]   (out dir defaults to data/owner/<title>)
"""
import json
import os
import pathlib
import subprocess
import sys

FILES = pathlib.Path(os.environ["LOCALAPPDATA"]) / "ProfessorEuler" / "files"


def main():
    title = sys.argv[1]
    out = pathlib.Path(sys.argv[2] if len(sys.argv) > 2 else f"data/owner/{title}")
    library = json.loads((FILES / "library.json").read_text(encoding="utf-8"))
    found = [d for d in library["documents"] if d.get("title") == title and d.get("kind") == "canvas"]
    if len(found) != 1:
        sys.exit(f"{len(found)} canvas notes are called {title!r}")
    sheet = FILES / "work" / found[0]["id"] / "sheets" / "notes.json"
    if not sheet.is_file():
        sys.exit(f"{title!r} has no sheet on this device yet: {sheet}")
    script = pathlib.Path(__file__).with_name("sheet_lines.py")
    subprocess.run([sys.executable, "-I", str(script), str(sheet), str(out)], check=True)


if __name__ == "__main__":
    main()
