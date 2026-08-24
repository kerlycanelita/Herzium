<#
.SYNOPSIS
    Reorganiza y limpia el repositorio Herzium.

.DESCRIPTION
    Ejecutar desde cualquier sitio; el script se ubica solo respecto a su
    propia ruta (tools\organize-repo.ps1 dentro del repo).

    Acciones:
      1. Borra arboles de carpetas vacias que quedaron de extracciones
         antiguas (net\minecraft\..., version\1.21.11\src, etc.).
      2. Borra artefactos temporales no versionados (tmp\).
      3. Mueve documentacion y material de release a docs\.
      4. Borra salidas de compilacion (build\, .gradle\, version\*\build\).
      5. Deja run\ intacto salvo que se pase -CleanRunDir.

    Usa "git mv" / "git rm" cuando el archivo esta versionado, para no
    perder el historial.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File .\tools\organize-repo.ps1 -WhatIf

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File .\tools\organize-repo.ps1
#>

[CmdletBinding(SupportsShouldProcess = $true)]
param(
    # Borra tambien run\ (mundo de pruebas, logs y mods descargados).
    [switch] $CleanRunDir,

    # No toca las carpetas build\ / .gradle\ (compilacion incremental).
    [switch] $KeepBuildOutput
)

$ErrorActionPreference = 'Stop'

# El script vive en <repo>\tools\, asi que la raiz es su carpeta padre.
$repoRoot = Split-Path -Parent $PSScriptRoot
if (-not (Test-Path (Join-Path $repoRoot 'settings.gradle'))) {
    throw "No se encontro settings.gradle en '$repoRoot'. Coloca este script en <repo>\tools\."
}
Set-Location $repoRoot
Write-Host "Repositorio: $repoRoot" -ForegroundColor Cyan

$hasGit = (Test-Path (Join-Path $repoRoot '.git')) -and
          ($null -ne (Get-Command git -ErrorAction SilentlyContinue))
if (-not $hasGit) {
    Write-Warning "Git no disponible: se usaran operaciones de archivo normales."
}

function Test-Tracked {
    param([string] $RelativePath)
    if (-not $hasGit) { return $false }
    # Windows PowerShell 5.1: con $ErrorActionPreference = 'Stop', cualquier
    # linea que un ejecutable nativo escriba en stderr se convierte en error
    # terminante aunque se redirija. "git ls-files --error-unmatch" escribe en
    # stderr cuando la ruta no esta versionada -- que es justo el caso normal
    # aqui. Se usa la forma sin --error-unmatch, que no escribe en stderr:
    # devuelve la lista de archivos versionados bajo la ruta, o nada.
    $tracked = & git ls-files -- $RelativePath
    return ($null -ne $tracked -and $tracked.Count -gt 0)
}

function Remove-RepoPath {
    param([string] $RelativePath, [string] $Reason)

    $full = Join-Path $repoRoot $RelativePath
    if (-not (Test-Path $full)) { return }

    if ($PSCmdlet.ShouldProcess($RelativePath, "Eliminar ($Reason)")) {
        if (Test-Tracked $RelativePath) {
            git rm -r -q --ignore-unmatch -- $RelativePath
        }
        if (Test-Path $full) {
            Remove-Item -LiteralPath $full -Recurse -Force
        }
        Write-Host "  - eliminado  $RelativePath   ($Reason)"
    }
}

function Move-RepoPath {
    param([string] $From, [string] $To)

    $fullFrom = Join-Path $repoRoot $From
    $fullTo   = Join-Path $repoRoot $To
    if (-not (Test-Path $fullFrom)) { return }
    if (Test-Path $fullTo) {
        Write-Warning "  ! destino ya existe, se omite: $To"
        return
    }

    if ($PSCmdlet.ShouldProcess("$From -> $To", 'Mover')) {
        $parent = Split-Path -Parent $fullTo
        if (-not (Test-Path $parent)) {
            New-Item -ItemType Directory -Path $parent -Force | Out-Null
        }
        if (Test-Tracked $From) {
            git mv -- $From $To
        } else {
            Move-Item -LiteralPath $fullFrom -Destination $fullTo -Force
        }
        Write-Host "  > movido    $From -> $To"
    }
}

# ---------------------------------------------------------------------------
Write-Host "`n[1/5] Carpetas vacias heredadas" -ForegroundColor Yellow
# Arbol net\minecraft\... : solo directorios, sin un solo archivo dentro.
Remove-RepoPath 'net'                        'arbol de carpetas vacias'
Remove-RepoPath 'version\1.21.11\src'        'overlay vacio'
Remove-RepoPath 'version\src\client\classic\dev\zymekoh\herzium\input' 'overlay vacio'

# ---------------------------------------------------------------------------
Write-Host "`n[2/5] Temporales" -ForegroundColor Yellow
Remove-RepoPath 'tmp'                        'logs y scratch de builds antiguos'

# ---------------------------------------------------------------------------
Write-Host "`n[3/5] Documentacion y material de release -> docs\" -ForegroundColor Yellow
$docs = Join-Path $repoRoot 'docs'
if (-not (Test-Path $docs)) {
    if ($PSCmdlet.ShouldProcess('docs', 'Crear carpeta')) {
        New-Item -ItemType Directory -Path $docs | Out-Null
    }
}
Move-RepoPath 'MODRINTH.md'   'docs\MODRINTH.md'
Move-RepoPath 'checksums.txt' 'docs\checksums.txt'
Move-RepoPath 'evidence'      'docs\evidence'
Move-RepoPath 'media'         'docs\media'

Write-Host "  ! Recuerda: checksums.txt sigue listando jars 1.8.7 (regeneralo para 1.9.3)."

# ---------------------------------------------------------------------------
Write-Host "`n[4/5] Salidas de compilacion" -ForegroundColor Yellow
if ($KeepBuildOutput) {
    Write-Host "  (omitido por -KeepBuildOutput)"
} else {
    Remove-RepoPath 'build'                      'salida de Gradle'
    Remove-RepoPath '.gradle'                    'cache de Gradle'
    Remove-RepoPath 'version\.gradle'            'cache de Gradle'
    Remove-RepoPath 'version\official26\.gradle' 'cache de Gradle'
    Remove-RepoPath 'version\1.21.11\.gradle'    'cache de Gradle'
    Get-ChildItem -Path (Join-Path $repoRoot 'version') -Directory -ErrorAction SilentlyContinue |
        ForEach-Object {
            $rel = "version\$($_.Name)\build"
            Remove-RepoPath $rel 'salida de Gradle por version'
        }
}

# ---------------------------------------------------------------------------
Write-Host "`n[5/5] Entorno de desarrollo" -ForegroundColor Yellow
if ($CleanRunDir) {
    Remove-RepoPath 'run' 'entorno de pruebas de Loom'
} else {
    Write-Host "  (run\ conservado; usa -CleanRunDir para borrarlo)"
}

Write-Host "`nListo. Revisa el resultado con:  git status" -ForegroundColor Green
