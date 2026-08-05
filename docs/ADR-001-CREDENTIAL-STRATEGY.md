# ADR-001: Strategia sejfu i integracji poświadczeń

- Status: zaakceptowana
- Data: 2026-07-26

## Kontekst

PasswdGen ma być aplikacją możliwą do publikacji w Google Play, zawierającą generator haseł oraz sejf loginów i haseł. Użytkownik rozważał połączenie aplikacji z Google Password Manager.

Bezpośrednie używanie sejfu Google jako zapisywalnej bazy dla dowolnych witryn nie jest właściwym fundamentem tej aplikacji. Uzależniłoby produkt od możliwości i ograniczeń zewnętrznego dostawcy, a aplikacja nie miałaby pełnej kontroli nad formatem danych, migracją, odzyskiwaniem ani modelem bezpieczeństwa.

## Decyzja

1. Źródłem prawdy PasswdGen będzie własny, lokalny i szyfrowany sejf.
2. W MVP dane nie opuszczają urządzenia i aplikacja nie ma uprawnienia `INTERNET`.
3. Po ustabilizowaniu sejfu aplikacja zostanie rozszerzona o Android Credential Provider, aby system i inne aplikacje mogły proponować zapisane poświadczenia.
4. Import i eksport z innych menedżerów haseł będzie jawny, kontrolowany przez użytkownika i poprzedzony ostrzeżeniem dotyczącym plików w formacie otwartym.
5. Nie wdrażamy nieudokumentowanej ani pośredniej synchronizacji z Google Password Manager.
6. Passkeys nie będą dodane w pierwszym wydaniu Credential Provider. Wymagają osobnego modelu zagrożeń, testów interoperacyjności i audytu.
7. Synchronizacja chmurowa może zostać rozważona wyłącznie jako szyfrowanie end-to-end, w którym serwer nie zna klucza odszyfrowującego.

## Konsekwencje

### Zalety

- pełna kontrola nad bezpieczeństwem, migracjami i odzyskiwaniem,
- możliwość działania całkowicie offline,
- mniejszy zakres danych i zależności w pierwszej wersji,
- jasny podział między sejfem PasswdGen a integracją z systemem Android.

### Koszty i ograniczenia

- konieczność samodzielnego zaprojektowania eksportu awaryjnego,
- integracja systemowa powstanie dopiero po stabilizacji podstawowego sejfu,
- dane nie będą automatycznie synchronizowane z Google Password Manager,
- przyszła synchronizacja między urządzeniami będzie osobnym, kosztownym etapem.

## Warunek zmiany decyzji

Decyzję można zmienić tylko po udokumentowaniu nowego, oficjalnego API platformy, które pozwala bezpiecznie i zgodnie z politykami sklepu zrealizować wymaganą integrację, oraz po ponownej analizie modelu zagrożeń.
