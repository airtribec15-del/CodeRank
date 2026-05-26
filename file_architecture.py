#!/usr/bin/env python3
"""
file_architecture.py
Drop this file into any folder and run it.
Output files are always saved in the SAME folder as this script.
Only includes relevant source/config files. Skips build/output directories.
"""

import os
import datetime

# ── WHAT TO INCLUDE ────────────────────────────────────────────────────────────
# Only files with these extensions will appear in the tree
INCLUDE_EXTENSIONS = {
    # Java / JVM
    ".java", ".kt", ".kts", ".groovy", ".scala",
    # Build & dependency
    ".xml", ".gradle", ".pom", ".properties", ".lock",
    # Config & infra
    ".yml", ".yaml", ".toml", ".ini", ".cfg", ".conf", ".env",
    # Database
    ".sql", ".ddl", ".dml",
    # Web / frontend source
    ".js", ".ts", ".jsx", ".tsx", ".css", ".scss", ".sass", ".less", ".html",
    # Python
    ".py",
    # Shell / scripts
    ".sh", ".bash", ".bat", ".cmd", ".ps1",
    # Docs / markdown
    ".md", ".rst", ".txt",
    # Data
    ".json", ".csv",
    # Docker & containerisation  (handled separately for exact names below)
}

# Exact filenames (no extension) that are always included
INCLUDE_EXACT_NAMES = {
    "dockerfile", "dockerfile.dev", "dockerfile.prod",
    "docker-compose", "docker-compose.yml", "docker-compose.yaml",
    "makefile", "jenkinsfile", "procfile",
    ".gitignore", ".dockerignore", ".editorconfig",
    "readme", "license", "contributing",
}

# ── WHAT TO SKIP ───────────────────────────────────────────────────────────────
# These folders are completely skipped (build output, caches, IDE noise)
IGNORE_DIRS = {
    # Build output
    "target", "build", "out", "bin", "dist", "release", "output",
    # Dependency caches
    "node_modules", ".gradle", ".m2",
    # IDE / tooling
    ".git", ".idea", ".vscode", ".eclipse",
    "__pycache__", ".mypy_cache", ".pytest_cache",
    # Python venvs
    ".venv", "venv", "env",
    # Misc
    ".cache", "tmp", "temp", "logs", "log",
}

# These exact filenames are always ignored
IGNORE_FILES = {
    ".DS_Store", "Thumbs.db", ".gitkeep",
    "file_architecture.py", "file_architecture.txt", "file_architecture.html",
}

MAX_DEPTH = None   # set an int (e.g. 6) to limit recursion depth

ROOT = os.path.dirname(os.path.abspath(__file__))

# ── helpers ────────────────────────────────────────────────────────────────────
def is_relevant(entry) -> bool:
    """Return True if this file should be included."""
    name = entry.name
    if name in IGNORE_FILES:
        return False
    lower = name.lower()
    # exact name match (case-insensitive)
    if lower in INCLUDE_EXACT_NAMES or os.path.splitext(lower)[0] in INCLUDE_EXACT_NAMES:
        return True
    # extension match
    ext = os.path.splitext(lower)[1]
    return ext in INCLUDE_EXTENSIONS


def human_size(path: str) -> str:
    try:
        s = os.path.getsize(path)
    except OSError:
        return "?"
    for unit in ("B", "KB", "MB", "GB"):
        if s < 1024:
            return f"{s:.1f} {unit}"
        s /= 1024
    return f"{s:.1f} TB"


def filtered_entries(root: str):
    """Return (dirs, files) after applying all filters."""
    try:
        raw = sorted(os.scandir(root), key=lambda e: (not e.is_dir(), e.name.lower()))
    except PermissionError:
        return [], []
    dirs  = [e for e in raw if e.is_dir(follow_symlinks=False) and e.name not in IGNORE_DIRS]
    files = [e for e in raw if e.is_file() and is_relevant(e)]
    return dirs, files


