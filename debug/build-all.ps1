$ErrorActionPreference = 'Stop'

Write-Host 'Building Herzium Debug for Minecraft 1.21.11...'
& "$PSScriptRoot\..\gradlew.bat" -p "$PSScriptRoot\version" clean build --console=plain
if ($LASTEXITCODE -ne 0) {
    throw 'Herzium Debug build failed for Minecraft 1.21.11.'
}

$targets = @(
    @{ Minecraft = '26.1'; ModMenu = '18.0.0' },
    @{ Minecraft = '26.1.1'; ModMenu = '18.0.0' },
    @{ Minecraft = '26.1.2'; ModMenu = '18.0.0' },
    @{ Minecraft = '26.2'; ModMenu = '20.0.1' }
)

foreach ($target in $targets) {
    Write-Host "Building Herzium Debug for Minecraft $($target.Minecraft)..."
    $arguments = @(
        '-p',
        $PSScriptRoot,
        'clean',
        'build',
        "-Pminecraft_version=$($target.Minecraft)",
        "-Pmodmenu_version=$($target.ModMenu)",
        '--console=plain',
        '-Dorg.gradle.java.home=C:\Program Files\Java\jdk-25.0.2'
    )
    & "$PSScriptRoot\..\gradlew.bat" @arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Herzium Debug build failed for Minecraft $($target.Minecraft)."
    }
}
