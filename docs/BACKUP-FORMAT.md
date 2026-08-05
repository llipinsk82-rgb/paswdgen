# Zaszyfrowana kopia sejfu `.pgvault`

## Cel

Format `.pgvault` służy do bezpiecznego przenoszenia i odzyskiwania sejfu PasswdGen niezależnie od klucza Android Keystore konkretnego telefonu.

Plik można zapisać:

- w pamięci urządzenia,
- na pamięci USB,
- w Google Drive, OneDrive lub innym dostawcy widocznym w systemowym selektorze plików.

Dostawca przechowuje wyłącznie zaszyfrowane bajty. PasswdGen nie wysyła kopii na serwery Blackserv.

## Format v1

Kontener binarny zawiera:

1. magiczny nagłówek `PASSWDGEN-BACKUP`,
2. wersję kontenera,
3. identyfikator KDF,
4. liczbę iteracji KDF,
5. losową sól,
6. identyfikator szyfru,
7. losowy IV,
8. długość i ciphertext wraz z tagiem uwierzytelniającym GCM.

Zaszyfrowany payload zawiera wersję, czas eksportu, liczbę wpisów oraz pełne pola każdego wpisu.

## Kryptografia

- KDF: PBKDF2-HMAC-SHA256,
- domyślny koszt: 600 000 iteracji,
- sól: 16 losowych bajtów,
- klucz: 256 bitów,
- szyfr: AES-256-GCM,
- IV: 12 losowych bajtów,
- tag GCM: 128 bitów,
- parametry kontenera są chronione jako AAD.

Parametry KDF są zapisane w kontenerze, aby można było zwiększać koszt w kolejnych wersjach bez utraty zgodności z istniejącymi kopiami.

## Limity parsera

- maksymalny rozmiar pliku: 16 MiB,
- maksymalna liczba wpisów: 10 000,
- kontrolowane maksymalne długości pól,
- odrzucanie zduplikowanych identyfikatorów,
- odrzucanie nieprawidłowych dat,
- odrzucanie dodatkowych bajtów po końcu kontenera,
- rygorystyczne dekodowanie UTF-8.

## Import

Import odbywa się dopiero po odblokowaniu sejfu. Wszystkie rekordy są najpierw odszyfrowywane i walidowane, a następnie zapisywane w jednej transakcji bazy.

W przypadku wpisu o tym samym identyfikatorze:

- nowszy `updatedAt` zastępuje starszy wpis,
- starsza lub identyczna wersja nie nadpisuje aktualnych danych.

## Ważne ostrzeżenia

- Utrata hasła kopii oznacza brak możliwości odzyskania danych.
- Hasło kopii powinno różnić się od haseł zapisanych w sejfie.
- Kopię warto przechowywać w co najmniej jednym miejscu poza telefonem.
- Modyfikacja pliku, błędne hasło lub uszkodzony ciphertext powodują odrzucenie importu.
- Plik CSV z Google Password Manager nie jest kopią `.pgvault` i pozostaje jawnym plikiem migracyjnym.

## Dalszy rozwój

- #8 — automatyczny tygodniowy backup do prywatnej chmury,
- #9 — kontrolowany most CSV z Google Password Manager,
- #3 — test odtworzenia na drugim fizycznym urządzeniu i niezależny przegląd kryptograficzny.
