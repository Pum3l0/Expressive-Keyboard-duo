# Обновление форка из оригинала (Expressive Keyboard Duo)

Этот файл описывает, как подтянуть новую версию оригинального репозитория
[`kotlalipopus/Expressive-Keyboard`](https://github.com/kotlalipopus/Expressive-Keyboard)
в наш форк [`Pum3l0/Expressive-keyboard-duo`](https://github.com/Pum3l0/Expressive-keyboard-duo).

## Схема версий

```
<версия оригинала>-duo.<номер нашей ревизии>
```

Пример: `11.1.6-duo.1` — оригинал 11.1.6, первая наша сборка поверх него.

- `versionCode` берём из оригинала (у 11.1.6 — `20`), чтобы обновления «поверх» работали;
- `versionName` — `"<оригинал>-duo.N"`;
- OTA-обновления (`Updater.kt`) смотрят **наш** репозиторий и сравнивают в том числе суффикс `-duo.N`.

## Быстрый порядок действий

```bash
# 1. Забрать оригинал (remote `upstream` уже настроен)
git fetch upstream --tags

# 2. Влить последний релиз оригинала в свою ветку
git merge upstream/main

# 3. Разрешить конфликты (см. ниже), затем собрать
./gradlew app:assembleDebug

# 4. Поднять версию: app/build.gradle.kts -> versionName = "X.Y.Z-duo.N"
# 5. Закоммитить и запушить
git add -A && git commit -m "Merge upstream vX.Y.Z"
git push origin main

# 6. Выпустить релиз
git tag vX.Y.Z-duo.N && git push --tags   # GitHub Actions соберёт APK и опубликует релиз
```

Если remote `upstream` пропал:

```bash
git remote add upstream https://github.com/kotlalipopus/Expressive-Keyboard.git
```

## Файлы, которые обычно конфликтуют

| Файл | Что там наше |
|---|---|
| `app/build.gradle.kts` | `versionName = "...-duo.N"`, автоименование APK |
| `KeyboardSettings.kt` | блок «складные экраны» (`split*`, `centralGap*`, `crease*`, `keyWidthPercent`), `FoldPosture`, `forPosture()`, `bgCrop*` |
| `KeyboardUi.kt` | `CentralGapWidget`, `splitRow`, разметка сплита внутри `KeyGrid`, `TOUCHPAD`, фон с обрезкой |
| `KeyboardService.kt` | `moveCursor` / `moveCursorVertical` / `extendSelection` (Shift-выделение), `checkSmartConjunctionComma`, `triggerCreasePulse` |
| `WordPredictor.kt` | режим `AGGRESSIVE` в `correctionLower` |
| `MainActivity.kt` | двухпанельный `SettingsScreen`, `SidebarNavigation`, страница `FOLD` / `FoldPage`, редактор обрезки фона |

## Как искать наши врезки

Все Duo-блоки помечены сентинелами. Ищите по строке `DUO-BEGIN`:

```bash
rg "DUO-BEGIN|DUO-END"
```

Формат:

```kotlin
// >>> DUO-BEGIN: <название блока> — см. docs/DUO_MERGE.md
... наш код ...
// <<< DUO-END: <название блока>
```

При конфликте правило простое: **сначала берём новую версию upstream**, затем возвращаем
содержимое соответствующего Duo-блока из предыдущего коммита:

```bash
git show HEAD~1:app/src/main/java/com/expressive/keyboard/KeyboardUi.kt > /tmp/old_ui.kt
# найти в /tmp/old_ui.kt блок между DUO-BEGIN и DUO-END и вернуть его
```

## Что нельзя терять при слиянии

- **Сплит-раскладка**: `settings.splitKeyboard` (`AUTO`/`ON`/`OFF`), `splitMode`
  (`STANDARD`/`DUPLICATE`), `splitLayout` (`FLAT`/`STAGGERED`), `splitSpacebar`.
  В `KeyGrid` наши ветки включаются флагом `isSplit`; если upstream добавит свой
  `landscapeSplit` — оставляем оба (`isSplit || split`).
- **`rows = baseRows`**: upstream может захотеть `KeyboardLayouts.split(baseRows)` — нам это не
  нужно, половины делит наш `splitRow`, иначе буквы продублируются дважды.
- **`KeyView(gapX = hSpace, gapY = rowSpacing)`**: так работает «касание в половине промежутка —
  тоже эта клавиша». Не возвращайте старый `hSpace = …` и `.padding(horizontal = hSpace / 2)` —
  из-за него появлялись мёртвые зоны между клавишами.
- **`FoldPage`**: если upstream переименует `SettingsPage.KEYS`, поправьте вызов `KeysPage(...)`
  в `when (page)` и в списках `SidebarNavigation` / `HomePage`.
- **`Updater.REPO`**: должен остаться `Pum3l0/Expressive-keyboard-duo`.

## Известные «тонкие» места

- `KeyboardScreen`/`KeyboardRoot` — фон рисуется из `bgCropFor(unfolded)`; при слиянии следите,
  чтобы `Canvas { drawCropped(...) }` не заменился обратно на `Image(ContentScale.Crop)`.
- `KeyView` — upstream может переписать обработку касаний; наш `act.keyTouch(fx, fy)` нужен для
  учёта промахов пальца в автозамене.
- `WordPredictor.correctionLower` — бюджет опечаток upstream задаётся `keepCost(len, normal)`;
  наш `AGGRESSIVE` просто увеличивает его (`keep += 3.2f`) и снимает ограничения для коротких слов.

## Полезное

- Проверка сборки: `./gradlew app:assembleDebug`
- Тесты upstream: `./gradlew app:testDebugUnitTest`
- Подпись релиза: если в `local.properties` задан `RELEASE_STORE_FILE`, используется свой ключ,
  иначе — отладочный (тогда обновления встают только поверх отладочных сборок).
