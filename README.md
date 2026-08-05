# PasswdGen

Androidowy generator haseł i lokalny sejf danych logowania.

## Status

**MVP 0.1.0 — kompilowalny kod bazowy.** Testy jednostkowe, Android Lint, debug APK oraz niepodpisany release AAB przechodzą w CI dla Android SDK 36, JDK 17 i Gradle 8.13. Projekt nie jest jeszcze gotowy do publikacji w Google Play: przed wydaniem wymagane są testy na emulatorach i fizycznych urządzeniach, audyt kryptografii, procedura odzyskiwania sejfu, bezpieczny klucz podpisujący, materiały sklepu i finalna decyzja dotycząca identyfikatora pakietu.

## Funkcje MVP

- kryptograficzne generowanie haseł przez `SecureRandom`,
- długość 8–128 znaków,
- małe i wielkie litery, cyfry i znaki specjalne,
- opcjonalne pomijanie znaków podobnych,
- kopiowanie jako danych wrażliwych i automatyczne czyszczenie schowka,
- lokalny sejf: usługa, adres strony, login, hasło i notatki,
- szyfrowanie każdego wpisu AES-256-GCM,
- klucz w Android Keystore chroniony biometrią lub blokadą urządzenia,
- automatyczne blokowanie sejfu po opuszczeniu aplikacji,
- blokada zrzutów ekranu i podglądu w ostatnich aplikacjach,
- brak uprawnienia `INTERNET`, reklam, analityki i telemetrii,
- wyłączony backup systemowy danych sejfu.

## Architektura bezpieczeństwa

SQLite przechowuje tylko losowy identyfikator rekordu, zaszyfrowany ładunek, IV oraz znaczniki czasu. Nazwa witryny, login, hasło i notatki nie występują w bazie w postaci jawnej. Identyfikator rekordu jest używany jako AAD dla AES-GCM, co wiąże szyfrogram z konkretnym rekordem.

Dokumenty:

- [`docs/SECURITY.md`](docs/SECURITY.md) — model bezpieczeństwa,
- [`docs/ADR-001-CREDENTIAL-STRATEGY.md`](docs/ADR-001-CREDENTIAL-STRATEGY.md) — decyzja dotycząca Google Password Manager i Android Credential Provider,
- [`docs/PLAY_STORE.md`](docs/PLAY_STORE.md) — przygotowanie publikacji,
- [`docs/ROADMAP.md`](docs/ROADMAP.md) — kolejne etapy.

## Wymagania

- Android Studio obsługujące Android Gradle Plugin 8.12,
- JDK 17,
- Android SDK 36.

Repozytorium zawiera Gradle Wrapper 8.13. Jego JAR oraz dystrybucja są przypięte zweryfikowanymi sumami SHA-256, a CI sprawdza je przed wykonaniem kodu Gradle.

## Budowanie

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
./gradlew bundleRelease
```

`bundleRelease` tworzy niepodpisany AAB. Klucz upload nie jest i nie będzie przechowywany w repozytorium ani w artefaktach zwykłego CI.

Awaryjne odtworzenie wrappera na zaufanej maszynie linuksowej:

```bash
bash scripts/bootstrap-gradle-wrapper.sh
```

Skrypt pobiera oficjalną dystrybucję Gradle 8.13, sprawdza jej SHA-256, generuje wrapper w izolowanym minimalnym projekcie, zapisuje checksumę dystrybucji w konfiguracji i sprawdza checksumę `gradle-wrapper.jar`.

## Ważne ograniczenia MVP

- brak synchronizacji z chmurą,
- brak importu i eksportu,
- brak Credential Provider / Autofill,
- utrata lub unieważnienie klucza Android Keystore może uniemożliwić odszyfrowanie lokalnych danych,
- nie ma jeszcze procedury odzyskiwania — przed publikacją potrzebny jest zaszyfrowany eksport awaryjny.

## Identyfikator aplikacji

Aktualny `applicationId` to `com.blackserv.passwdgen`. Po publikacji w Google Play identyfikatora nie można zmienić dla istniejącej aplikacji, dlatego musi zostać formalnie zatwierdzony przed utworzeniem wpisu sklepowego.

## Licencja

Copyright © 2026. Wszystkie prawa zastrzeżone. Decyzja o ewentualnej licencji open-source zostanie podjęta przed publicznym wydaniem kodu.
