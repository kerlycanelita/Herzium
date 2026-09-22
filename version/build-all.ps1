$ErrorActionPreference = 'Stop'

$targets = @(
    @{ Minecraft = '1.21'; ModMenu = '11.0.4' },
    @{ Minecraft = '1.21.1'; ModMenu = '11.0.4' },
    @{ Minecraft = '1.21.2'; ModMenu = '12.0.1' },
    @{ Minecraft = '1.21.3'; ModMenu = '12.0.1' },
    @{ Minecraft = '1.21.4'; ModMenu = '13.0.4' },
    @{ Minecraft = '1.21.5'; ModMenu = '14.0.2' },
    @{ Minecraft = '1.21.6'; ModMenu = '15.0.2' },
    @{ Minecraft = '1.21.7'; ModMenu = '15.0.2' },
    @{ Minecraft = '1.21.8'; ModMenu = '15.0.2' },
    @{ Minecraft = '1.21.9'; ModMenu = '16.0.1' },
    @{ Minecraft = '1.21.10'; ModMenu = '16.0.1' },
    @{ Minecraft = '1.21.11'; ModMenu = '17.0.0' },
    @{ Minecraft = '26.1'; ModMenu = '18.0.0' },
    @{ Minecraft = '26.1.1'; ModMenu = '18.0.0' },
    @{ Minecraft = '26.1.2'; ModMenu = '18.0.0' },
    @{ Minecraft = '26.2'; ModMenu = '20.0.1' }
)

# Every target runs its Gradle daemon on Java 25; the 1.21.x ones compile at
# release 21 through a toolchain Gradle provisions itself (see the foojay
# resolver in settings.gradle). The daemon home used to name one exact install
# directory, which stopped existing the first time the machine's JDK was
# updated and took every 26.x build down with it. Resolve it instead: JAVA_HOME
# first, then whatever `java` is on PATH, then the newest jdk-25 under the usual
# install roots.
function Resolve-Java25Home {
    $candidates = @()
    if ($env:JAVA_HOME) { $candidates += $env:JAVA_HOME }
    $onPath = Get-Command java -ErrorAction SilentlyContinue
    if ($onPath) { $candidates += (Split-Path (Split-Path $onPath.Source -Parent) -Parent) }
    $candidates += Get-ChildItem -Path 'C:\Program Files\Java', 'C:\Program Files\Eclipse Adoptium', 'C:\Program Files\Microsoft' -Directory -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -match 'jdk-?25' } |
        Sort-Object Name -Descending |
        ForEach-Object { $_.FullName }

    foreach ($candidate in $candidates) {
        $release = Join-Path $candidate 'release'
        if (-not (Test-Path (Join-Path $candidate 'bin\javac.exe'))) { continue }
        if (Test-Path $release) {
            $version = Select-String -Path $release -Pattern '^JAVA_VERSION="25' -Quiet
            if (-not $version) { continue }
        }
        return $candidate
    }

    throw 'No Java 25 JDK found. Install one or set JAVA_HOME to it before building the 26.x targets.'
}

$javaHome = Resolve-Java25Home
Write-Host "Using Java 25 from $javaHome."

foreach ($target in $targets) {
    Write-Host "Building Herzium for Minecraft $($target.Minecraft)..."
    $projectDirectory = if ($target.Minecraft.StartsWith('26.')) {
        "$PSScriptRoot\official26"
    } else {
        $PSScriptRoot
    }
    $loomVersion = if ($target.Minecraft.StartsWith('26.')) {
        '1.17.17'
    } else {
        '1.16.0-alpha.10'
    }
    $herziumGradleArguments = @(
        '-p',
        $projectDirectory,
        'clean',
        'build',
        "-Pminecraft_version=$($target.Minecraft)",
        "-Pmodmenu_version=$($target.ModMenu)",
        "-Ploom_version=$loomVersion",
        "-Dorg.gradle.java.home=$javaHome",
        '--console=plain'
    )
    & "$PSScriptRoot\..\gradlew.bat" @herziumGradleArguments
    if ($LASTEXITCODE -ne 0) {
        throw "Herzium build failed for Minecraft $($target.Minecraft)."
    }
}
