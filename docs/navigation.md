# 圖鑑重選與系統返回

目前專案沒有 Navigation Component / NavController。`PetCollectionApp` 以 destination 切換底部目的地；`CollectionScreen` 保存分類、品種及照片詳細頁 ID；`OtherAlbumsScreen` 保存自訂相簿、照片及管理／選片狀態。圖鑑主頁就是既有的分類切換與品種網格，沒有另建一個圖鑑首頁。

## 本次行為

- 從其他目的地點擊底部圖鑑：仍使用原本 destination 與 initialSpecies，不改首頁貓狗入口或底部入口的預設分類。
- 已位於圖鑑時再點底部圖鑑：更新 saveable 的 collectionRootRequest，透過 Compose key 捨棄整個圖鑑本地頁面狀態，回到原本圖鑑主頁。品種、照片、自訂相簿、多選與選片模式全部退出，舊 BackHandler 也隨組合移除。
- 主頁重複點擊不增加任何頁面／歷史。只有一個圖鑑組合，不會保留可返回的詳細頁。
- 系統返回沿用既有 BackHandler：照片詳細 → 品種／相簿；品種 → 所在分類清單；自訂相簿 → 其他相片；管理與選片先退出目前模式。沒有額外加入水平手勢，避免干擾照片操作。
- 原生返回鍵及邊緣手勢使用 AndroidX ComponentActivity 的 OnBackPressedDispatcher，target SDK 37，未停用 OnBackInvokedCallback。無上一頁時交回 Activity 的既有返回行為。
- 足跡目前只有空狀態頁，沒有詳細畫面可接入；其他底部目的地不變。

## 修改範圍

- `PetCollectionApp.kt`：圖鑑重選 key、底部按鈕測試標記、可注入既有 CollectionUnlockState 供隔離測試。
- `CollectionNavigationTest.kt`：使用記憶體 Room，不修改正式相片資料。驗證各分類深層照片頁重選回主頁、重複點擊、清除舊返回處理、原生邊緣右滑與系統返回鍵逐層返回。
- 本文件。

不修改 CollectionScreen／OtherAlbumsScreen 的拖曳、排序或管理實作，不修改辨識、特效、Settings 或資料庫。

## 驗證（2026-10-11）

- `:app:assembleDebug`、`:app:testDebugUnitTest`、`:app:assembleDebugAndroidTest` 成功，27 項單元測試通過。
- 9 項不同裝置測試分批通過：導航 2、既有中英文首頁入口 1、照片排序／管理 UI 6。
- 原生手勢測試在已啟用手勢導航的 emulator-5554 執行 shell input 從左邊緣右滑，再使用系統 KEYCODE_BACK，涵蓋貓、狗、猴子、自訂相簿、其他相片。需要等待 SystemUI 完成動畫，返回分類清單後捲回頁首才能查到 LazyGrid 分類標籤；測試已處理這兩項，最終通過。
- 圖鑑重選測試涵蓋英國短毛貓、柯基、猴子、其他貓狗、其他相片及自訂相簿的深層照片頁，且在重選後再次送出系統返回，確認舊詳細頁 callback 已清除。
- 照片跨列拖曳、短按、邊緣捲動、日期與自訂排序切換、多選移動及中英文日夜模式回歸通過。
- `git diff --check` 通過。分支 `feature/navigation`，沒有 commit、push 或 merge。不同實體手機的原生手勢手感尚需實機確認。
