[CmdletBinding()]
param(
    [string]$OutputDirectory = "",
    [string]$Alias = "passwdgen-release",
    [string]$DistinguishedName = "CN=PasswdGen Release, O=BlackServ, C=PL"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function ConvertFrom-SecureStringPlain {
    param([Parameter(Mandatory = $true)][Security.SecureString]$Value)

    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($Value)
    try {
        return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    }
    finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
    }
}

function Read-ConfirmedPassword {
    while ($true) {
        $firstSecure = Read-Host "Podaj silne haslo klucza (minimum 16 znakow)" -AsSecureString
        $secondSecure = Read-Host "Powtorz haslo" -AsSecureString
        $first = ConvertFrom-SecureStringPlain $firstSecure
        $second = ConvertFrom-SecureStringPlain $secondSecure

        if ($first.Length -lt 16) {
            Write-Warning "Haslo musi miec co najmniej 16 znakow."
            continue
        }
        if ($first -cne $second) {
            Write-Warning "Hasla nie sa identyczne."
            continue
        }
        return $first
    }
}

function Find-Keytool {
    $candidates = @()

    $command = Get-Command keytool.exe -ErrorAction SilentlyContinue
    if ($command) {
        $candidates += $command.Source
    }

    $command = Get-Command keytool -ErrorAction SilentlyContinue
    if ($command) {
        $candidates += $command.Source
    }

    if ($env:JAVA_HOME) {
        $candidates += (Join-Path $env:JAVA_HOME "bin\keytool.exe")
        $candidates += (Join-Path $env:JAVA_HOME "bin/keytool")
    }

    if ([Environment]::OSVersion.Platform -eq [PlatformID]::Win32NT) {
        $candidates += "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe"

        $adoptiumRoots = Get-ChildItem "C:\Program Files\Eclipse Adoptium" -Directory -ErrorAction SilentlyContinue |
            Sort-Object Name -Descending
        foreach ($root in $adoptiumRoots) {
            $candidates += (Join-Path $root.FullName "bin\keytool.exe")
        }
    }

    foreach ($candidate in ($candidates | Select-Object -Unique)) {
        if ($candidate -and (Test-Path -LiteralPath $candidate)) {
            return (Resolve-Path -LiteralPath $candidate).Path
        }
    }

    throw "Nie znaleziono keytool. Zainstaluj JDK 17 albo Android Studio."
}

$keytool = Find-Keytool

if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    $documents = [Environment]::GetFolderPath([Environment+SpecialFolder]::MyDocuments)
    if ([string]::IsNullOrWhiteSpace($documents)) {
        $documents = $HOME
    }
    $OutputDirectory = Join-Path $documents "PasswdGen-Signing"
}

$OutputDirectory = [IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null

$isWindows = [Environment]::OSVersion.Platform -eq [PlatformID]::Win32NT
if ($isWindows) {
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent().Name
    & icacls.exe $OutputDirectory /inheritance:r | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Nie udalo sie wylaczyc dziedziczenia ACL dla $OutputDirectory" }
    & icacls.exe $OutputDirectory /grant:r "${identity}:(OI)(CI)F" | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Nie udalo sie ograniczyc ACL dla $OutputDirectory" }
}

$keystorePath = Join-Path $OutputDirectory "passwdgen-release.jks"
$base64Path = Join-Path $OutputDirectory "passwdgen-release.jks.base64.txt"
$certificatePath = Join-Path $OutputDirectory "passwdgen-release-cert.der"
$metadataPath = Join-Path $OutputDirectory "release-signing-info.txt"

foreach ($path in @($keystorePath, $base64Path, $certificatePath, $metadataPath)) {
    if (Test-Path $path) {
        throw "Plik juz istnieje: $path. Przerwano, aby nie nadpisac klucza."
    }
}

$password = Read-ConfirmedPassword
$env:PASSWDGEN_SIGNING_PASSWORD = $password

try {
    $generateArgs = @(
        "-genkeypair",
        "-v",
        "-keystore", $keystorePath,
        "-storetype", "JKS",
        "-alias", $Alias,
        "-keyalg", "RSA",
        "-keysize", "4096",
        "-sigalg", "SHA256withRSA",
        "-validity", "36500",
        "-dname", $DistinguishedName,
        "-storepass:env", "PASSWDGEN_SIGNING_PASSWORD",
        "-keypass:env", "PASSWDGEN_SIGNING_PASSWORD"
    )
    & $keytool @generateArgs
    if ($LASTEXITCODE -ne 0) { throw "keytool nie utworzyl magazynu kluczy." }

    $exportArgs = @(
        "-exportcert",
        "-keystore", $keystorePath,
        "-storetype", "JKS",
        "-alias", $Alias,
        "-storepass:env", "PASSWDGEN_SIGNING_PASSWORD",
        "-file", $certificatePath
    )
    & $keytool @exportArgs
    if ($LASTEXITCODE -ne 0) { throw "Nie udalo sie wyeksportowac certyfikatu publicznego." }

    $keystoreBytes = [IO.File]::ReadAllBytes($keystorePath)
    $keystoreBase64 = [Convert]::ToBase64String($keystoreBytes)
    [IO.File]::WriteAllText($base64Path, $keystoreBase64, [Text.Encoding]::ASCII)

    $certificateSha256 = (Get-FileHash -Algorithm SHA256 -Path $certificatePath).Hash.ToLowerInvariant()
    $certificateFingerprint = (($certificateSha256.ToUpperInvariant() -split '(..)' | Where-Object { $_ }) -join ':')
    $keystoreSha256 = (Get-FileHash -Algorithm SHA256 -Path $keystorePath).Hash.ToLowerInvariant()
    $generatedUtc = [DateTime]::UtcNow.ToString("yyyy-MM-ddTHH:mm:ssZ")

    $metadata = @"
PasswdGen release signing identity
Generated UTC: $generatedUtc
Application ID: com.blackserv.passwdgen
Alias: $Alias
Certificate SHA-256: $certificateSha256
Certificate fingerprint SHA-256: $certificateFingerprint
Keystore SHA-256: $keystoreSha256
Validity days: 36500
"@
    [IO.File]::WriteAllText($metadataPath, $metadata, [Text.UTF8Encoding]::new($false))

    Write-Host ""
    Write-Host "Utworzono docelowy klucz podpisujacy PasswdGen." -ForegroundColor Green
    Write-Host "Katalog: $OutputDirectory"
    Write-Host "Fingerprint certyfikatu SHA-256: $certificateFingerprint"
    Write-Host ""
    Write-Host "Dodaj nastepujace GitHub Actions repository secrets:"
    Write-Host "ANDROID_KEYSTORE_BASE64 = cala zawartosc pliku $base64Path"
    Write-Host "ANDROID_KEYSTORE_PASSWORD = haslo wpisane przed chwila"
    Write-Host "ANDROID_KEY_ALIAS = $Alias"
    Write-Host "ANDROID_KEY_PASSWORD = to samo haslo"
    Write-Host ""
    Write-Host "Nie wysylaj pliku JKS ani hasla przez czat i nie dodawaj ich do repozytorium." -ForegroundColor Yellow
}
finally {
    Remove-Item Env:PASSWDGEN_SIGNING_PASSWORD -ErrorAction SilentlyContinue
    $password = $null
}
