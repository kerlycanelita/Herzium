"""Validate and launch the exact release JAR in an isolated, self-closing client.

First build with version/build-all.ps1 -MinecraftVersions ... -ExportRuntime.
No release classes or user profiles are modified. Requires the resolved Loom cache.
"""
import argparse
import hashlib
import json
import os
import re
from pathlib import Path
import shutil
import subprocess
import time
import zipfile

ROOT = Path(__file__).resolve().parents[2]
MC = "net.minecraft.client.Minecraft"
SCREEN = "net.minecraft.client.gui.screens.Screen"
TARGETS = [MC, "net.minecraft.client.KeyMapping", "net.minecraft.client.MouseHandler",
           "net.minecraft.client.gui.screens.TitleScreen"]


class Names:
    def __init__(self, classpath):
        self.classes = {}
        self.members = {}
        mapping = next((Path(p) for p in classpath if p.endswith("mappings.jar")), None)
        if mapping:
            with zipfile.ZipFile(mapping) as archive:
                lines = archive.read("mappings/mappings.tiny").decode().splitlines()
            namespaces = lines[0].split("\t")[3:]
            named, runtime = namespaces.index("named"), namespaces.index("intermediary")
            owner = None
            for line in lines[1:]:
                parts = line.split("\t")
                if parts[0] == "c":
                    owner = parts[1 + named].replace("/", ".")
                    self.classes[owner] = parts[1 + runtime].replace("/", ".")
                elif len(parts) > 4 and parts[0] == "" and parts[1] in ("m", "f"):
                    key = (owner, parts[3 + named])
                    self.members.setdefault(key, set()).add(parts[3 + runtime])

    def cls(self, name):
        return self.classes.get(name, name)

    def member(self, owner, name):
        values = self.members.get((owner, name), {name})
        if len(values) != 1:
            raise ValueError(f"Ambiguous mapping: {owner}.{name}: {values}")
        return next(iter(values))


def validate_jar(jar, version):
    with zipfile.ZipFile(jar) as archive:
        assert archive.testzip() is None, "Corrupt ZIP"
        meta = json.loads(archive.read("fabric.mod.json"))
        assert meta["id"] == "herzium" and meta["environment"] == "client"
        assert meta["depends"]["minecraft"] == version
        java = 21 if version.startswith("1.") else 25
        assert meta["depends"]["java"] == f">={java}"
        assert meta["depends"]["fabricloader"] == ">=0.19.3"
        mixins = json.loads(archive.read("herzium.client.mixins.json"))
        assert mixins["compatibilityLevel"] == f"JAVA_{java}"
        assert mixins["required"] and mixins["injectors"]["defaultRequire"] == 1
        for mixin in mixins["client"]:
            assert (mixins["package"] + "." + mixin).replace(".", "/") + ".class" in archive.namelist()
        languages = [n for n in archive.namelist() if n.startswith("assets/herzium/lang/") and n.endswith(".json")]
        english = json.loads(archive.read("assets/herzium/lang/en_us.json"))
        assert len(languages) == 8
        for lang in languages:
            assert json.loads(archive.read(lang)).keys() == english.keys(), lang
        for name in archive.namelist():
            assert not name.startswith("herzium/validation/"), "Test mod leaked into release"
            if name.endswith(".class"):
                assert int.from_bytes(archive.read(name)[6:8], "big") <= java + 44, name
        assert meta["icon"] in archive.namelist()
    return {"file": jar.name, "minecraft": version, "mod_version": meta["version"],
            "sha512": hashlib.sha512(jar.read_bytes()).hexdigest(),
            "sha256": hashlib.sha256(jar.read_bytes()).hexdigest(), "metadata": "PASS"}


