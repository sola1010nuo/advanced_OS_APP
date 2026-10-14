# 狗模型更新與固定稀有度

分支：feature/dog-model-update。此報告為實作對照，沒有 Commit、Push 或 Merge。

## 模型與資料相容性

- dog_labels.txt 的實際順序是唯一模型索引依據，36 個輸出均能對應目前狗狗圖鑑。
- 輸入 [1,224,224,3] FLOAT32、RGB 0～255；輸出 [1,36] FLOAT32，末層 Softmax。
- FlatBuffer 首兩個運算為乘以 0.007843137718737125 與減去 1，等同 x / 127.5 - 1。App 不再做額外正規化。
- 狗品種門檻維持 75%；貓品種 75%、毛色 60%、第一階段 70% 保持不變。
- 圖鑑總數：貓咪 28（22 品種＋6 毛色）、狗狗 36、整體 65（含既有猴子）。
- 舊版品種退出現行圖鑑與完成率，但保留原始 ID、照片、封面、介紹及相簿，有既有紀錄時顯示「舊版品種相簿」。沒有資料庫遷移或清除。
- 稀有度是台灣日常遇見難易程度的遊戲化主觀設定，非正式人口統計，也不使用模型信心分數。毛色、其他貓咪、猴子、舊版相簿不套用星級。
- 收集徽章按現有 1／5／10／20／30 項與全圖鑑條件顯示解鎖，全圖鑑目標由共用統計取得。

## 移除與新增

退出現行圖鑑（相簿保留）：英國蹲獵犬（English Setter）、庇里牛斯山犬（Great Pyrenees）、哈威那（Havanese）、日本狆（Japanese Chin）、凱斯犬（Keeshond）、蘭伯格犬（Leonberger）。

新增：台灣犬（Taiwan Dog）、臘腸犬（Dachshund）、西施犬（Shih Tzu）、比熊犬（Bichon Frise）、西伯利亞哈士奇（Siberian Husky）、日本狐狸犬（Japanese Spitz）、西高地白梗（West Highland White Terrier）、伯恩山犬（Bernese Mountain Dog）。

## 36 個狗模型標籤的原始順序與對應

| 模型索引 | 原始 Label | 穩定資料 ID | 中文名稱 | 英文名稱 | 星級 | 狀態 |
|---:|---|---|---|---|---:|---|
| 0 | Bernese_Mountain_Dog | bernese_mountain_dog | 伯恩山犬 | Bernese Mountain Dog | 4 | 新增 |
| 1 | Bichon_Frise | bichon_frise | 比熊犬 | Bichon Frise | 2 | 新增 |
| 2 | Border_Collie | border_collie | 邊境牧羊犬 | Border Collie | 3 | 保留 |
| 3 | Corgi | corgi | 柯基犬 | Corgi | 1 | 保留 |
| 4 | Dachshund | dachshund | 臘腸犬 | Dachshund | 2 | 新增 |
| 5 | French_Bulldog | french_bulldog | 法國鬥牛犬 | French Bulldog | 2 | 保留 |
| 6 | Golden_Retriever | golden_retriever | 黃金獵犬 | Golden Retriever | 1 | 保留 |
| 7 | Japanese_Spitz | japanese_spitz | 日本狐狸犬 | Japanese Spitz | 3 | 新增 |
| 8 | Labrador_Retriever | labrador_retriever | 拉布拉多犬 | Labrador Retriever | 1 | 保留 |
| 9 | Maltese | maltese | 馬爾濟斯 | Maltese | 1 | 保留 |
| 10 | Poodle | poodle | 貴賓犬 | Poodle | 1 | 保留 |
| 11 | Schnauzer | schnauzer | 雪納瑞 | Schnauzer | 2 | 保留 |
| 12 | Shetland_Sheepdog | shetland_sheepdog | 喜樂蒂牧羊犬 | Shetland Sheepdog | 3 | 保留 |
| 13 | Shih_Tzu | shih_tzu | 西施犬 | Shih Tzu | 2 | 新增 |
| 14 | Siberian_Husky | siberian_husky | 西伯利亞哈士奇 | Siberian Husky | 3 | 新增 |
| 15 | Taiwan_Dog | taiwan_dog | 台灣犬 | Taiwan Dog | 1 | 新增 |
| 16 | West_Highland_White_Terrier | west_highland_white_terrier | 西高地白梗 | West Highland White Terrier | 3 | 新增 |
| 17 | american_bulldog | american_bulldog | 美國鬥牛犬 | American Bulldog | 5 | 保留 |
| 18 | american_pit_bull_terrier | american_pit_bull_terrier | 美國比特鬥牛㹴 | American Pit Bull Terrier | 4 | 保留 |
| 19 | basset_hound | basset_hound | 巴吉度獵犬 | Basset Hound | 4 | 保留 |
| 20 | beagle | beagle | 米格魯 | Beagle | 2 | 保留 |
| 21 | boxer | boxer | 拳師犬 | Boxer | 4 | 保留 |
| 22 | chihuahua | chihuahua | 吉娃娃 | Chihuahua | 2 | 保留 |
| 23 | english_cocker_spaniel | english_cocker_spaniel | 英國可卡犬 | English Cocker Spaniel | 4 | 保留 |
| 24 | german_shorthaired | german_shorthaired_pointer | 德國短毛指示犬 | German Shorthaired Pointer | 5 | 保留 |
| 25 | miniature_pinscher | miniature_pinscher | 迷你杜賓犬 | Miniature Pinscher | 3 | 保留 |
| 26 | newfoundland | newfoundland | 紐芬蘭犬 | Newfoundland | 5 | 保留 |
| 27 | pomeranian | pomeranian | 博美犬 | Pomeranian | 1 | 保留 |
| 28 | pug | pug | 巴哥犬 | Pug | 3 | 保留 |
| 29 | saint_bernard | saint_bernard | 聖伯納犬 | Saint Bernard | 5 | 保留 |
| 30 | samoyed | samoyed | 薩摩耶 | Samoyed | 3 | 保留 |
| 31 | scottish_terrier | scottish_terrier | 蘇格蘭㹴 | Scottish Terrier | 4 | 保留 |
| 32 | shiba_inu | shiba_inu | 柴犬 | Shiba Inu | 1 | 保留 |
| 33 | staffordshire_bull_terrier | staffordshire_bull_terrier | 斯塔福郡鬥牛㹴 | Staffordshire Bull Terrier | 5 | 保留 |
| 34 | wheaten_terrier | soft_coated_wheaten_terrier | 愛爾蘭軟毛麥色㹴 | Soft Coated Wheaten Terrier | 5 | 保留 |
| 35 | yorkshire_terrier | yorkshire_terrier | 約克夏 | Yorkshire Terrier | 2 | 保留 |

