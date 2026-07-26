# PasswdGen

Androidowy generator haseł i lokalny sejf danych logowania.

## Status

**MVP 0.1.0 — kod bazowy.** Projekt nie jest jeszcze gotowy do publikacji w Google Play. Przed wydaniem wymagane są pełna kompilacja, testy na fizycznych urządzeniach, audyt kryptografii, podpis release, grafiki sklepu i finalna decyzja dotycząca identyfikatora pakietu.

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
- Android SDK 36,
- Gradle 8.13.

Repozytorium nie zawiera jeszcze binarnego `gradle-wrapper.jar`, ponieważ bieżące środowisko wykonawcze nie mogło pobrać oficjalnej dystrybucji. Nie należy kopiować przypadkowego JAR-a ani generować wrappera z niezweryfikowanej instalacji.

Na zaufanej maszynie z Linuksem i dostępem do internetu należy wykonać:

```bash
bash scripts/bootstrap-gradle-wrapper.sh
./gradlew --version
./gradlew test lintDebug assembleDebug
```

Skrypt pobiera oficjalną dystrybucję Gradle 8.13, sprawdza jej SHA-256, generuje wrapper w izolowanym minimalnym projekcie, zapisuje checksumę dystrybucji w konfiguracji i sprawdza checksumę `gradle-wrapper.jar`. Wygenerowane pliki wrappera powinny zostać dodane do tej samej gałęzi dopiero po przejściu weryfikacji.

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
