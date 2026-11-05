# Move TV Desktop – Android TV aplikacija

Otvara https://play.move.tv/ preko celog ekrana kao da je desktop računar
(Windows Chrome identitet), sa kursorom miša koji se pomera daljinskim.
Namenjeno za X96 Max+ (Android 9), radi i na drugim Android TV boksovima.

## Pravljenje APK-a (GitHub Actions)
1. Napravi novi repozitorijum na GitHub-u (npr. `MoveTV`) i ubaci SVE fajlove
   iz ovog foldera (uključujući skriveni `.github` folder i `app/webtv.jks`).
2. Na kartici **Actions** sačekaj da se završi "Build APK" (~3–5 min).
3. APK se nalazi:
   - u **Releases → latest → MoveTV.apk**, ili
   - u Actions → poslednji build → Artifacts → `MoveTV-apk` (zip).

## Instalacija na X96 Max+
**Varijanta A – USB fleš:** kopiraj `MoveTV.apk` na fleš, na boksu otvori
File Manager → klikni na APK → Instaliraj (dozvoli "Nepoznati izvori" kad pita).

**Varijanta B – Downloader aplikacija:** instaliraj "Downloader" iz Play prodavnice
i upiši adresu (repo mora biti javan):
`https://github.com/KORISNIK/REPO/releases/download/latest/MoveTV.apk`

## Daljinski
| Taster | Radnja |
|---|---|
| Strelice | pomeranje kursora (drži = ubrzava); na ivici ekrana skroluje stranicu |
| OK | klik (drži OK + strelice = prevlačenje, npr. traka za premotavanje) |
| NAZAD | prethodna stranica / izlaz iz celog ekrana; 2× na početnoj = izlaz |
| NAZAD (drži) ili MENU | meni: osveži, uvećaj/umanji prikaz, brzina kursora, autostart, adresa, odjava |
| Play/Pauza | pauza/nastavak videa |
| CH+/CH− ili PgUp/PgDn | skrol stranice |

## Napomene
- Sledeće verzije se instaliraju preko stare (isti potpis, rastući broj verzije).
- Ako video ne kreće ili sajt javlja da pregledač nije podržan: ažuriraj
  "Android System WebView" (Play prodavnica) – na Android 9 boksovima je često zastareo.
- Zaštićeni (DRM/Widevine) sadržaj zavisi od samog uređaja; X96 Max+ obično ima
  Widevine L3, pa neki servisi mogu ograničiti kvalitet.