def smoke(version, output, gameplay=False, extra_mods=()):
    build = ROOT / "version" / version / "build"
    spec = json.loads((build / "validation/runtime.json").read_text())
    jars = [p for p in (build / "libs").glob("*.jar") if not p.name.endswith("-sources.jar")]
    assert len(jars) == 1, jars
    jar = jars[0]
    result = validate_jar(jar, version)
    profile = output / version
    profile.mkdir(parents=True, exist_ok=False)
    (profile / "mods").mkdir()
    shutil.copy2(jar, profile / "mods" / jar.name)
    for extra in extra_mods:
        shutil.copy2(extra, profile / "mods" / extra.name)
    (profile / "options.txt").write_text("onboardAccessibility:false\ntutorialStep:none\nfullscreen:false\nmaxFps:60\nguiScale:2\nrenderDistance:3\nsimulationDistance:5\nsoundCategory_master:0.0\n")
    names = Names(spec["classpath"])
    split_gui = version in ("26.2", "26.3")
    targets = TARGETS + ["net.minecraft.client.gui.Hud" if split_gui else "net.minecraft.client.gui.Gui",
                         "net.minecraft.client.player.FirstPersonHandsAndItems" if version == "26.3"
                         else "net.minecraft.client.renderer.ItemInHandRenderer"]
    if split_gui:
        targets.append("net.minecraft.client.gui.Gui")
    tick = names.member(MC, "runTick")
    source = f'''package herzium.validation.mixin;
import herzium.validation.SmokeChecks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(targets="{names.cls(MC)}", remap=false)
abstract class SmokeTickMixin {{
 @Inject(method="{tick}(Z)V", at=@At("TAIL"), remap=false, require=1)
 private void smoke(boolean advance, CallbackInfo ci) {{ SmokeChecks.tick(this); }}
}}'''
    (profile / "SmokeTickMixin.java").write_text(source)
    classes = profile / "classes"
    classes.mkdir()
    compile_cp = os.pathsep.join(spec["classpath"])
    sources = [str(profile / "SmokeTickMixin.java"), str(ROOT / "tools/validation/SmokeChecks.java")]
    if gameplay:
        assert version == "26.3", "World integration currently targets the new 26.3 hand adapter"
        sources.append(str(ROOT / "tools/validation/Gameplay263.java"))
    java = Path(spec["java"])
    subprocess.run([str(java.with_name("javac.exe" if os.name == "nt" else "javac")), "-proc:none", "--release", "21",
                    "-cp", compile_cp, "-d", str(classes), *sources], check=True)
    with zipfile.ZipFile(profile / "mods/herzium-validation.jar", "w") as archive:
        for cls in classes.rglob("*.class"):
            archive.write(cls, cls.relative_to(classes).as_posix())
        archive.writestr("fabric.mod.json", json.dumps({"schemaVersion": 1, "id": "herzium_validation",
                          "version": "1.0.0", "environment": "client", "mixins": ["smoke.mixins.json"]}))
        archive.writestr("smoke.mixins.json", json.dumps({"required": True, "package": "herzium.validation.mixin",
                          "compatibilityLevel": "JAVA_21", "client": ["SmokeTickMixin"]}))
    classpath = []
    games = {}
    cache = None
    for entry in spec["classpath"]:
        p = Path(entry)
        if not p.is_file() or not entry.endswith(".jar") or entry.endswith("mappings.jar"):
            continue
        if "minecraftMaven" in entry:
            kind = "client" if "clientonly" in entry else "common"
            if version.startswith("1."):
                for base in ("minecraft-clientonly", "minecraft-common"):
                    entry = entry.replace(base, base + "-intermediary")
            assert Path(entry).is_file(), entry
            games[kind] = entry
            cache = Path(entry.split("minecraftMaven")[0])
        classpath.append(entry)
    info = json.loads((cache / version / "mojang_minecraft_info.json").read_text())
    asset_index = f"{version}-{info['assetIndex']['id']}"
    index_path = cache / "assets/indexes" / f"{asset_index}.json"
    assert index_path.is_file(), f"Run Loom downloadAssets first: {index_path}"
    assert hashlib.sha1(index_path.read_bytes()).hexdigest() == info["assetIndex"]["sha1"], "Asset index checksum mismatch"
    props = {
        "fabric.development": "false", "fabric.gameVersion": version,
        "fabric.gameJarPath": games["common"], "fabric.gameJarPath.client": games["client"],
        "fabric.defaultModDistributionNamespace": "intermediary" if version.startswith("1.") else "official",
        "fabric.defaultMixinRemapType": "static", "mixin.debug.export": "true",
        "herzium.smoke.guiField": "gui" if split_gui else "",
        "herzium.smoke.screenField": names.member(MC, "screen"),
        "herzium.smoke.screenClass": names.cls(SCREEN),
        "herzium.smoke.titleClass": names.cls(TARGETS[-1]),
        "herzium.smoke.targets": ",".join(names.cls(t) for t in targets),
        "herzium.smoke.setScreen": "setScreen" if split_gui else names.member(MC, "setScreen"),
        "herzium.smoke.onClose": names.member(SCREEN, "onClose"),
        "herzium.smoke.stop": names.member(MC, "stop"),
        "herzium.smoke.gameplay": str(gameplay).lower(),
        "herzium.smoke.modmenu": str(any(p.name.startswith("modmenu-") for p in extra_mods)).lower(),
        "herzium.smoke.getTitle": names.member(SCREEN, "getTitle"),
        "herzium.smoke.getString": names.member("net.minecraft.network.chat.Component", "getString"),
    }
    arguments = ["-Xmx2G", "-Dfile.encoding=UTF-8", f"-XX:ErrorFile={profile / 'jvm-crash.log'}"]
    arguments += [f"-D{k}={v}" for k, v in props.items()]
    arguments += ["-cp", os.pathsep.join(classpath), "net.fabricmc.loader.impl.launch.knot.KnotClient",
                  "--username", "HerziumAudit", "--accessToken", "0", "--version", version,
                  "--gameDir", str(profile), "--assetsDir", str(cache / "assets"),
                  "--assetIndex", asset_index, "--width", "960", "--height", "540"]
    argfile = profile / "launch.args"
    argfile.write_text("\n".join('"' + arg.replace("\\", "/").replace('"', '\\"') + '"' for arg in arguments))
    log = profile / "client.log"
    with log.open("w", encoding="utf-8") as stream:
        process = subprocess.Popen([str(java), "@" + str(argfile)], cwd=profile, stdout=stream, stderr=subprocess.STDOUT)
        print(f"{version}: PID {process.pid}, testing {jar.name}", flush=True)
        try:
            code = process.wait(timeout=150)
        except subprocess.TimeoutExpired:
            process.terminate()
            code = process.wait(timeout=15)
            raise RuntimeError(f"{version}: timed out; see {log}")
        finally:
            if process.poll() is None:
                process.kill()
                process.wait()
    text = log.read_text(encoding="utf-8", errors="replace")
    if code != 0 or "[HERZIUM-SMOKE] PASS" not in text:
        print(text[-10000:])
        raise RuntimeError(f"{version}: client failed ({code}); see {log}")
    for line in text.splitlines():
        if "[HERZIUM-SMOKE]" in line:
            print(line, flush=True)
    config = json.loads((profile / "config/herzium.json").read_text())
    assert config["startupWarningAcknowledged"] and config["hotbarOrder"] == "HERZIUM"
    for target, handler in [(targets[4], "renderVanillaResolvableHotbarInput"),
                            (targets[5], "replaceVisibleItemImmediately")]:
        exported = profile / ".mixin.out/class" / (names.cls(target).replace(".", "/") + ".class")
        bytecode = subprocess.check_output([str(java.with_name("javap.exe" if os.name == "nt" else "javap")),
                                            "-p", "-c", str(exported)], text=True, encoding="utf-8")
        assert re.search(r"invoke\w+.*herzium\$" + handler, bytecode), f"Optional hook did not inject: {handler}"
    assert "Can't open the resource index" not in text and "Couldn't set icon" not in text, "Broken validation assets"
    result.update(runtime="PASS", optional_injections="PASS", gameplay=gameplay,
                  extra_mods=[p.name for p in extra_mods], exit_code=code, log=str(log))
    (profile / "result.json").write_text(json.dumps(result, indent=2))
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("versions", nargs="+", choices=["1.21.10", "1.21.11", "26.1", "26.1.1", "26.1.2", "26.2", "26.3"])
    parser.add_argument("--gameplay", action="store_true", help="Also test a fresh 26.3 world and the new hand extraction adapter")
    parser.add_argument("--mods", nargs="*", type=Path, default=[], help="Additional mod JARs to copy into each isolated profile")
    args = parser.parse_args()
    output = ROOT / "tmp" / "release-audit" / time.strftime("smoke-%Y%m%d-%H%M%S")
    output.mkdir(parents=True)
    results = [smoke(version, output, args.gameplay, args.mods) for version in args.versions]
    (output / "results.json").write_text(json.dumps(results, indent=2))
    print(f"PASS: {len(results)} release clients. Report: {output / 'results.json'}")


if __name__ == "__main__":
    main()