## 品種貓完整排序

同星級由較常遇見到較難遇見排列；本表順序即 UI 固定順序。

| 排序 | 星級與名稱 | 中文名稱 | 英文名稱 | ID |
|---:|---|---|---|---|
| 1 | 1 星 常見 / Common | 英國短毛貓 | British Shorthair | british_shorthair |
| 2 | 1 星 常見 / Common | 美國短毛貓 | American Shorthair | american_shorthair |
| 3 | 1 星 常見 / Common | 蘇格蘭摺耳貓 | Scottish Fold | scottish_fold |
| 4 | 1 星 常見 / Common | 波斯貓 | Persian | persian |
| 5 | 2 星 少見 / Uncommon | 布偶貓 | Ragdoll | ragdoll |
| 6 | 2 星 少見 / Uncommon | 曼赤肯貓 | Munchkin | munchkin |
| 7 | 2 星 少見 / Uncommon | 暹羅貓 | Siamese | siamese |
| 8 | 2 星 少見 / Uncommon | 異國短毛貓 | Exotic Shorthair | exotic_shorthair |
| 9 | 3 星 稀有 / Rare | 緬因貓 | Maine Coon | maine_coon |
| 10 | 3 星 稀有 / Rare | 孟加拉貓 | Bengal | bengal |
| 11 | 3 星 稀有 / Rare | 俄羅斯藍貓 | Russian Blue | russian_blue |
| 12 | 3 星 稀有 / Rare | 挪威森林貓 | Norwegian Forest Cat | norwegian_forest_cat |
| 13 | 4 星 珍稀 / Epic | 斯芬克斯貓 | Sphynx | sphynx |
| 14 | 4 星 珍稀 / Epic | 阿比西尼亞貓 | Abyssinian | abyssinian |
| 15 | 4 星 珍稀 / Epic | 伯曼貓 | Birman | birman |
| 16 | 4 星 珍稀 / Epic | 土耳其安哥拉貓 | Turkish Angora | turkish_angora |
| 17 | 4 星 珍稀 / Epic | 東方短毛貓 | Oriental Shorthair | oriental_shorthair |
| 18 | 5 星 極稀有 / Legendary | 德文捲毛貓 | Devon Rex | devon_rex |
| 19 | 5 星 極稀有 / Legendary | 柯尼斯捲毛貓 | Cornish Rex | cornish_rex |
| 20 | 5 星 極稀有 / Legendary | 孟買貓 | Bombay | bombay |
| 21 | 5 星 極稀有 / Legendary | 埃及貓 | Egyptian Mau | egyptian_mau |
| 22 | 5 星 極稀有 / Legendary | 曼島貓 | Manx | manx |

