# MROS Klaviatura

Android uchun Gboard'ga o'xshash klaviatura — **Liquid Glass** (shisha) dizaynida, iOS uslubidagi emoji paneli bilan.

## Imkoniyatlar
- Tillar: **Oʻzbekcha (lotin)**, **Русский / Ўзбекча (kirill)**, **English** — bo'shliqni bosib turish yoki yuqoridagi 🌐 UZ belgisi
- Shisha tugmalar: jele kabi tebranish, suv tomchisi to'lqini, orqadagi rangli yorug'lik, "pop" bo'ladigan harf baloni
- Android 15 da tizimning pastki tugmalari (⌄, klaviatura almashtirish) klaviatura ustiga tushmaydi; "Pastdan masofa" sozlamasi bor
- Uzoq bosish: raqamlar va qo'shimcha harflar (o → oʻ, g → gʻ, е → ё, у → ў, к → қ, г → ғ, х → ҳ ...)
- Shift / CAPS (ikki marta bosish), avtomatik bosh harf, ikki bo'shliq = nuqta
- Bo'shliq tugmasini surib kursorni siljitish, bosib turish — klaviaturalar ro'yxati
- So'z takliflari (o'zbek, rus, ingliz) + siz yozgan so'zlarni o'rganish
- iOS uslubidagi emoji panel: gorizontal varaqlash, bo'limlar, "tez-tez ishlatiladi", **qidiruv** (o'zbek/rus/ingliz so'zlar bilan), **teri rangini tanlash** (emoji ustida bosib turing)
- Bufer (clipboard) tarixi — nusxa olingan matnlarni bir bosishda joylash
- Raqam maydonlari uchun alohida raqam paneli, email/havola maydonlari uchun @ va / tugmalari
- Yorug' / qorong'i / avto mavzu, balandlikni o'zgartirish, tebranish va ovoz sozlamalari

## GitHub orqali APK yig'ish
Har bir `push` dan keyin **Actions** avtomatik APK yig'adi va **Releases** bo'limiga joylaydi.

### Telefondan (kompyutersiz)
1. github.com da yangi repo oching (masalan `mros-keyboard`).
2. **Add file → Create new file**, nomiga `.github/workflows/build.yml` deb yozing va shu fayl mazmunini joylang → *Commit*.
3. **Add file → Upload files** → `mros-keyboard.zip` ni yuklang → *Commit*.
4. **Actions** bo'limida build tugashini kuting (5–8 daqiqa). ZIP avtomatik ochiladi va kod repoga joylanadi.
5. **Releases** bo'limidan `MROS-Klaviatura-...apk` ni yuklab, o'rnating.

### Kompyuterdan
ZIP ichidagi `mros-keyboard` papkasi mazmunini repoga yuklang (`.github` papkasi bilan birga) va push qiling.

## Ilova ichidan yangilanish (yopiq repo uchun)
Ilova yangilanishni **ochiq** repodagi Releases'dan oladi (yopiq repoga ilova kira olmaydi).
1. GitHub'da yangi **ochiq** repo oching, masalan `mros-keyboard-releases` ("Add a README file" belgilansin).
2. GitHub → Settings → Developer settings → **Fine-grained token** yarating:
   faqat shu ochiq repo, ruxsat: **Contents → Read and write**.
3. Asosiy (yopiq) repoda: Settings → Secrets and variables → Actions:
   - **Secrets** → `RELEASES_TOKEN` = token
   - **Variables** → `RELEASES_REPO` = `foydalanuvchi/mros-keyboard-releases`
4. Keyingi build'dan boshlab APK ochiq repoga joylanadi, ilova esa uni topib, bildirishnoma yuboradi.
"Nima yangi" matni `release-notes.md` faylidan olinadi — har build oldidan tahrirlang.

## Shaxsiy shrift
Sozlamalar → Shaxsiy shrift → "TTF fayl yuklash". Tanlangan shrift emoji panelida ishlatiladi.

## O'rnatgandan keyin
Ilovani oching → **1. Klaviaturani yoqish** → **2. MROS Klaviaturani tanlash**.

## 3D emoji
Build paytida [Microsoft Fluent Emoji](https://github.com/microsoft/fluentui-emoji) (MIT litsenziya) 3D rasmlari yuklab olinadi
va `assets/emoji3d/` ga WebP qilib joylanadi (`tools/fluent_emoji.py`). Yuklab bo'lmasa — tizim emojisi ishlatiladi.
Sozlamalarda "Emoji uslubi: 3D / Tizim" tanlanadi.

## Eslatma
- Emoji panelda 3D (Fluent) yoki tizim uslubida ko'rinadi. Yuborilgan emoji esa oddiy belgi: matn maydonida va qabul qiluvchida o'sha ilova/qurilma uslubida chiqadi (iPhone'da — Apple emojilari).
- `app/mros-keystore.jks` — yangilanishlar ustidan o'rnatilishi uchun doimiy imzo kaliti. Repo ochiq bo'lsa, kalitni GitHub Secrets'ga ko'chirish tavsiya etiladi.
