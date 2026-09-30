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
GRADLE_MODULES = Path.home() / ".gradle/caches/modules-2/files-2.1"
# Tick boundaries and carried-slot packets for the gameplay harness, with names resolved per target.
ORDERS_MIXINS = """package herzium.validation.mixin;
import herzium.validation.{hook};
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(targets="{mc}", remap=false)
abstract class OrdersTickMixin {{
 @Inject(method="{tick}()V", at=@At("HEAD"), remap=false, require=1)
 private void herziumOrders$tickHead(CallbackInfo ci) {{ {hook}.onTickHead(); }}
 @Inject(method="{tick}()V", at=@At("RETURN"), remap=false, require=1)
 private void herziumOrders$tickReturn(CallbackInfo ci) {{ {hook}.onTickReturn(); }}
}}
@Mixin(targets="{listener}", remap=false)
abstract class OrdersPacketMixin {{
 @Inject(method="{send}(L{packet};)V", at=@At("HEAD"), remap=false, require=1)
 private void herziumOrders$send(@Coerce Object packet, CallbackInfo ci) {{ {hook}.onSend(packet); }}
}}
"""


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


def module_jar(group, artifact, version):
    jars = list((GRADLE_MODULES / group / artifact / version).glob(f"*/{artifact}-{version}.jar"))
    assert jars, f"{group}:{artifact}:{version} is not in the Gradle cache"
    return jars[0]


def remap_to_intermediary(named_jar, remapped_jar, spec, profile):
    """Remaps the validation classes the way Loom remaps a 1.21.x release JAR."""
    mapping = next(Path(p) for p in spec["classpath"] if p.endswith("mappings.jar"))
    tiny = profile / "mappings.tiny"
    with zipfile.ZipFile(mapping) as archive:
        tiny.write_bytes(archive.read("mappings/mappings.tiny"))
    tools = [module_jar("net.fabricmc", "tiny-remapper", "0.14.0"), module_jar("net.fabricmc", "mapping-io", "0.8.0")]
    tools += [module_jar("org.ow2.asm", name, "9.10.1")
              for name in ("asm", "asm-commons", "asm-tree", "asm-util", "asm-analysis")]
    libraries = [p for p in spec["classpath"]
                 if p.endswith(".jar") and not p.endswith("mappings.jar") and Path(p).is_file()]
    subprocess.run([spec["java"], "-cp", os.pathsep.join(map(str, tools)), "net.fabricmc.tinyremapper.Main",
                    str(named_jar), str(remapped_jar), str(tiny), "named", "intermediary", *libraries], check=True)


