# PasswdGen — produkcyjny klucz podpisujący i OTA

## Decyzja architektoniczna

Docelowy identyfikator aplikacji zostaje zamrożony jako:

```text
com.blackserv.passwdgen
```

Nie wolno go zmienić po pierwszym wydaniu produkcyjnym. Każda aktualizacja OTA musi mieć wyższy `versionCode` i być podpisana tym samym certyfikatem.

## Docelowy certyfikat produkcyjny

Docelowy fingerprint SHA-256 certyfikatu podpisującego:

```text
4B:BE:62:F2:BA:D5:73:3F:CC:15:85:ED:6F:E4:37:BE:DE:B1:81:FF:B3:63:7A:0A:06:71:38:D2:13:1C:90:B1
```

Wartość znormalizowana używana przez workflowy:

```text
4bbe62f2bad5733fcc1585ed6fe437bedeb181ffb3637a0a067138d2131c90b1
```

Fingerprint certyfikatu publicznego nie jest sekretem. Workflow `Android signing preflight` oraz workflow wydania OTA wymagają dokładnie tego certyfikatu i zatrzymają proces przy próbie użycia innego klucza.

## Zasada bezpieczeństwa

Klucz produkcyjny powstaje wyłącznie lokalnie na zaufanym komputerze. Nie generujemy go w GitHub Actions, nie wysyłamy przez czat i nigdy nie zapisujemy w repozytorium.

Skrypty używają JDK `keytool` i przekazują hasło przez chronioną zmienną środowiskową procesu (`-storepass:env` oraz `-keypass:env`), zamiast umieszczać hasło w argumentach polecenia. Tworzony certyfikat RSA 4096/SHA-256 jest ważny przez 36 500 dni, czyli około 100 lat.

## Wymagania

- JDK 17 z poleceniem `keytool` dostępnym w `PATH`,
- zaufany komputer bez aktywnego zdalnego pulpitu lub współdzielenia ekranu,
- bezpieczny menedżer haseł,
- dwa niezależne nośniki na zaszyfrowane kopie offline.

## Windows — rekomendowana ścieżka

Uruchom PowerShell w katalogu repozytorium:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\create-release-keystore.ps1
```

Domyślny katalog wynikowy:

```text
Dokumenty\PasswdGen-Signing
```

Skrypt:

- wymaga hasła o długości co najmniej 16 znaków,
- używa tego samego hasła dla magazynu JKS i prywatnego klucza,
- ogranicza ACL katalogu do bieżącego konta Windows,
- nie nadpisuje istniejącego klucza,
- tworzy JKS, jednowierszowy Base64, publiczny certyfikat i raport fingerprintu SHA-256,
- nie zapisuje hasła w żadnym pliku.

## Linux lub macOS

```bash
chmod +x scripts/create-release-keystore.sh
./scripts/create-release-keystore.sh
```

Domyślny katalog wynikowy:

```text
~/PasswdGen-Signing
```

Można wskazać inny katalog jako pierwszy argument.

## Artefakty lokalne

W katalogu wynikowym powstaną:

- `passwdgen-release.jks` — najważniejszy prywatny magazyn klucza,
- `passwdgen-release.jks.base64.txt` — wartość sekretu GitHub,
- `passwdgen-release-cert.der` — publiczny certyfikat,
- `release-signing-info.txt` — alias, fingerprint certyfikatu i sumy kontrolne.

Samodzielny generator użyty przy pierwszym utworzeniu klucza może zapisać plik Base64 jako `passwdgen-release-base64.txt` oraz certyfikat jako `passwdgen-release-cert.pem`. Są to równoważne dane dla tego samego JKS; do sekretu `ANDROID_KEYSTORE_BASE64` należy użyć całej jednowierszowej zawartości właściwego pliku Base64.

Pliki JKS i Base64 są objęte regułami `.gitignore`, ale nadal nie należy tworzyć ich wewnątrz repozytorium.

## Kopie bezpieczeństwa

Przed dodaniem sekretów:

1. Zapisz hasło w zaufanym menedżerze haseł.
2. Skopiuj `passwdgen-release.jks` i raport fingerprintu na dwa niezależne, zaszyfrowane nośniki.
3. Co najmniej jeden nośnik przechowuj offline i poza komputerem roboczym.
4. Porównaj SHA-256 kopii JKS z wartością zapisaną w raporcie.
5. Nie przechowuj hasła i wszystkich kopii klucza w tej samej lokalizacji.

Utrata klucza oznacza utratę możliwości płynnego aktualizowania istniejących instalacji poza mechanizmem rotacji oferowanym przez sklep. Ujawnienie klucza umożliwia podszywanie się pod wydawcę aplikacji.

## Sekrety GitHub Actions

W repozytorium otwórz:

```text
Settings → Secrets and variables → Actions → New repository secret
```

Dodaj dokładnie cztery sekrety:

| Nazwa | Wartość |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | cała jednowierszowa zawartość pliku Base64 wygenerowanego z `passwdgen-release.jks` |
| `ANDROID_KEYSTORE_PASSWORD` | hasło podane lokalnemu skryptowi |
| `ANDROID_KEY_ALIAS` | `passwdgen-release` |
| `ANDROID_KEY_PASSWORD` | to samo hasło |

Nie wklejaj tych wartości do Issue, komentarza PR, logu, pliku `.env`, `gradle.properties` ani `keystore.properties` w repozytorium.

## Preflight po dodaniu sekretów

1. Otwórz workflow `Android signing preflight`.
2. Uruchom `Run workflow` dla gałęzi `agent/android-vault-mvp`.
3. Oczekiwany wynik: `PASS`.
4. Bezpieczny raport musi wskazać signer SHA-256 `4bbe62f2bad5733fcc1585ed6fe437bedeb181ffb3637a0a067138d2131c90b1`.
5. Każda inna wartość musi zatrzymać proces.

Preflight nie tworzy taga ani GitHub Release i nie publikuje APK.

## Pierwsze wydanie

Pierwszy tag wolno utworzyć dopiero po:

- zielonym preflightcie podpisu,
- fizycznym teście instalacji podpisanego APK,
- zapisaniu testowych wpisów sejfu,
- przygotowaniu wersji N+1 z większym `versionCode`,
- potwierdzeniu aktualizacji N → N+1 oraz odszyfrowania sejfu.

PR pozostaje Draft do czasu zakończenia tych testów. Nie tworzymy produkcyjnego taga na potrzeby samego sprawdzenia konfiguracji.
