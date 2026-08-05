# Model bezpieczeństwa

## Cele

1. Hasła i loginy nie opuszczają urządzenia w MVP.
2. Dane spoczynkowe są zaszyfrowane.
3. Klucz szyfrujący nie jest zapisany w kodzie, bazie ani preferencjach.
4. Dostęp do klucza wymaga silnej biometrii lub kodu urządzenia.
5. Aplikacja nie loguje sekretów.

## Kryptografia

- algorytm: AES-256-GCM,
- losowy IV generowany przez implementację `Cipher`,
- tag uwierzytelniający: 128 bitów,
- klucz: Android Keystore,
- wymagane uwierzytelnienie: `BIOMETRIC_STRONG` lub `DEVICE_CREDENTIAL`,
- ważność uwierzytelnienia dla operacji kluczem: 300 sekund,
- AAD: identyfikator UUID rekordu.

Każdy rekord jest szyfrowany oddzielnie. SQLite nie zawiera jawnej nazwy usługi, domeny, loginu, hasła ani notatek.

## Widoczne metadane

Szyfrowanie zawartości nie ukrywa wszystkiego. Osoba mająca dostęp do pliku bazy może poznać:

- liczbę rekordów,
- losowe identyfikatory UUID,
- czas utworzenia i ostatniej aktualizacji rekordu,
- długość szyfrogramu, a więc przybliżoną wielkość zaszyfrowanej zawartości,
- fakt korzystania z aplikacji PasswdGen.

W MVP nie stosujemy dopełniania rekordów ani ukrywania wzorców czasowych. Nie wolno opisywać tej konstrukcji jako rozwiązania ukrywającego metadane.

## Ochrona interfejsu

- `FLAG_SECURE` blokuje zrzuty ekranu i podgląd ekranu w przełączniku aplikacji,
- sejf jest blokowany w `onStop`,
- hasła są domyślnie zamaskowane,
- schowek jest oznaczany jako wrażliwy i czyszczony po 60 sekundach, o ile nadal zawiera skopiowaną wartość.

Oznaczenie schowka jako wrażliwego ogranicza podgląd systemowy, ale nie daje absolutnej ochrony na przejętym urządzeniu. Złośliwa aplikacja z wysokimi uprawnieniami lub usługa dostępności nadal może stanowić zagrożenie.

## Backup

Backup Androida i transfer danych są wyłączone. Klucz Keystore jest związany z urządzeniem, dlatego przywrócenie samej bazy na innym urządzeniu nie dałoby dostępu do danych.

## Dane w pamięci

Po odblokowaniu wpisy są odszyfrowywane do pamięci procesu, aby można było je wyświetlić i przeszukiwać. Zablokowanie sejfu usuwa je ze stanu aplikacji, ale środowisko zarządzanej pamięci JVM/ART nie gwarantuje natychmiastowego nadpisania wszystkich kopii obiektów `String`.

## Zagrożenia poza zakresem MVP

- urządzenie z rootem lub złośliwym firmware,
- aktywny malware z uprawnieniami dostępności,
- przejęta sesja urządzenia po prawidłowym odblokowaniu,
- fizyczna analiza pamięci RAM,
- błędy producenta w implementacji Keystore/TEE,
- odzyskanie danych po utracie urządzenia — brak zaszyfrowanego eksportu awaryjnego.

## Wymagane przed publikacją

- testy na co najmniej jednym urządzeniu Google Pixel i jednym urządzeniu Samsung,
- test unieważnienia klucza po zmianie blokady ekranu i biometrii,
- test zachowania przy pełnej i uszkodzonej bazie,
- niezależny przegląd implementacji kryptograficznej,
- testy backupu i migracji urządzenia,
- analiza OWASP MASVS dla obszaru storage, crypto i auth,
- przygotowanie procedury zaszyfrowanego eksportu i odzyskiwania.
