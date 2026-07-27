# Ikony usług w sejfie

## Cel

Wpisy sejfu mogą otrzymywać rozpoznawalne ikony usług bez automatycznego ujawniania domen użytkownika zewnętrznym podmiotom.

## Etap 1 — lokalnie i bez sieci

- normalizacja nazwy usługi oraz hosta podanego przez użytkownika,
- lokalne mapowanie popularnych usług do zasobów dołączonych do aplikacji,
- brak zapytań sieciowych,
- fallback do estetycznego monogramu, gdy usługa nie jest rozpoznana,
- ikona nie wpływa na zaszyfrowane dane wpisu ani na możliwość jego otwarcia.

Przykładowe rodziny usług: Google, Microsoft, Apple, Amazon, PayPal, GitHub, Facebook, Instagram, Netflix, Spotify, Telegram, WhatsApp, Discord, Dropbox, Proton, Revolut i popularne banki wskazane podczas testów.

## Etap 2 — opcjonalna favicon

Pobieranie favicony może być dostępne wyłącznie jako świadoma opcja użytkownika dla konkretnego wpisu.

- domyślnie wyłączone,
- bez użycia pośrednika Google Favicon ani podobnej usługi agregującej,
- wyłącznie HTTPS do hosta jawnie zapisanego przez użytkownika,
- limit rozmiaru, czasu i przekierowań,
- walidacja typu obrazu przed dekodowaniem,
- zapis tylko w prywatnej pamięci aplikacji,
- możliwość usunięcia cache,
- brak telemetrii, logowania domen i automatycznych ponowień w tle.

## Bezpieczeństwo

Lista domen w sejfie jest informacją wrażliwą. Automatyczne pobieranie ikon mogłoby ujawnić dostawcy zewnętrznemu, z jakich usług korzysta użytkownik. Dlatego mechanizm lokalny jest domyślny, a sieciowy wymaga jawnej decyzji.
