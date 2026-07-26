# PasswdGen

Androidowy generator haseł i lokalny sejf danych logowania.

## Status

**MVP 0.1.0 — kod bazowy.** Projekt nie jest jeszcze gotowy do publikacji w Google Play. Przed wydaniem wymagane są testy na fizycznych urządzeniach, audyt kryptografii, podpis release, grafiki sklepu i finalna decyzja dotycząca identyfikatora pakietu.

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

Szczegóły: [`docs/SECURITY.md`](docs/SECURITY.md).

## Wymagania

- Android Studio obsługujące Android Gradle Plugin 8.12,
- JDK 17,
- Android SDK 36,
- Gradle 8.13.

Repozytorium nie zawiera jeszcze binarnego `gradle-wrapper.jar`, ponieważ obecne środowisko wykonawcze nie miało narzędzia Gradle. Po pierwszym klonowaniu należy jednorazowo wykonać:

```bash
gradle wrapper --gradle-version 8.13
./gradlew test
./gradlew lintDebug
./gradlew assembleDebug
```

Wygenerowany wrapper powinien zostać dodany do repozytorium przed pierwszym PR-em wydaniowym.

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
