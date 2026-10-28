"""Einmalige Überführung der alten Inhalte (backend/src/main/resources/content/*.json, HTML)
in Content-Pakete (ein Ordner je Modul, eine Datei je Aufgabe, Texte als Markdown).

    python content/tools/convert_legacy.py <alter-content-ordner> <ziel-ordner>

Danach werden die Pakete von Hand durchgesehen; das Skript bleibt als Nachweis der Migration im Repo.
"""
import json
import re
import sys
from html.parser import HTMLParser
from pathlib import Path

OPTION_IDS = "abcdefghijklmnopqrstuvwxyz"


class MarkdownWriter(HTMLParser):
    """Wandelt das kleine HTML-Vokabular der alten Inhalte in Markdown um."""

    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.out = []
        self.lists = []  # Stapel aus "ul"/"ol" mit Zähler
        self.in_pre = False
        self.in_code = False
        self.table = None  # Liste von Zeilen, jede Zeile Liste von Zellen
        self.cell = None

    def handle_starttag(self, tag, attrs):
        if self.table is not None and tag in ("td", "th"):
            self.cell = []
            return
        if tag == "tr" and self.table is not None:
            self.table.append([])
            return
        if tag == "table":
            self.table = []
        elif tag == "p":
            self._block()
        elif tag == "br":
            self.out.append("  \n" if not self.in_pre else "\n")
        elif tag == "pre":
            self._block()
            self.out.append("```\n")
            self.in_pre = True
        elif tag == "code" and not self.in_pre:
            self._emit("`")
            self.in_code = True
        elif tag in ("strong", "b"):
            self._emit("**")
        elif tag in ("em", "i"):
            self._emit("*")
        elif tag in ("ul", "ol"):
            self._block()
            self.lists.append([tag, 0])
        elif tag == "li":
            kind = self.lists[-1] if self.lists else ["ul", 0]
            kind[1] += 1
            self.out.append("\n" + ("1. " if kind[0] == "ol" else "- ").replace("1", str(kind[1])) if kind[0] == "ol" else "\n- ")

    def handle_endtag(self, tag):
        if self.table is not None and tag in ("td", "th"):
            self.table[-1].append("".join(self.cell).strip().replace("|", "\\|"))
            self.cell = None
            return
        if tag == "table" and self.table is not None:
            rows, self.table = self.table, None
            self._block()
            if rows:
                width = max(len(r) for r in rows)
                rows = [r + [""] * (width - len(r)) for r in rows]
                self.out.append("| " + " | ".join(rows[0]) + " |\n")
                self.out.append("|" + "---|" * width + "\n")
                for r in rows[1:]:
                    self.out.append("| " + " | ".join(r) + " |\n")
            self._block()
        elif tag == "p":
            self._block()
        elif tag == "pre":
            if not "".join(self.out).endswith("\n"):
                self.out.append("\n")
            self.out.append("```")
            self.in_pre = False
            self._block()
        elif tag == "code" and not self.in_pre:
            self._emit("`")
            self.in_code = False
        elif tag in ("strong", "b"):
            self._emit("**")
        elif tag in ("em", "i"):
            self._emit("*")
        elif tag in ("ul", "ol"):
            if self.lists:
                self.lists.pop()
            self._block()

    def handle_data(self, data):
        if self.cell is not None:
            self.cell.append(data)
        elif self.in_pre:
            self.out.append(data)
        else:
            self._emit(re.sub(r"\s+", " ", data) if not self.in_code else data)

    def _emit(self, text):
        if self.cell is not None:
            self.cell.append(text)
        else:
            self.out.append(text)

    def _block(self):
        text = "".join(self.out)
        if text and not text.endswith("\n\n"):
            self.out.append("\n\n" if not text.endswith("\n") else "\n")

    def markdown(self):
        text = "".join(self.out)
        text = re.sub(r"[ \t]+\n", lambda m: "  \n" if m.group(0).startswith("  ") else "\n", text)
        text = re.sub(r"\n{3,}", "\n\n", text)
        return text.strip()


def to_markdown(html):
    if not html:
        return ""
    if "<" not in html:
        return html.strip()
    writer = MarkdownWriter()
    writer.feed(html)
    writer.close()
    return writer.markdown()


def convert_blank(blank):
    if isinstance(blank, dict):
        result = {"answers": [blank["answer"]]}
        if blank.get("caseSensitive"):
            result["caseSensitive"] = True
        return result
    return {"answers": [blank]}


