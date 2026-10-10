# 相簿照片排序

## 適用頁面

| 照片分類 | UI | 獨立儲存鍵 |
| --- | --- | --- |
| 所有貓、狗品種（包含其他貓、其他狗） | BreedDetailScreen | breed:品種 ID |
| 中山大學長 | BreedDetailScreen | breed:monkey |
| 其他分類中尚未分配的照片 | OtherAlbumsScreen | other:unassigned |
| 使用者建立的所有其他相簿 | OtherAlbumsScreen | other:相簿 UUID |

目前其餘照片 UI 是封面、單張照片詳細資訊、上傳預覽與辨識結果，不是多張照片相簿。這些畫面的行為保持原樣。

## 操作與儲存

- 相簿右上角「排序」選單提供從新到舊、從舊到新、自訂排序，勾選目前模式。
- 短按繼續開啟詳細資訊；長按拖曳跨列移動，靠近上下邊緣自動捲動。
- 兩種日期模式只排序顯示資料，不覆寫自訂順序。完成拖曳時，以拖曳完成的整個可見相簿順序保存，並切換自訂模式。
- Room `photo_sort_modes` 依 albumKey 保存模式；`photo_custom_order` 以 albumKey + encounterId 保存位置。
- 新照片加入自訂順序尾端；移入／移出相簿也在同一 transaction 中同步排序。照片刪除由外鍵級聯清理對應排序資料。
- 編輯日期不改動自訂順序。分類變更會從原分類移除排序資料，再加到新分類尾端。
- 日期來源為原本編輯器的 `EncounterRecord.encounteredAt.toLocalDate()`，以日期值比較，同日保留自訂順序。空日期在兩種日期模式都排最後。
- 日期欄位現在可留空，使用原欄位的 null 值，不新增另一份日期。非空的無效日期仍禁止儲存。
- Room v2 → v3 migration 保存既有 encounters、封面、自建相簿及成員；備份外鍵子表後重建可空日期欄位，恢復子表，建立排序表並以舊順序初始化。v1 使用 1 → 2 → 3 升級。
- 拖曳只改排序資料，不移動或複製照片檔案。不新增依賴，不改 Settings 或辨識特效。

## 修改檔案

- 資料層：`PhotoSorting.kt`（新增）、`CollectionDatabase.kt`、`EncounterRepository.kt`、`EncounterRecord.kt`、Room schema `3.json`（新增）。
- 共用 UI：`PhotoGridSorting.kt`（新增）、`CollectionUnlockState.kt`。
- 相簿整合：`BreedDetailScreen.kt`、`CollectionScreen.kt`、`OtherAlbumsScreen.kt`。
- 空日期顯示與編輯：`EncounterDetailScreen.kt`、`EncounterDialogs.kt`。
- 語系：values、values-en、values-b+zh+Hant 下的 `photo_sort_strings.xml`（新增）。
- 測試：`PhotoSortingTest.kt`、`PhotoSortingPersistenceTest.kt`、`PhotoSortingUiTest.kt`（新增），`OtherAlbumsTest.kt`（新增 migration 註冊）、`EncounterLocalizationTest.kt`（支援 lazy 網格與已存在的使用者照片，不清除正式資料庫）。
- 本文件。

## 測試範圍

單元測試驗證實際年月日比較、空日期、同日穩定順序、日期修改、自訂／日期模式切換與相簿鍵隔離。

裝置測試驗證所有相簿類型的獨立順序及模式、關閉再重新開啟資料庫、新增／刪除／移動／改分類、v1/v2 migration、跨列拖曳、短按、邊緣捲動，以及中英文和日夜模式。

實機體驗檢查：在每一類相簿拖曳照片到另一欄／另一列、拖到上下邊緣、切換日期和自訂模式，離開相簿及重新啟動 App 後確認順序和模式仍相同。大量照片及不同手機尺寸的動畫順暢度，仍應在實際手機確認。

## 驗證結果（2026-10-11）

- `:app:assembleDebug`、`:app:testDebugUnitTest`、`:app:assembleDebugAndroidTest` 成功。
- 27 項單元測試通過；31 項不同的裝置測試分批通過（排序 UI 5、排序持久化 2、既有其他相簿 4、集合持久化 3、相簿 CRUD 8、語系 2、辨識特效 5、Settings 2）。
- 邊緣捲動測試同時確認 viewport 移到後續列及照片 ID 沒有遺失／重複。
- 既有正式 App 語系測試現在依實際解鎖狀態驗證文字，支援保留了照片的模擬器，不清除使用者資料。
- 測試裝置為 emulator-5554（1080 × 2400、420 dpi）；尚未在多種實體手機驗證拖曳手感。
- `git diff --check` 通過，分支為 `feature/photo-reorder`，未 commit、push 或 merge。

## 相片管理與排序 UI 調整（2026-10-11）

- 實際照片網格為 `BreedDetailScreen`（貓、狗、猴子、其他貓狗）及 `OtherAlbumsScreen`（其他相片、自訂相簿）。`CollectionScreen` 的品種卡片清單不放照片排序按鈕。
- 品種頁的排序由頁首移至「我的相遇照片」區右上方；其他相片及自訂相簿改在照片標題下方靠右並列「管理相片」與「排序」，避免擠壓標題與返回按鈕。
- 三份語系資源中的 `other_organize` 改為「管理相片」／「Manage Photos」。沿用勾選框、多選數量、取消、目的相簿選單及移動回呼；管理時停用排序與拖曳，退出後恢復。
- 移動沿用 `OtherAlbumMember` 關聯交易及 `reconcilePhotoOrders`，保留來源剩餘順序，移入目的自訂順序尾端。日期排序沿用 `encounteredAt`，不更新照片紀錄或實際檔案。目的地限既有其他自訂相簿，不改貓狗猴子辨識分類。
- 本次修改：兩個畫面、三份 `other_album_strings.xml`、`PhotoSortingUiTest.kt` 及本文件。拖曳核心、資料庫與 Settings 未再修改。
- UI 測試補上所有品種照片區選單，以及多選移動、資訊保留、來源／目標順序與取消管理。既有拖曳、日期模式、持久化與 CRUD 測試回歸。
- 本次驗證：App／測試 APK 編譯成功，27 項單元測試通過；16 項不同裝置測試分批通過（照片排序 UI 6、排序持久化 2、相簿 CRUD 8）。初次三項 UI 測試定位失敗，改用 LazyGrid 捲動定位與目的相簿對話框範圍後，三項重跑全部通過。

## Compose 實作參考

長按手勢使用 [AndroidX DragGestureDetector](https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/foundation/foundation/src/commonMain/kotlin/androidx/compose/foundation/gestures/DragGestureDetector.kt) 提供的 awaitLongPressOrCancellation / drag。根網格手勢只處理照片，保留相簿卡片的既有長按操作。

拖曳座標換算考量 [AndroidX LazyGridMeasuredItem](https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/foundation/foundation/src/commonMain/kotlin/androidx/compose/foundation/lazy/grid/LazyGridMeasuredItem.kt) 在佈局時另加 visualOffset 的內容 padding；移動第一個可見 key 時固定 viewport，避免 LazyGrid 跟隨 key 跳動。