def has_relevant_content(path: str, depth: int = 0) -> bool:
    """True if a dir (recursively) contains at least one relevant file."""
    if MAX_DEPTH is not None and depth > MAX_DEPTH:
        return False
    dirs, files = filtered_entries(path)
    if files:
        return True
    return any(has_relevant_content(d.path, depth + 1) for d in dirs)


def build_tree(root: str, prefix: str = "", depth: int = 0) -> list:
    lines = []
    if MAX_DEPTH is not None and depth > MAX_DEPTH:
        return lines
    dirs, files = filtered_entries(root)
    # only keep dirs that actually contain relevant files
    dirs = [d for d in dirs if has_relevant_content(d.path, depth + 1)]
    entries = dirs + files

    for i, entry in enumerate(entries):
        connector = "└── " if i == len(entries) - 1 else "├── "
        extender  = "    " if i == len(entries) - 1 else "│   "
        if entry.is_dir(follow_symlinks=False):
            lines.append(f"{prefix}{connector}{entry.name}/")
            lines.extend(build_tree(entry.path, prefix + extender, depth + 1))
        else:
            lines.append(f"{prefix}{connector}{entry.name}  [{human_size(entry.path)}]")
    return lines


def build_html_tree(root: str, depth: int = 0) -> str:
    if MAX_DEPTH is not None and depth > MAX_DEPTH:
        return ""
    dirs, files = filtered_entries(root)
    dirs = [d for d in dirs if has_relevant_content(d.path, depth + 1)]
    entries = dirs + files
    if not entries:
        return ""

    items = []
    for entry in entries:
        if entry.is_dir(follow_symlinks=False):
            inner     = build_html_tree(entry.path, depth + 1)
            toggle_id = f"d{abs(hash(entry.path))}"
            items.append(
                f'<li class="dir">'
                f'<label for="{toggle_id}">📁 <strong>{entry.name}</strong></label>'
                f'<input type="checkbox" id="{toggle_id}" class="toggle" checked>'
                f'{inner}'
                f'</li>'
            )
        else:
            ext  = os.path.splitext(entry.name)[1].lower()
            size = human_size(entry.path)
            icon = {
                ".java":"☕", ".kt":"🟣", ".scala":"🔴", ".groovy":"🟢",
                ".xml":"📋",  ".gradle":"🔨", ".pom":"🔨",
                ".yml":"⚙️",  ".yaml":"⚙️", ".toml":"⚙️", ".properties":"⚙️",
                ".sql":"🗄",  ".ddl":"🗄",
                ".py":"🐍",   ".js":"📜",  ".ts":"📜",
                ".html":"🌐", ".css":"🎨", ".scss":"🎨",
                ".sh":"⚙️",   ".bat":"⚙️", ".ps1":"⚙️",
                ".md":"📝",   ".txt":"📄", ".json":"📋",
                ".csv":"📊",  ".env":"🔒",
            }.get(ext, "📄")
            # special exact-name icons
            lower = entry.name.lower()
            if "dockerfile" in lower:
                icon = "🐳"
            elif "docker-compose" in lower:
                icon = "🐳"
            elif lower in ("makefile", "jenkinsfile"):
                icon = "🔧"
            items.append(
                f'<li class="file">'
                f'<span class="file-icon">{icon}</span>'
                f'<span class="file-name">{entry.name}</span>'
                f'<span class="file-size">{size}</span>'
                f'</li>'
            )
    return "<ul>" + "".join(items) + "</ul>"


def generate_stats(root: str) -> dict:
    stats = {"files": 0, "dirs": 0, "size_bytes": 0, "ext_counts": {}}
    for dirpath, dirnames, filenames in os.walk(root):
        dirnames[:] = [d for d in dirnames if d not in IGNORE_DIRS]
        stats["dirs"] += len(dirnames)
        for f in filenames:
            entry_path = os.path.join(dirpath, f)
            # simulate is_relevant via a dummy object
            class _E:
                name = f
                def is_file(self): return True
            if not is_relevant(_E()):
                continue
            stats["files"] += 1
            ext = os.path.splitext(f)[1].lower() or "(no ext)"
            stats["ext_counts"][ext] = stats["ext_counts"].get(ext, 0) + 1
            try:
                stats["size_bytes"] += os.path.getsize(entry_path)
            except OSError:
                pass
    return stats


