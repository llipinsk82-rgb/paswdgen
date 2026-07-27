# Migracja CSV z Google Password Manager

## Charakter funkcji

CSV służy wyłącznie do krótkotrwałej migracji. Nie jest szyfrowaną kopią zapasową ani mechanizmem synchronizacji. Plik zawiera hasła w postaci jawnej.

## Import do PasswdGen

Obsługiwane wymagane kolumny:

- `url`,
- `username`,
- `password`.

Kolumny opcjonalne:

- `name`,
- `note`.

Parser obsługuje UTF-8, BOM, dowolną kolejność nagłówków, pola cytowane, przecinki, podwójne cudzysłowy i nowe linie. Przed zapisem użytkownik otrzymuje podgląd liczby rekordów poprawnych, odrzuconych i zduplikowanych.

Oczekujące rekordy pozostają tylko w pamięci i są czyszczone po anulowaniu albo zablokowaniu sejfu. Rekordy zaakceptowane trafiają do lokalnego zaszyfrowanego sejfu.

## Eksport z PasswdGen

Eksport tworzy nagłówek:

```text
name,url,username,password,note
```

Wszystkie pola są cytowane. Wpisy bez adresu witryny są pomijane. Jeden plik zawiera maksymalnie 3 000 rekordów, aby odpowiadać przepływowi importu Google Password Manager.

## Ochrona przed przypadkowym ujawnieniem

- osobna sekcja „Migracja Google CSV”,
- ostrzeżenie przed utworzeniem pliku,
- ponowne uwierzytelnienie biometrią lub kodem,
- zapis tylko do lokalizacji wskazanej przez użytkownika,
- brak automatycznego uploadu,
- wykrywanie pól zaczynających się od `=`, `+`, `-` lub `@`,
- ostrzeżenie, aby nie otwierać takiego CSV w arkuszu kalkulacyjnym,
- czyszczenie bufora bajtów po zapisie.

## Procedura użytkownika

1. Utwórz CSV wyłącznie na czas migracji.
2. Zaimportuj plik w docelowym menedżerze haseł.
3. Potwierdź obecność rekordów w docelowym sejfie.
4. Usuń CSV z pamięci urządzenia, folderu pobranych plików, kosza i chmury.
5. Do regularnego odzyskiwania używaj wyłącznie zaszyfrowanego `.pgvault`.
