# Roadmap

## Etap 1 — MVP lokalny

- generator haseł,
- szyfrowany sejf,
- biometria / kod urządzenia,
- wyszukiwanie lokalne,
- kopiowanie wrażliwe,
- dokumentacja bezpieczeństwa i publikacji.

## Etap 2 — gotowość produkcyjna

- komplet testów jednostkowych i UI,
- migracje bazy bez utraty danych,
- zaszyfrowany eksport i import awaryjny,
- automatyczna blokada po konfigurowalnym czasie,
- kontrola integralności i raport błędów bez sekretów,
- pełna dostępność TalkBack,
- tłumaczenia polski/angielski,
- przegląd OWASP MASVS.

## Etap 3 — integracja systemowa

- Android Credential Provider dla haseł,
- Autofill dla starszych wersji Androida, jeżeli nadal będzie uzasadniony,
- przypisywanie wpisów do domen i pakietów aplikacji,
- import z Google Password Manager przez jawny, kontrolowany plik,
- passkeys dopiero po niezależnym audycie projektu.

## Etap 4 — opcjonalna synchronizacja

Synchronizacja nie będzie dodana bez osobnej decyzji architektonicznej. Wymaga szyfrowania end-to-end, modelu odzyskiwania, zarządzania kluczami, backendu, polityki prywatności i analizy kosztów. Serwer nie może znać klucza odszyfrowującego sejf.