# ── main ───────────────────────────────────────────────────────────────────────
def main():
    root      = ROOT
    root_name = os.path.basename(root) or root
    now       = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")

    print(f"📂  Scanning: {root}")

    tree_lines  = [f"{root_name}/"] + build_tree(root)
    text_output = "\n".join(tree_lines)

    stats = generate_stats(root)
    total = stats["size_bytes"]
    for unit in ("B", "KB", "MB", "GB"):
        if total < 1024:
            size_str = f"{total:.1f} {unit}"
            break
        total /= 1024
    else:
        size_str = f"{total:.1f} TB"

    top_exts = sorted(stats["ext_counts"].items(), key=lambda x: -x[1])[:10]
    ext_rows = "".join(
        f"<tr><td><code>{e}</code></td><td>{c}</td></tr>" for e, c in top_exts
    )

    html_tree    = build_html_tree(root)
    html_content = f"""<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>File Architecture — {root_name}</title>
<style>
  *{{box-sizing:border-box;margin:0;padding:0}}
  body{{font-family:'Segoe UI',system-ui,sans-serif;background:#0f1117;color:#c9d1d9;min-height:100vh;padding:2rem}}
  h1{{font-size:1.6rem;color:#58a6ff;margin-bottom:.3rem}}
  .meta{{font-size:.85rem;color:#8b949e;margin-bottom:1.5rem}}
  .meta span{{margin-right:1.2rem}}
  .badge{{display:inline-block;background:#21262d;border:1px solid #30363d;border-radius:4px;
           font-size:.72rem;padding:.15rem .5rem;color:#79c0ff;margin-left:.4rem;vertical-align:middle}}
  .stats{{display:flex;flex-wrap:wrap;gap:1rem;margin-bottom:2rem}}
  .stat-card{{background:#161b22;border:1px solid #30363d;border-radius:8px;padding:1rem 1.4rem;min-width:140px}}
  .stat-card .val{{font-size:1.8rem;font-weight:700;color:#58a6ff}}
  .stat-card .lbl{{font-size:.78rem;color:#8b949e;margin-top:.2rem}}
  .columns{{display:grid;grid-template-columns:1fr 340px;gap:1.5rem;align-items:start}}
  @media(max-width:900px){{.columns{{grid-template-columns:1fr}}}}
  .tree-box{{background:#161b22;border:1px solid #30363d;border-radius:10px;padding:1.2rem;overflow:auto;max-height:70vh}}
  ul{{list-style:none;padding-left:1.4rem}}
  ul:first-child{{padding-left:0}}
  li{{margin:.18rem 0;font-size:.88rem;line-height:1.6}}
  li.dir>label{{cursor:pointer;font-size:.9rem;color:#79c0ff;user-select:none}}
  li.dir>label:hover{{color:#58a6ff}}
  .toggle{{display:none}}
  .toggle~ul{{display:block}}
  .toggle:not(:checked)~ul{{display:none}}
  .file{{display:flex;align-items:center;gap:.4rem}}
  .file-name{{color:#c9d1d9}}
  .file-size{{margin-left:auto;font-size:.76rem;color:#8b949e;white-space:nowrap}}
  .file-icon{{font-size:.9rem}}
  .ext-table{{background:#161b22;border:1px solid #30363d;border-radius:10px;overflow:hidden}}
  .ext-table h2{{font-size:1rem;padding:.9rem 1.2rem;border-bottom:1px solid #30363d;color:#79c0ff}}
  table{{width:100%;border-collapse:collapse}}
  th,td{{padding:.5rem 1.2rem;text-align:left;font-size:.85rem}}
  th{{background:#0d1117;color:#8b949e;font-weight:600}}
  tr:nth-child(even){{background:#0d1117}}
  td:last-child{{text-align:right;color:#58a6ff;font-weight:600}}
  .pre-box{{background:#161b22;border:1px solid #30363d;border-radius:10px;margin-top:1.5rem;overflow:auto}}
  .pre-box h2{{font-size:1rem;padding:.9rem 1.2rem;border-bottom:1px solid #30363d;color:#79c0ff;
               display:flex;justify-content:space-between;align-items:center}}
  pre{{padding:1rem 1.2rem;font-size:.78rem;line-height:1.6;color:#adbac7;white-space:pre;overflow:auto;max-height:40vh}}
  .copy-btn{{font-size:.76rem;padding:.3rem .7rem;background:#21262d;border:1px solid #30363d;
              border-radius:6px;color:#8b949e;cursor:pointer}}
  .copy-btn:hover{{background:#30363d;color:#c9d1d9}}
  .skipped{{font-size:.8rem;color:#8b949e;margin-bottom:1.5rem;line-height:1.8}}
  .skipped strong{{color:#c9d1d9}}
</style>
</head>
<body>
<h1>📂 {root_name} <span class="badge">source files only</span></h1>
<div class="meta">
  <span>📍 {root}</span>
  <span>🕐 {now}</span>
</div>

<div class="skipped">
  <strong>Excluded folders:</strong> target, build, out, bin, dist, node_modules, .gradle, .m2, .git, .idea, .vscode, logs, tmp …<br>
  <strong>Included types:</strong> .java .xml .sql .yml .yaml .properties .gradle .json .py .sh .md .env Dockerfile docker-compose …
</div>

<div class="stats">
  <div class="stat-card"><div class="val">{stats['files']}</div><div class="lbl">Source Files</div></div>
  <div class="stat-card"><div class="val">{stats['dirs']}</div><div class="lbl">Folders</div></div>
  <div class="stat-card"><div class="val">{size_str}</div><div class="lbl">Source Size</div></div>
  <div class="stat-card"><div class="val">{len(stats['ext_counts'])}</div><div class="lbl">File Types</div></div>
</div>

<div class="columns">
  <div>
    <div class="tree-box">
      <ul><li class="dir">
        <label for="root-toggle">📁 <strong>{root_name}</strong></label>
        <input type="checkbox" id="root-toggle" class="toggle" checked>
        {html_tree}
      </li></ul>
    </div>
  </div>
  <div>
    <div class="ext-table">
      <h2>Top File Types</h2>
      <table>
        <tr><th>Extension</th><th>Count</th></tr>
        {ext_rows}
      </table>
    </div>
  </div>
</div>

<div class="pre-box">
  <h2>Plain-Text Tree <button class="copy-btn" onclick="copyTree()">Copy</button></h2>
  <pre id="tree-text">{text_output}</pre>
</div>

<script>
function copyTree(){{
  navigator.clipboard.writeText(document.getElementById('tree-text').innerText)
    .then(()=>{{const b=document.querySelector('.copy-btn');b.textContent='Copied!';setTimeout(()=>b.textContent='Copy',2000)}})
}}
</script>
</body>
</html>"""

    txt_path  = os.path.join(ROOT, "file_architecture.txt")
    html_path = os.path.join(ROOT, "file_architecture.html")

    with open(txt_path, "w", encoding="utf-8") as f:
        f.write(text_output)
    with open(html_path, "w", encoding="utf-8") as f:
        f.write(html_content)

    print(f"\n✅  file_architecture.txt   saved in script folder")
    print(f"✅  file_architecture.html  saved in script folder")
    print(f"\n📊  {stats['files']} source files | {stats['dirs']} folders | {size_str}")
    print("\nTop file types:")
    for ext, cnt in top_exts:
        print(f"   {ext:<14} {cnt}")
    print("\n🚫  Skipped: target/, build/, out/, bin/, .class, .html (compiled), node_modules/, .gradle/, logs/ …")


if __name__ == "__main__":
    main()