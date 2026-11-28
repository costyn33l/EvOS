<div align="center">
<h1>EvOS</h1>
<b>O viață nouă pentru ecranul Android din mașină</b>
<br><br>
<img src="https://img.shields.io/badge/Android-4.1%2B-3DDC84?logo=android&logoColor=white">
<a href="LICENSE"><img src="https://img.shields.io/github/license/costyn33l/EvOS?label=licen%C8%9B%C4%83"></a>
</div>

---

**EvOS** este un launcher (ecran principal) **gratuit, fără reclame și open source** pentru ecranele Android din mașini (head unit). Se instalează ca orice aplicație, **fără flash-uire și fără să modifice sistemul**. Dacă nu-ți place, îl dezinstalezi și totul rămâne cum era. Merge pe unitățile vechi cu Android 4.x și pe cele moderne.

Limba implicită este **româna**; din Setări → Aspect → Limbă poți alege una dintre celelalte 13 limbi.

## ✨ Ce oferă

**Pe ecranul principal**
- 🗺️ **Hartă live și offline:** descarci harta țării din aplicație (gratuit, din OpenStreetMap) și o folosești fără internet.
- 🧭 **Navigație pe hartă (opțională):** indicații vocale pas cu pas, recalculare și trasee offline aproximative.
- 📲 **Android Auto și CarPlay** dintr-o atingere (aplicația ecranului sau [Open Headunit](https://github.com/andreknieriem/headunit-revived) prin USB).
- 🎵 **Muzica ta:** titlu, artist și timp pentru ce se redă prin Bluetooth, cu butoane mari.
- ⏱️ **Vitezometru**, vreme, ceas și **memento pentru schimbul de ulei** și alte operațiuni de întreținere (RCA, ITP, filtre…).
- 🔧 **Scaner OBD-II (opțional):** ELM327 prin Bluetooth, Wi-Fi sau USB; temperatură, baterie, turații, alerte vocale și coduri de eroare explicate fără internet. Doar citește, nu scrie nimic în mașină.

**Personalizare**
- Teme cu variantă întunecată și luminoasă, culoare de accent, player în stânga sau în dreapta, bară de butoane normală, compactă sau laterală.
- Fiecare buton deschide aplicația sau funcția pe care o vrei. Unități km/h sau mph.
- Copie de siguranță a setărilor pe stick USB.

## 📥 Instalare

1. Descarcă **`EvOS-x.y.z.apk`** din [Releases](https://github.com/costyn33l/EvOS/releases/latest) și copiază-l pe ecran (prin stick USB, în memoria internă).
2. Activează **Setări → Securitate → Surse necunoscute** și instalează-l.
3. Apasă **Acasă** și alege **EvOS → Întotdeauna**. Un asistent te ghidează în rest.

## 🔒 Confidențialitate

Fără conturi, reclame sau statistici. Locația se folosește doar pe unitate. Detalii în [PRIVACY.md](PRIVACY.md).

## 📄 Licență și credite

[GNU GPL v3.0](LICENSE).

EvOS este o versiune derivată din **[StradaOS](https://github.com/lordnordic-debug/StradaOS)** de lordnordic-debug și contributorii săi, publicat sub GPL-3.0. Mulțumim tuturor celor care au contribuit la proiectul original.

Hărți © [contribuitorii OpenStreetMap](https://www.openstreetmap.org/copyright), OpenTopoMap și Esri; harta offline folosește un cititor adaptat din [mapsforge](https://github.com/mapsforge/mapsforge) (LGPL-3.0) și hărți de pe [download.mapsforge.org](https://download.mapsforge.org/). Vreme: [Open-Meteo](https://open-meteo.com). Android Auto cu [Open Headunit](https://github.com/andreknieriem/headunit-revived) (GPL-3.0). QR de la [Project Nayuki](https://github.com/nayuki/QR-Code-generator) (MIT). Pictograme [Material Symbols](https://fonts.google.com/icons) (Apache 2.0).

**Trafic în Germania (opțional, în Setări › Hartă):** date de trafic de la [Die Autobahn GmbH des Bundes](https://verkehr.autobahn.de) (datele publice ale aplicației «Autobahn»); prețuri carburant de la [Tankerkönig.de](https://creativecommons.tankerkoenig.de), licență [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/), date de la Markttransparenzstelle für Kraftstoffe (MTS-K), cu cheia API personală a utilizatorului; parcări și avertizări meteo prin [InfraNode](https://infranode.dev), cu sursa fiecărei date (de ex. „Datenbasis: Deutscher Wetterdienst”). **OBD:** formulele PID-urilor standard SAE J1979 din [OBDb/SAEJ1979](https://github.com/OBDb/SAEJ1979), licență [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/) (fișierul e inclus nemodificat în `assets/obd/`, cu traducerea românească a denumirilor sub aceeași licență). Datele specifice fiecărui model vin din depozitele [OBDb](https://github.com/OBDb) (CC BY-SA 4.0), descărcate la cerere pentru modelul ales; EvOS folosește din ele doar comenzile de citire (servicii 01/21/22).

EvOS este un proiect independent, neafiliat cu Google, Apple sau vreun producător. *Android Auto* este marcă Google LLC, iar *CarPlay* este marcă Apple Inc. Nu-l folosi în timp ce conduci.