def rubric_from(instructions, points):
    """Nummerierte Punkte der Aufgabenstellung werden zu Bewertungskriterien mit gleich verteilten Punkten."""
    items = [m.group(1).strip() for m in re.finditer(r"^\s*\d+\.\s*(.+)$", instructions or "", re.M)]
    if not items:
        return [{"id": "gesamt", "label": "Gesamteindruck", "points": points}]
    base, rest = divmod(points, len(items))
    rubric = []
    for i, label in enumerate(items):
        rubric.append({"id": f"k{i + 1}", "label": label, "points": base + (1 if i < rest else 0)})
    return [c for c in rubric if c["points"] > 0]


def convert_config(task):
    cfg = task["config"]
    kind = task["type"]
    if kind == "MULTIPLE_CHOICE":
        options = [{"id": OPTION_IDS[i], "text": text} for i, text in enumerate(cfg["options"])]
        correct = [OPTION_IDS[i] for i in cfg["correct"]]
        return {"options": options, "correct": correct, "multiple": len(correct) > 1}
    if kind == "FILL_BLANK":
        return {"template": cfg["template"], "blanks": [convert_blank(b) for b in cfg["blanks"]]}
    if kind == "FILL_CODE":
        return {
            "template": cfg["template"],
            "blanks": [convert_blank(b) for b in cfg["blanks"]],
            "tests": [{"expectedOutput": cfg["expectedOutput"]}],
        }
    if kind == "CODE":
        return {"starterCode": cfg.get("starterCode", ""), "tests": [{"expectedOutput": cfg["expectedOutput"]}]}
    if kind == "SORTING":
        items = [{"id": f"s{i + 1}", "text": text} for i, text in enumerate(cfg["items"])]
        return {"items": items, "correctOrder": [f"s{i + 1}" for i in cfg["correctOrder"]]}
    if kind == "SQL":
        return {
            "schemaSql": cfg["schemaSql"],
            "solutionSql": cfg["solutionSql"],
            "starterSql": cfg.get("starterSql", ""),
            "orderMatters": False,
        }
    if kind == "TERMINAL":
        expected = [p for p in cfg["expectedState"].split(",") if p]
        # Pfade mit Unterpfaden sind Ordner, alle anderen Dateien
        dirs = sorted({p for p in expected if any(o.startswith(p + "/") for o in expected)})
        files = sorted(p for p in expected if p not in dirs)
        return {"initialDirectories": [], "initialFiles": {}, "expectedDirectories": dirs, "expectedFiles": files}
    if kind == "PRACTICE":
        return {
            "instructions": cfg.get("instructions", ""),
            "submissionNote": cfg.get("submissionNote", ""),
            "rubric": rubric_from(cfg.get("instructions"), task["points"]),
        }
    raise ValueError(f"Unbekannter Typ {kind}")


def convert_task(task):
    cfg = task["config"]
    hints = [h for h in [cfg.get("hint"), cfg.get("support")] if h]
    result = {
        "slug": task["slug"],
        "type": task["type"],
        "title": task["title"],
        "difficulty": task["difficulty"],
        "points": task["points"],
        "sortOrder": task["sortOrder"],
        "learningGoal": "",
        "story": to_markdown(task.get("story")),
        "assignment": to_markdown(task.get("description")),
        "hints": [to_markdown(h) for h in hints],
        "explanation": "",
        "config": convert_config(task),
    }
    if task["type"] == "PRACTICE":
        result["config"]["instructions"] = to_markdown(result["config"]["instructions"])
    return result


def main(source, target):
    source, target = Path(source), Path(target)
    for path in sorted(source.glob("*.json")):
        data = json.loads(path.read_text(encoding="utf-8"))
        module_dir = target / data["slug"]
        (module_dir / "tasks").mkdir(parents=True, exist_ok=True)
        module = {
            "slug": data["slug"],
            "title": data["title"],
            "description": data["description"],
            "icon": data["iconEmoji"],
            "sortOrder": data["sortOrder"],
        }
        (module_dir / "module.json").write_text(json.dumps(module, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        for task in sorted(data["tasks"], key=lambda t: t["sortOrder"]):
            converted = convert_task(task)
            name = f"{task['sortOrder']:02d}-{task['slug']}.json"
            (module_dir / "tasks" / name).write_text(json.dumps(converted, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(f"{data['slug']}: {len(data['tasks'])} Aufgaben")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
