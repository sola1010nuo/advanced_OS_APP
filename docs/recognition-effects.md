# 動物辨識特效

## 修改檔案

路徑以專案根目錄為基準。

| 檔案 | 用途 |
| --- | --- |
| `app/build.gradle.kts` | Preferences DataStore 依賴 |
| `app/src/main/java/com/example/petcollection/data/EffectsSettingsRepository.kt` | 永久保存總開關 |
| `app/src/main/java/com/example/petcollection/ui/settings/EffectsSettings.kt` | 設定狀態與 Switch |
| `app/src/main/java/com/example/petcollection/ui/effects/RecognitionEffectsState.kt` | 動物對應與單次事件 |
| `app/src/main/java/com/example/petcollection/ui/effects/RecognitionEffectAnchor.kt` | 拍立得定位、生命週期與動畫分派 |
| `app/src/main/java/com/example/petcollection/ui/effects/FullScreenRecognitionEffect.kt` | 全螢幕透明、觸控穿透的 App 子視窗 |
| `app/src/main/java/com/example/petcollection/ui/effects/Banana.kt` | 共用既有香蕉向量 |
| `app/src/main/java/com/example/petcollection/ui/effects/BananaRain.kt` | 有限粒子動畫 |
| `app/src/main/java/com/example/petcollection/ui/PetCollectionApp.kt` | Settings 接線與全域開關 |
| `app/src/main/java/com/example/petcollection/ui/home/PhotoRecognitionState.kt` | 新辨識開始時清除舊特效 |
| `app/src/main/java/com/example/petcollection/ui/collection/CollectionUnlockState.kt` | 最終分類完成事件、關閉清理 |
| `app/src/main/java/com/example/petcollection/ui/collection/CollectionEncounterHost.kt` | 傳遞特效狀態 |
| `app/src/main/java/com/example/petcollection/ui/collection/EncounterDialogs.kt` | 結果拍立得與來源生命週期 |
| `app/src/main/java/com/example/petcollection/ui/collection/MonkeyCelebration.kt` | 沿用原拍立得，接入新香蕉雨 |
| `app/src/main/res/values/effects_strings.xml` | 預設文字 |
| `app/src/main/res/values-en/effects_strings.xml` | 英文文字 |
| `app/src/main/res/values-b+zh+Hant/effects_strings.xml` | 繁體中文文字 |
| `app/src/test/java/com/example/petcollection/ui/effects/RecognitionEffectsStateTest.kt` | 事件與粒子單元測試 |
| `app/src/androidTest/java/com/example/petcollection/ui/effects/RecognitionEffectsUiTest.kt` | 拍立得動畫整合測試 |
| `app/src/androidTest/java/com/example/petcollection/ui/settings/EffectsSettingsTest.kt` | 儲存與設定 UI 測試 |
| `docs/recognition-effects.md` | 架構與測試說明 |

## 觸發與生命週期

只有 `CollectionUnlockState.onClassificationResult()` 成功建立結果草稿後，才會用最終 `Breed` 與唯一 `EncounterRecord.id` 發出特效事件。手動 `onBreedRecognized()` 不發出事件。模型、Labels、分類門檻與收藏解鎖條件維持原樣。

`RecognitionEffectRegistry` 將最終品種對應到動畫種類，目前只有 `monkey → BANANA_RAIN`。`RecognitionEffectsState` 由原本的 Encounter ViewModel 持有；每個事件只能被 `claim()` 一次，關閉特效時也會消耗事件，因此重新繪製、Dialog 重掛載、旋轉或之後重新打開特效不會重播舊結果。新照片辨識成功會取得新的草稿 ID，能再次播放。

`RecognitionEffectAnchor` 保留原拍立得布局及原有事件控制，改用 `FullScreenRecognitionEffect` 將既有 Canvas 動畫放入結果 Dialog 上方的全螢幕透明 App 子視窗。視窗使用 MATCH_PARENT、TYPE_APPLICATION_ATTACHED_DIALOG 及零 inset（API 30+），不受照片或卡片裁切限制，不需要懸浮視窗權限。FLAG_NOT_TOUCHABLE／FLAG_NOT_FOCUSABLE 讓點擊、滑動、返回及焦點留給原本 UI。離開組合時立即移除視窗並 dispose composition。

離開結果、開始新辨識、關閉總開關或 Lifecycle 低於 STARTED 時停止動畫。LaunchedEffect 隨組合離開取消，Lifecycle observer 在 onDispose 移除；沒有背景計時器或持續動畫執行緒。

## 香蕉雨與素材

`Banana.kt` 沿用專案原先自行繪製的香蕉曲線，無第三方素材或動畫函式庫。`BananaRain.kt` 用單一 4 秒 Animatable 驅動 48 個快取粒子，於前 2.3 秒陸續出現，剩餘時間讓香蕉自然落到螢幕下方。每波隨機分配八個橫向區域，涵蓋整個螢幕寬度；尺寸為 24～42 dp，落下時間 1.2～1.7 秒，並有不同旋轉及飄動。香蕉從畫面上方之外開始，到畫面下方之外才消失，沒有接近底部提前淡出的情形。動畫值只在繪製階段讀取，不逐幀重組整個拍立得。