def smoke(version, output, gameplay=False, extra_mods=(), orders=False, crystal=False):
    if crystal:
        assert version in ("26.2", "26.3"), "The crystal cycle targets the 26.2+ Gui API"
    hook = "GameplayCrystal" if crystal else "GameplayOrders"
    orders = orders or crystal
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
    options = ("onboardAccessibility:false\ntutorialStep:none\nfullscreen:false\nmaxFps:60\nguiScale:2\n"
               "renderDistance:3\nsimulationDistance:5\nsoundCategory_master:0.0\n")
    if orders:
        # Uncapped and never paused, so the harness itself does not limit frame timing.
        options = (options.replace("maxFps:60", "maxFps:260")
                   + "enableVsync:false\npauseOnLostFocus:false\ninactivityFpsLimit:minimized\n")
    (profile / "options.txt").write_text(options)
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
    mixin_names = ["SmokeTickMixin"]
    if orders:
        listener = "net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl"
        (profile / "OrdersMixins.java").write_text(ORDERS_MIXINS.format(
            hook=hook, mc=names.cls(MC), tick=names.member(MC, "tick"), listener=names.cls(listener),
            send=names.member(listener, "send"),
            packet=names.cls("net.minecraft.network.protocol.Packet").replace(".", "/")))
        sources += [str(profile / "OrdersMixins.java"), str(ROOT / f"tools/validation/{hook}.java")]
        mixin_names += ["OrdersTickMixin", "OrdersPacketMixin"]
    elif gameplay:
        assert version == "26.3", "World integration currently targets the new 26.3 hand adapter"
        sources.append(str(ROOT / "tools/validation/Gameplay263.java"))
    java = Path(spec["java"])
    subprocess.run([str(java.with_name("javac.exe" if os.name == "nt" else "javac")), "-proc:none", "--release", "21",
                    "-cp", compile_cp, "-d", str(classes), *sources], check=True)
    compiled = profile / "validation-named.jar"
    with zipfile.ZipFile(compiled, "w") as archive:
        for cls in classes.rglob("*.class"):
            archive.write(cls, cls.relative_to(classes).as_posix())
    if orders and version.startswith("1."):
        remapped = profile / "validation-intermediary.jar"
        remap_to_intermediary(compiled, remapped, spec, profile)
        compiled = remapped
    with zipfile.ZipFile(compiled) as source_jar, zipfile.ZipFile(profile / "mods/herzium-validation.jar", "w") as archive:
        for entry in source_jar.namelist():
            if entry.endswith(".class"):
                archive.writestr(entry, source_jar.read(entry))
        archive.writestr("fabric.mod.json", json.dumps({"schemaVersion": 1, "id": "herzium_validation",
                          "version": "1.0.0", "environment": "client", "mixins": ["smoke.mixins.json"]}))
        archive.writestr("smoke.mixins.json", json.dumps({"required": True, "package": "herzium.validation.mixin",
                          "compatibilityLevel": "JAVA_21", "client": mixin_names}))
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
        "herzium.smoke.gameplay": str(gameplay or orders).lower(),
        "herzium.smoke.gameplayClass": f"herzium.validation.{hook}" if orders else "herzium.validation.Gameplay263",
        "herzium.smoke.phaseSeconds": "420" if orders else "90",
        "herzium.orders.keyPress": names.member("net.minecraft.client.KeyboardHandler", "keyPress"),
        "herzium.orders.onCreate": names.member("net.minecraft.client.gui.screens.worldselection.CreateWorldScreen", "onCreate"),
        "herzium.orders.report": str(profile / "orders-report.json"),
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
            code = process.wait(timeout=600 if orders else 150)
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
    # A fresh install starts with every burst option on.
    assert config["splitBursts"] and config["strictActionOrder"] and config["offhandSync"], config
    for target, handler in [(targets[4], "renderVanillaResolvableHotbarInput"),
                            (targets[5], "replaceVisibleItemImmediately")]:
        exported = profile / ".mixin.out/class" / (names.cls(target).replace(".", "/") + ".class")
        bytecode = subprocess.check_output([str(java.with_name("javap.exe" if os.name == "nt" else "javap")),
                                            "-p", "-c", str(exported)], text=True, encoding="utf-8")
        assert re.search(r"invoke\w+.*herzium\$" + handler, bytecode), f"Optional hook did not inject: {handler}"
    assert "Can't open the resource index" not in text and "Couldn't set icon" not in text, "Broken validation assets"
    result.update(runtime="PASS", optional_injections="PASS", gameplay=gameplay,
                  extra_mods=[p.name for p in extra_mods], exit_code=code, log=str(log))
    if orders:
        result["orders_report"] = str(profile / "orders-report.json")
    (profile / "result.json").write_text(json.dumps(result, indent=2))
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("versions", nargs="+", choices=["1.21.10", "1.21.11", "26.1", "26.1.1", "26.1.2", "26.2", "26.3"])
    parser.add_argument("--gameplay", action="store_true", help="Also test a fresh 26.3 world and the new hand extraction adapter")
    parser.add_argument("--mods", nargs="*", type=Path, default=[], help="Additional mod JARs to copy into each isolated profile")
    parser.add_argument("--orders", action="store_true",
                        help="Create a world and drive the three selection orders through the real input path")
    parser.add_argument("--crystal", action="store_true",
                        help="26.2+: time the obsidian and end-crystal placement cycle under each order")
    args = parser.parse_args()
    output = ROOT / "tmp" / "release-audit" / time.strftime("smoke-%Y%m%d-%H%M%S")
    output.mkdir(parents=True)
    results = [smoke(version, output, args.gameplay, args.mods, args.orders, args.crystal)
               for version in args.versions]
    (output / "results.json").write_text(json.dumps(results, indent=2))
    print(f"PASS: {len(results)} release clients. Report: {output / 'results.json'}")


if __name__ == "__main__":
    main()
