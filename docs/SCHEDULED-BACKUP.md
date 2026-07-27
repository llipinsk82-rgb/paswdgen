# Automatyczna zaszyfrowana kopia

## Założenia

Automatyczna kopia nie może omijać uwierzytelnienia wymaganego przez główny klucz sejfu. Worker działający w tle nie odszyfrowuje bazy i nie otrzymuje loginów ani haseł.

## Przepływ

1. Użytkownik odblokowuje sejf i konfiguruje osobne hasło odzyskiwania.
2. PasswdGen wyprowadza klucz PBKDF2-HMAC-SHA256 i nie zapisuje hasła.
3. Pochodny klucz jest opakowany osobnym kluczem AES-256-GCM w Android Keystore.
4. Po każdej zmianie odblokowany sejf tworzy lokalny zaszyfrowany snapshot `.pgvault`.
5. WorkManager raz w tygodniu kopiuje wyłącznie snapshot do folderu wybranego przez Storage Access Framework.
6. Po zapisie aplikacja odczytuje dokument ponownie i porównuje SHA-256.
7. Zachowywane są cztery najnowsze automatyczne kopie PasswdGen; ręczne kopie i inne pliki nie są usuwane.

## Dostawcy

Folder może pochodzić z pamięci urządzenia albo z dostawcy Android DocumentsProvider, np. Google Drive lub OneDrive. Dostęp ogranicza się do folderu jawnie wybranego przez użytkownika i może zostać odebrany przez wyłączenie funkcji.

## Harmonogram

- częstotliwość: raz na siedem dni,
- opcjonalnie tylko sieć bez limitu,
- wymagany odpowiedni poziom baterii i wolnego miejsca,
- ponowienia z wykładniczym opóźnieniem,
- ręczny przycisk „Kopia teraz”.

## Dane widoczne dla chmury

Dostawca widzi nazwę dokumentu, rozmiar i czas modyfikacji. Zawartość pozostaje zaszyfrowanym kontenerem `.pgvault`. PasswdGen nie wysyła kopii na serwery Blackserv.

## Odzyskiwanie

Do odtworzenia na innym urządzeniu potrzebny jest plik `.pgvault` i dokładne hasło odzyskiwania. Klucz opakowany w Android Keystore służy wyłącznie harmonogramowi na bieżącym urządzeniu i nie zastępuje hasła odzyskiwania.