## 品種狗完整排序

同星級由較常遇見到較難遇見排列；本表順序即 UI 固定順序。

| 排序 | 星級與名稱 | 中文名稱 | 英文名稱 | ID |
|---:|---|---|---|---|
| 1 | 1 星 常見 / Common | 貴賓犬 | Poodle | poodle |
| 2 | 1 星 常見 / Common | 馬爾濟斯 | Maltese | maltese |
| 3 | 1 星 常見 / Common | 博美犬 | Pomeranian | pomeranian |
| 4 | 1 星 常見 / Common | 柴犬 | Shiba Inu | shiba_inu |
| 5 | 1 星 常見 / Common | 柯基犬 | Corgi | corgi |
| 6 | 1 星 常見 / Common | 台灣犬 | Taiwan Dog | taiwan_dog |
| 7 | 1 星 常見 / Common | 黃金獵犬 | Golden Retriever | golden_retriever |
| 8 | 1 星 常見 / Common | 拉布拉多犬 | Labrador Retriever | labrador_retriever |
| 9 | 2 星 少見 / Uncommon | 雪納瑞 | Schnauzer | schnauzer |
| 10 | 2 星 少見 / Uncommon | 臘腸犬 | Dachshund | dachshund |
| 11 | 2 星 少見 / Uncommon | 法國鬥牛犬 | French Bulldog | french_bulldog |
| 12 | 2 星 少見 / Uncommon | 吉娃娃 | Chihuahua | chihuahua |
| 13 | 2 星 少見 / Uncommon | 西施犬 | Shih Tzu | shih_tzu |
| 14 | 2 星 少見 / Uncommon | 比熊犬 | Bichon Frise | bichon_frise |
| 15 | 2 星 少見 / Uncommon | 米格魯 | Beagle | beagle |
| 16 | 2 星 少見 / Uncommon | 約克夏 | Yorkshire Terrier | yorkshire_terrier |
| 17 | 3 星 稀有 / Rare | 邊境牧羊犬 | Border Collie | border_collie |
| 18 | 3 星 稀有 / Rare | 西伯利亞哈士奇 | Siberian Husky | siberian_husky |
| 19 | 3 星 稀有 / Rare | 巴哥犬 | Pug | pug |
| 20 | 3 星 稀有 / Rare | 日本狐狸犬 | Japanese Spitz | japanese_spitz |
| 21 | 3 星 稀有 / Rare | 薩摩耶 | Samoyed | samoyed |
| 22 | 3 星 稀有 / Rare | 喜樂蒂牧羊犬 | Shetland Sheepdog | shetland_sheepdog |
| 23 | 3 星 稀有 / Rare | 西高地白梗 | West Highland White Terrier | west_highland_white_terrier |
| 24 | 3 星 稀有 / Rare | 迷你杜賓犬 | Miniature Pinscher | miniature_pinscher |
| 25 | 4 星 珍稀 / Epic | 伯恩山犬 | Bernese Mountain Dog | bernese_mountain_dog |
| 26 | 4 星 珍稀 / Epic | 英國可卡犬 | English Cocker Spaniel | english_cocker_spaniel |
| 27 | 4 星 珍稀 / Epic | 美國比特鬥牛㹴 | American Pit Bull Terrier | american_pit_bull_terrier |
| 28 | 4 星 珍稀 / Epic | 拳師犬 | Boxer | boxer |
| 29 | 4 星 珍稀 / Epic | 蘇格蘭㹴 | Scottish Terrier | scottish_terrier |
| 30 | 4 星 珍稀 / Epic | 巴吉度獵犬 | Basset Hound | basset_hound |
| 31 | 5 星 極稀有 / Legendary | 聖伯納犬 | Saint Bernard | saint_bernard |
| 32 | 5 星 極稀有 / Legendary | 紐芬蘭犬 | Newfoundland | newfoundland |
| 33 | 5 星 極稀有 / Legendary | 美國鬥牛犬 | American Bulldog | american_bulldog |
| 34 | 5 星 極稀有 / Legendary | 斯塔福郡鬥牛㹴 | Staffordshire Bull Terrier | staffordshire_bull_terrier |
| 35 | 5 星 極稀有 / Legendary | 愛爾蘭軟毛麥色㹴 | Soft Coated Wheaten Terrier | soft_coated_wheaten_terrier |
| 36 | 5 星 極稀有 / Legendary | 德國短毛指示犬 | German Shorthaired Pointer | german_shorthaired_pointer |

