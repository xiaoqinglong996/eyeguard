import sys, importlib.util

SPACE = "QNBtsNzNZSBGX3gUoPM91d"
PARENT = "mcH9xTtfrmNKXHuaC9AwAN"
SP = r"C:/Users/Administrator/AppData/Local/Programs/WorkBuddy/resources/app.asar.unpacked/resources/plugins/workbuddy-builtin/skills/library/space_api.py"
MD_PATH = r"C:/Users/Administrator/WorkBuddy/2026-10-23-16-51-22/eyeguard/README.md"

with open(MD_PATH, "r", encoding="utf-8") as f:
    md = f.read()

sys.argv = [
    "space_api", "space.importer.create-doc",
    "--space-id", SPACE,
    "--parent-id", PARENT,
    "--title", "护眼助手 EyeGuard 项目说明 README（最新 v1.4）",
    "--markdown", md,
]

spec = importlib.util.spec_from_file_location("space_api", SP)
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)
raise SystemExit(m.main())
