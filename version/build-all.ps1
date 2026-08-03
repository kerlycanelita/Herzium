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
        '--console=plain'
    )
    if ($target.Minecraft.StartsWith('26.')) {
        $herziumGradleArguments += '-Dorg.gradle.java.home=C:\Program Files\Java\jdk-25.0.2'
    }
    & "$PSScriptRoot\..\gradlew.bat" @herziumGradleArguments
    if ($LASTEXITCODE -ne 0) {
        throw "Herzium build failed for Minecraft $($target.Minecraft)."
    }
}
