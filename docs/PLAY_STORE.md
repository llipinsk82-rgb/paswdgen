# Google Play — przygotowanie wydania

## Stan zgodności

- `targetSdk = 36`,
- `compileSdk = 36`,
- Android Gradle Plugin 8.12.2,
- zweryfikowany Gradle Wrapper 8.13,
- testy jednostkowe, Android Lint i debug APK przechodzą w CI,
- brak reklam i SDK analitycznych,
- brak uprawnienia `INTERNET`,
- aplikacja przetwarza dane logowania wyłącznie lokalnie,
- backup danych aplikacji jest wyłączony.

## Formularz Data safety — projekt odpowiedzi MVP

Dla wersji bez sieci aplikacja nie zbiera ani nie udostępnia danych użytkownika poza urządzenie. Dane logowania są podawane przez użytkownika i przechowywane lokalnie w formie zaszyfrowanej.

Odpowiedzi muszą zostać ponownie sprawdzone po dodaniu synchronizacji, raportowania błędów, analityki, importu/eksportu albo Credential Provider.

## Polityka prywatności — szkic

PasswdGen przechowuje dane logowania lokalnie na urządzeniu. Aplikacja w wersji MVP nie wysyła danych do serwerów, nie zawiera reklam, analityki ani mechanizmów śledzących. Użytkownik może trwale usunąć pojedynczy wpis z poziomu sejfu. Odinstalowanie aplikacji usuwa lokalną bazę i klucz szyfrujący.

Przed publikacją należy uzupełnić:

- nazwę i dane administratora aplikacji,
- adres kontaktowy,
- adres URL polityki prywatności,
- procedurę obsługi zgłoszeń bezpieczeństwa,
- datę wejścia polityki w życie.

## Lista kontrolna wydania

- [ ] zatwierdzić nazwę aplikacji i `applicationId`,
- [ ] wygenerować oraz zabezpieczyć klucz podpisujący upload,
- [ ] włączyć Play App Signing,
- [x] dodać kompletny Gradle Wrapper i zweryfikować checksumę,
- [x] przeprowadzić `testDebugUnitTest`, `lintDebug` i `assembleDebug`,
- [ ] zbudować niepodpisany release AAB i zachować raport R8,
- [ ] przeprowadzić testy emulatorów API 30 i API 36,
- [ ] przeprowadzić testy na fizycznym urządzeniu Google Pixel i Samsung,
- [ ] przygotować ikonę 512×512, feature graphic i zrzuty ekranów,
- [ ] opublikować politykę prywatności,
- [ ] wypełnić Data safety,
- [ ] wypełnić deklarację zawartości i klasyfikację wiekową,
- [ ] sprawdzić ostrzeżenia pre-launch report,
- [ ] wdrożyć test zamknięty przed produkcją,
- [ ] potwierdzić procedurę odzyskiwania i eksportu awaryjnego.
