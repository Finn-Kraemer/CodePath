"""Prüft alle Content-Pakete gegen das JSON-Schema (läuft in der CI).

    pip install jsonschema && python content/validate.py

Die vollständige Prüfung samt Probelauf macht der Server (ContentPackagesTest, Editor „Prüfen“).
"""
import hashlib
import json
import re
import sys
from pathlib import Path

from jsonschema import Draft202012Validator

ROOT = Path(__file__).parent
MEDIA_KEY = re.compile(r"(?<![0-9a-f])[0-9a-f]{64}\.(?:png|jpg|gif|webp)")


def load(path):
    return json.loads(path.read_text(encoding="utf-8"))


def main():
    module_schema = Draft202012Validator(load(ROOT / "schema" / "module.schema.json"))
    task_schema = Draft202012Validator(load(ROOT / "schema" / "task.schema.json"))
    errors = []
    modules = sorted(p.parent for p in ROOT.glob("*/module.json"))
    module_slugs = set()
    tasks = 0
    for module_dir in modules:
        module = load(module_dir / "module.json")
        errors += [f"{module_dir.name}/module.json: {e.message}" for e in module_schema.iter_errors(module)]
        if module.get("slug") != module_dir.name:
            errors.append(f"{module_dir.name}/module.json: slug muss dem Ordnernamen entsprechen")
        if module.get("slug") in module_slugs:
            errors.append(f"{module_dir.name}: doppelter Modul-Slug")
        module_slugs.add(module.get("slug"))

        media = {}
        for path in sorted((module_dir / "media").glob("*")) if (module_dir / "media").is_dir() else []:
            if not MEDIA_KEY.fullmatch(path.name):
                errors.append(f"{module_dir.name}/media/{path.name}: Name muss SHA-256 + Endung sein (tools/add_media.py)")
            elif hashlib.sha256(path.read_bytes()).hexdigest() != path.name.split(".")[0]:
                errors.append(f"{module_dir.name}/media/{path.name}: Inhalt passt nicht zum Namen")
            elif path.stat().st_size > 2 * 1024 * 1024:
                errors.append(f"{module_dir.name}/media/{path.name}: größer als 2 MB")
            media[path.name] = False

        slugs = set()
        for path in sorted((module_dir / "tasks").glob("*.json")):
            tasks += 1
            task = load(path)
            where = f"{module_dir.name}/tasks/{path.name}"
            errors += [f"{where}: {'/'.join(map(str, e.absolute_path))}: {e.message}" for e in task_schema.iter_errors(task)]
            if task.get("slug") in slugs:
                errors.append(f"{where}: doppelter Slug {task.get('slug')}")
            slugs.add(task.get("slug"))
            if not path.stem.endswith(task.get("slug", "")):
                errors.append(f"{where}: Dateiname sollte auf den Slug enden")
            for key in MEDIA_KEY.findall(path.read_text(encoding="utf-8")):
                if key not in media:
                    errors.append(f"{where}: Bild {key} fehlt in {module_dir.name}/media/")
                else:
                    media[key] = True
            config = task.get("config", {})
            if task.get("type") in ("FILL_BLANK", "FILL_CODE"):
                gaps = config.get("template", "").count("___")
                if gaps != len(config.get("blanks", [])):
                    errors.append(f"{where}: {gaps} Lücken in der Vorlage, aber {len(config.get('blanks', []))} Lösungen")
            if task.get("type") == "PRACTICE":
                total = sum(c.get("points", 0) for c in config.get("rubric", []))
                if total != task.get("points"):
                    errors.append(f"{where}: Rubrik ergibt {total} Punkte, Aufgabe hat {task.get('points')}")

        errors += [f"{module_dir.name}/media/{k}: wird von keiner Aufgabe verwendet" for k, used in media.items() if not used]

    for e in errors:
        print("FEHLER", e)
    print(f"{len(modules)} Module, {tasks} Aufgaben geprüft, {len(errors)} Fehler")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