## 新品種介紹來源

- [Bernese Mountain Dog — AKC](https://www.akc.org/dog-breeds/bernese-mountain-dog/)
- [Bichon Frise — AKC](https://www.akc.org/dog-breeds/bichon-frise/)
- [Dachshund — AKC](https://www.akc.org/dog-breeds/dachshund/)
- [Japanese Spitz — FCI standard](https://www.fci.be/nomenclature/Standards/262g05-en.pdf)
- [Shih Tzu — AKC](https://www.akc.org/dog-breeds/shih-tzu/)
- [Siberian Husky — AKC](https://www.akc.org/dog-breeds/siberian-husky/)
- [Taiwan Dog — FCI standard](https://fci.be/Nomenclature/Standards/348g05-en.pdf)
- [West Highland White Terrier — AKC](https://www.akc.org/dog-breeds/west-highland-white-terrier/)

新增介紹為獨立撰寫的中英文內容，維持原本中文 80～120 字、英文 60～90 字的測試約束。既有資料未全部重新產生。

## 修改檔案

模型資產（沿用使用者提供的原檔，未重新訓練或改寫內容）：

- `app/src/main/assets/dog_model.tflite`
- `app/src/main/assets/dog_labels.txt`

程式：

- `app/src/main/java/com/example/petcollection/BreedClassifier.kt`
- `app/src/main/java/com/example/petcollection/data/Breed.kt`
- `app/src/main/java/com/example/petcollection/data/BreedRarity.kt`（新增）
- `app/src/main/java/com/example/petcollection/data/MockBreedRepository.kt`
- `app/src/main/java/com/example/petcollection/ui/collection/BreedRarityBadge.kt`（新增）
- `app/src/main/java/com/example/petcollection/ui/collection/CollectionScreen.kt`
- `app/src/main/java/com/example/petcollection/ui/collection/BreedDetailScreen.kt`
- `app/src/main/java/com/example/petcollection/ui/collection/EncounterDialogs.kt`
- `app/src/main/java/com/example/petcollection/ui/home/HomePreviewData.kt`
- `app/src/main/java/com/example/petcollection/ui/home/HomeScreen.kt`

雙語資源：`values`、`values-b+zh+Hant`、`values-en` 各自的 `breed_catalog.xml` 與新增的 `rarity_strings.xml`（共六個檔案）。

測試：

- `app/src/test/java/com/example/petcollection/data/BreedIntroductionResourcesTest.kt`
- `app/src/test/java/com/example/petcollection/data/MockBreedRepositoryTest.kt`
- `app/src/test/java/com/example/petcollection/data/BreedRarityCatalogTest.kt`（新增）
- `app/src/test/java/com/example/petcollection/ui/collection/CollectionUnlockStateTest.kt`
- `app/src/androidTest/java/com/example/petcollection/CollectionPersistenceTest.kt`
- `app/src/androidTest/java/com/example/petcollection/DogModelUpdateTest.kt`（新增）
- `app/src/androidTest/java/com/example/petcollection/ui/CollectionNavigationTest.kt`
- `app/src/androidTest/java/com/example/petcollection/ui/collection/CollectionCrudUiTest.kt`
- `app/src/androidTest/java/com/example/petcollection/ui/collection/BreedRarityUiTest.kt`（新增）

文件：`docs/dog-model-update.md`（本報告）。

## 驗證結果

- `:app:assembleDebug`、`:app:assembleDebugAndroidTest`：成功。
- `:app:testDebugUnitTest`：36 項通過。
- `DogModelUpdateTest`：原生新狗模型實際推論、36 種標籤雙語對應、75% 門檻、收藏去重、照片與封面、六個舊版相簿及 Room 關閉重開持久化均通過。
- `BreedRarityUiTest`：卡片、詳細頁與拍立得的英文／繁體中文、日間／夜間組合，及舊版相簿導覽均通過。
- 原有 `BreedClassifierInferenceTest`：貓狗模型、第一階段分類與 URI 辨識收藏測試通過。
- 四張代表畫面已輸出至 `app/build/dog-rarity-*.png` 並檢查，未見文字溢出或解鎖前照片外洩。
- `git diff --check`：通過，只有使用者提供的 `dog_labels.txt` 有既有 Git 換行提示。

實作範圍內沒有編譯錯誤或未完成項目。稀有度是可在 `BreedRarity.kt` 調整的遊戲設計值，並非正式統計或模型判斷結果。測試使用獨立資料庫，未清除使用者收藏。