## Settings

`EffectsSettingsRepository` 使用 Preferences DataStore 1.2.1，檔名 `effects_settings.preferences_pb`，布林 key `effects_enabled`，預設 true。Application Context 的單例 delegate 避免語言切換產生不同實例。

設定尚未讀取完成前，不啟動特效；切換先更新 UI，立即停止舊動畫，再非同步持久化。讀寫失敗顯示本地化提示。開關支援中英文及日夜模式。原先語言、外觀的保存行為沒有變更。

## 新增貓、狗或其他特效

1. 在 `RecognitionEffectsState.kt` 的 `RecognitionEffectKind` 新增種類，並於 `RecognitionEffectRegistry.effectFor()` 加入品種 ID 或 `breed.species` 對應。
2. 在 `ui/effects/` 建立獨立動畫 Composable，使用有限動畫與完成回呼。
3. 在 `RecognitionEffectAnchor.kt` 的 `when (playing)` 接上新 renderer。
4. 補上 mapping、單次播放、取消及 UI 測試。

不需改寫拍立得布局，也不需再建立一套設定保存機制。

## Android 模擬器手動驗證

1. 在 `feature/effects` 執行 `gradlew.bat :app:assembleDebug`，或直接用 Android Studio Run。
2. Settings → 特效保持開啟。回首頁上傳一張清楚的猴子照片，等待模型完成分類。
3. 最終結果必須是「中山大學長」，香蕉雨應從整個螢幕上方陸續落下，跨越拍立得與周圍 UI，約 4 秒結束。後段不再出現新香蕉，剩餘香蕉自然掉出螢幕。繼續／取消應可以正常點擊，內容可滑動。
4. 等動畫完成，切換語言、外觀或旋轉螢幕，舊結果不應重播。
5. 關閉結果，再上傳另一張猴子照片，應再次播放。貓、狗或無法辨識的照片不播放。
6. Settings 關閉特效，完全結束 App 再啟動；開關仍應關閉，猴子結果不播放香蕉雨。
7. 分別檢查繁中／英文、日間／夜間及不同尺寸／橫向。確認香蕉涵蓋整個螢幕寬度、大小與飄動自然；即使香蕉經過按鈕或文字，操作仍然正常。

模型可能將照片分類為貓、狗或未辨識；這種情況不應用照片主觀內容強制觸發香蕉雨。必須依模型的最終結果判斷。

## 自動驗證

2026-10-10 本地驗證：`:app:assembleDebug`、`:app:testDebugUnitTest`、`:app:assembleDebugAndroidTest` 均 Build 成功；22 個 JVM 測試全部通過。三個語系的新增 XML 格式合法，`git diff --check` 通過。

| 模擬器 | 尺寸／密度 | 結果 |
| --- | --- | --- |
| emulator-5580 | 900 × 1600 px，320 dpi | 9 / 9 通過 |
| emulator-5554 | 1080 × 2400 px，420 dpi | 9 / 9 通過 |

上表為前一版拍立得範圍動畫的驗證紀錄。本次全螢幕修改另跑 5 個特效 UI 測試：檢查 Overlay 與顯示區 bounds 相同、第 3 秒仍播放而第 4.2 秒清除、同結果不重播、新結果重播、關閉／背景停止，並透過 Android input dispatcher 注入繼續按鈕點擊，驗證視窗觸控穿透。測試另保存裝置合成截圖供視覺檢查。

本次全螢幕版本：兩台模擬器各 5 / 5 通過，22 個 JVM 測試通過，Debug APK 與測試 APK Build 成功。已檢視兩台的裝置合成截圖，確認香蕉跨越拍立得及螢幕兩側。截圖位於 `app/build/banana-rain-preview-emulator-5580.png` 與 `app/build/banana-rain-preview-emulator-5554.png`。本次僅調整 `BananaRain.kt`、`RecognitionEffectAnchor.kt`、新增 `FullScreenRecognitionEffect.kt`、兩份特效測試及本文件；Settings、翻譯與辨識接線均未再修改。

- `RecognitionEffectsStateTest`：最終結果對應、單次消耗、關閉後不補播、新事件、取消、粒子數量與時間上限。
- `RecognitionEffectsUiTest`：注入既有的最終分類完成回呼，驗證結果 Dialog、重組、開關、背景、取消、系統觸控與全螢幕 Overlay 範圍。
- `EffectsSettingsTest`：DataStore 寫入後關閉再重新建立實例，驗證 true／false 保存；中英文及兩種主題的 Switch。

UI 測試注入的是辨識完成結果，沒有假裝經過照片模型推論。真實猴子照片端到端辨識、實際冷啟動操作、動畫視覺觀感與實機掉幀仍需手動確認。

參考：[Android WindowManager.LayoutParams](https://developer.android.com/reference/android/view/WindowManager.LayoutParams)、[Android DataStore](https://developer.android.com/topic/libraries/architecture/datastore)、[Compose side effects](https://developer.android.com/develop/ui/compose/side-effects)、[Compose 繪製階段與效能](https://developer.android.com/develop/ui/compose/performance/phases)。
