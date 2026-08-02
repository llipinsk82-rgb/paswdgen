# PasswdGen — produkcyjny klucz podpisujący i OTA

## Decyzja architektoniczna

Docelowy identyfikator aplikacji zostaje zamrożony jako:

```text
com.blackserv.passwdgen
```

Nie wolno go zmienić po pierwszym wydaniu produkcyjnym. Każda aktualizacja OTA musi mieć wyższy `versionCode` i być podpisana tym samym certyfikatem.

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

Pliki JKS i Base64 są objęte regułami `.gitignore`, ale nadal nie należy tworzyć ich wewnątrz repozytorium.

## Kopie bezpieczeństwa

Przed dodaniem sekretów:

1. Zapisz hasło w zaufanym menedżerze haseł.
2. Skopiuj `passwdgen-release.jks` i `release-signing-info.txt` na dwa niezależne, zaszyfrowane nośniki.
3. Co najmniej jeden nośnik przechowuj offline i poza komputerem roboczym.
4. Porównaj SHA-256 kopii z wartością w `release-signing-info.txt`.
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
| `ANDROID_KEYSTORE_BASE64` | cała jednowierszowa zawartość `passwdgen-release.jks.base64.txt` |
| `ANDROID_KEYSTORE_PASSWORD` | hasło podane lokalnemu skryptowi |
| `ANDROID_KEY_ALIAS` | domyślnie `passwdgen-release` |
| `ANDROID_KEY_PASSWORD` | to samo hasło |

Nie wklejaj tych wartości do Issue, komentarza PR, logu, pliku `.env`, `gradle.properties` ani `keystore.properties` w repozytorium.

## Preflight po dodaniu sekretów

1. Otwórz workflow `Android signing preflight`.
2. Uruchom `Run workflow` dla gałęzi `agent/android-vault-mvp`.
3. Oczekiwany wynik: `PASS`.
4. Zachowaj fingerprint certyfikatu SHA-256 z bezpiecznego raportu.
5. Porównaj go z `release-signing-info.txt`.

Preflight nie tworzy taga ani GitHub Release i nie publikuje APK. Jeżeli fingerprinty są różne, zatrzymaj proces i nie twórz wydania.

## Pierwsze wydanie

Pierwszy tag wolno utworzyć dopiero po:

- zielonym preflightcie podpisu,
- fizycznym teście instalacji podpisanego APK,
- zapisaniu testowych wpisów sejfu,
- przygotowaniu wersji N+1 z większym `versionCode`,
- potwierdzeniu aktualizacji N → N+1 oraz odszyfrowania sejfu.

PR pozostaje Draft do czasu zakończenia tych testów. Nie tworzymy produkcyjnego taga na potrzeby samego sprawdzenia konfiguracji.
