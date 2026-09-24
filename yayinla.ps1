# Pengu Launcher (Android) yayin scripti
#
# Kullanim (PowerShell, bu klasorde):
#   .\yayinla.ps1                          -> surumu 1 arttirir (1.0.0 -> 1.0.1), derler
#   .\yayinla.ps1 -Surum 1.2.0 -Notlar "Yeni kozmetikler eklendi"
#   .\yayinla.ps1 -Istege                  -> oyuncu "Sonra" diyebilir (varsayilan: zorunlu)
#
# Cikti: <mc-launcher>\dist\android\ icinde
#   Pengu-Launcher-<surum>.apk  +  latest-android.json
# Ikisini de sitede /indir/ klasorune yukle. Acik olan butun launcher'lar
# bir sonraki acilista guncellemeyi gorup kendini gunceller.

param(
    [string]$Surum = "",
    [string]$Notlar = "",
    [switch]$Istege,
    [string]$McLauncher = "$env:USERPROFILE\OneDrive\Masaüstü\Masaüstü\mc-launcher",
    [string]$SiteAdresi = "https://penguscraft.com.tr/indir"
)

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"  # PS 5.1 indirmeleri cok yavaslatiyor
$Kok = $PSScriptRoot
$Assets = Join-Path $Kok "app_pojavlauncher\src\main\assets\pengu"

if (-not (Test-Path (Join-Path $Kok "keystore.properties"))) {
    throw "keystore.properties yok. Imza anahtari olmadan guncelleme yayinlanamaz (yedekten geri yukle)."
}

# 1) PC launcher'daki bizim modlarimizi ve kaynak paketlerini esitle
foreach ($cift in @(@("assets\mods", "mods", "*.jar"), @("assets\packs", "packs", "*.zip"))) {
    $kaynak = Join-Path $McLauncher $cift[0]
    $hedef = Join-Path $Assets $cift[1]
    if (-not (Test-Path $kaynak)) { throw "Bulunamadi: $kaynak" }
    New-Item -ItemType Directory -Force $hedef | Out-Null
    Get-ChildItem $hedef -Filter $cift[2] | Remove-Item -Force
    Copy-Item (Join-Path $kaynak $cift[2]) $hedef
    Write-Host "Esitlendi: $($cift[0])"
}

# 1b) APK'ya gomulu modlar: kurulu gelsin, ilk OYNA'da internetten inmesin.
#      Liste PenguConfig.MODLAR ile ayni olmali.
$hazir = Join-Path $Assets "mods-hazir"
New-Item -ItemType Directory -Force $hazir | Out-Null
Get-ChildItem $hazir -Filter *.jar | Remove-Item -Force
$modlar = @("P7dR8mSH", "9eGKb6K1", "U7KwGAnT", "AANobbMI", "uXXizFIs", "nmDcB62a", "gvQqBUqZ", "5ZwdcRci", "NNAgCjsB", "LQ3K71Q1")
$sorgu = "game_versions=" + [uri]::EscapeDataString('["1.21"]') + "&loaders=" + [uri]::EscapeDataString('["fabric"]')
$ua = @{ "User-Agent" = "penguscraft-launcher-android/1.0 (oyna.penguscraft.com)" }
foreach ($id in $modlar) {
    $surumler = Invoke-RestMethod -Headers $ua "https://api.modrinth.com/v2/project/$id/version?$sorgu"
    $sec = ($surumler | Where-Object { $_.version_type -eq "release" } | Select-Object -First 1)
    if (-not $sec) { $sec = $surumler | Select-Object -First 1 }
    $dosya = ($sec.files | Where-Object { $_.primary } | Select-Object -First 1)
    if (-not $dosya) { $dosya = $sec.files[0] }
    Invoke-WebRequest -UseBasicParsing -Headers $ua $dosya.url -OutFile (Join-Path $hazir $dosya.filename)
    Write-Host "Gomulu mod: $($dosya.filename)"
}

# 2) Surum numarasi
$surumDosya = Join-Path $Kok "pengu-surum.properties"
$icerik = Get-Content $surumDosya
$kod = [int](($icerik | Where-Object { $_ -match '^versionCode=' }) -replace 'versionCode=', '')
$ad = ($icerik | Where-Object { $_ -match '^versionName=' }) -replace 'versionName=', ''
if (-not $Surum) {
    $p = $ad.Split('.')
    $p[$p.Length - 1] = [string]([int]$p[$p.Length - 1] + 1)
    $Surum = $p -join '.'
}
$kod++
$yeni = $icerik | ForEach-Object {
    if ($_ -match '^versionCode=') { "versionCode=$kod" }
    elseif ($_ -match '^versionName=') { "versionName=$Surum" }
    else { $_ }
}
[IO.File]::WriteAllLines($surumDosya, $yeni)
Write-Host "Surum: $Surum (versionCode $kod)"

# 3) Derle
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"
# Gradle uyarilari stderr'e yaziyor; PowerShell 5.1 bunlari hata sanmasin
$ErrorActionPreference = "Continue"
& (Join-Path $Kok "gradlew.bat") -p $Kok :app_pojavlauncher:assembleRelease
$ErrorActionPreference = "Stop"
if ($LASTEXITCODE -ne 0) { throw "Derleme basarisiz (surum dosyasi $Surum olarak kaldi, tekrar calistirabilirsin)" }

# 4) Cikti + guncelleme bilgisi
$cikti = Join-Path $McLauncher "dist\android"
New-Item -ItemType Directory -Force $cikti | Out-Null
$apkAd = "Pengu-Launcher-$Surum.apk"
$apk = Join-Path $cikti $apkAd
Copy-Item (Join-Path $Kok "app_pojavlauncher\build\outputs\apk\release\app_pojavlauncher-release.apk") $apk -Force
$sha = (Get-FileHash $apk -Algorithm SHA256).Hash.ToLower()

$bilgi = [ordered]@{
    versionCode = $kod
    versionName = $Surum
    url         = "$SiteAdresi/$apkAd"
    sha256      = $sha
    zorunlu     = (-not $Istege.IsPresent)
    notlar      = $Notlar
}
$json = $bilgi | ConvertTo-Json
# BOM'suz UTF-8: uygulamadaki JSON okuyucu BOM'u kabul etmiyor
[IO.File]::WriteAllText((Join-Path $cikti "latest-android.json"), $json, (New-Object Text.UTF8Encoding($false)))

Write-Host ""
Write-Host "Hazir! Su iki dosyayi sitede /indir/ klasorune yukle:"
Write-Host "  $apk"
Write-Host "  $(Join-Path $cikti 'latest-android.json')"
