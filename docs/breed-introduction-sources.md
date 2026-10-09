# 品種獨立介紹：編輯位置與參考來源

涵蓋既有圖鑑的 22 種貓、34 種狗。查閱日期：2026-10-10。介紹依品種資料整理後自行撰寫，沒有複製網站段落。

## 自行編輯

- 繁體中文：`app/src/main/res/values-b+zh+Hant/breed_catalog.xml`
- 英文：`app/src/main/res/values-en/breed_catalog.xml`
- 預設文字：`app/src/main/res/values/breed_catalog.xml`（新增介紹與繁中保持一致）

搜尋 `breed_<id>_introduction`，例如 `breed_abyssinian_introduction`。介紹集中在檔案末尾的新區段；不必改動模型 Labels 或品種 ID。繁中每段 80–120 字，英文每段 60–90 words；同步更新三份資源。Android 字串中的 ASCII 單引號與雙引號需要跳脫，`&`、`<` 必須使用 XML entity。

`MockBreedRepository.Entry.introduction` 明確對應資源，讀入既有 `Breed.description`；詳細頁「認識牠 / About this breed」沿用原來的顯示位置。起源、個性、體型與毛髮等基本特徵保持既有內容，猴子仍使用 `monkey_description`。

## 查核與撰寫範圍

- 貓優先採 CFA，並與 TICA 的品種介紹核對；曼赤肯使用 TICA，組織認可差異另核對 GCCF。
- 狗優先採 AKC，搭配 UKC 品種標準；美國比特鬥牛㹴採 UKC 與 ADBA，沒有以美國斯塔福郡㹴替代。
- Corgi 涵蓋彭布羅克／卡迪根；Schnauzer 涵蓋迷你／標準／巨型；Poodle 涵蓋標準／迷你／玩具。介紹保持既有概括分類，不新增分類或假定子品種。
- 伯曼、波斯、挪威森林貓等早期歷史不完全確定，文案保留不確定性。神話、浣熊混種說不當作史實；布偶貓不保證每隻都喜歡被抱。
- CFA 與 TICA 對 Devon Rex 的早期年份、Bombay 的培育起點有不同敘述，因此只採可一致確認的地區、親本與發展背景。介紹不承諾品種健康、低過敏或固定性格。
- 本次只新增介紹與更新「原型摘要」提示；不校訂使用者既有的名稱或基本特徵。舊有起源欄位若與新查核介紹不同，可由使用者另外校訂。

## 逐品種來源

| 品種 ID／介紹 key | 參考來源 |
|---|---|
| `abyssinian` / `breed_abyssinian_introduction` | [CFA](https://cfa.org/breed/abyssinian/)、[TICA](https://tica.org/breed/abyssinian/) |
| `american_shorthair` / `breed_american_shorthair_introduction` | [CFA](https://cfa.org/breed/american-shorthair/)、[TICA](https://tica.org/breed/american-shorthair/) |
| `bengal` / `breed_bengal_introduction` | [CFA](https://cfa.org/breed/bengal/)、[TICA](https://tica.org/breed/bengal/) |
| `birman` / `breed_birman_introduction` | [CFA](https://cfa.org/breed/birman/)、[TICA](https://tica.org/breed/birman/) |
| `bombay` / `breed_bombay_introduction` | [CFA](https://cfa.org/breed/bombay/)、[TICA](https://tica.org/breed/bombay/) |
| `british_shorthair` / `breed_british_shorthair_introduction` | [CFA](https://cfa.org/breed/british-shorthair/)、[TICA](https://tica.org/breed/british-shorthair/) |
| `cornish_rex` / `breed_cornish_rex_introduction` | [CFA](https://cfa.org/breed/cornish-rex/)、[TICA](https://tica.org/breed/cornish-rex/) |
| `devon_rex` / `breed_devon_rex_introduction` | [CFA](https://cfa.org/breed/devon-rex/)、[TICA](https://tica.org/breed/devon-rex/) |
| `egyptian_mau` / `breed_egyptian_mau_introduction` | [CFA](https://cfa.org/breed/egyptian-mau/)、[TICA](https://tica.org/breed/egyptian-mau/) |
| `exotic_shorthair` / `breed_exotic_shorthair_introduction` | [CFA](https://cfa.org/breed/exotic/)、[TICA](https://tica.org/breed/exotic-shorthair/) |
| `maine_coon` / `breed_maine_coon_introduction` | [CFA](https://cfa.org/breed/maine-coon-cat/)、[TICA](https://tica.org/breed/maine-coon/) |
| `manx` / `breed_manx_introduction` | [CFA](https://cfa.org/breed/manx/)、[TICA](https://tica.org/breed/manx/) |
| `munchkin` / `breed_munchkin_introduction` | [TICA](https://tica.org/breed/munchkin/)、[GCCF recognition policy](https://www.gccfcats.org/wp-content/uploads/2024/11/Unrecognised-Breeds-11.2024.pdf) |
| `norwegian_forest_cat` / `breed_norwegian_forest_cat_introduction` | [CFA](https://cfa.org/breed/norwegian-forest-cat/)、[TICA](https://tica.org/breed/norwegian-forest/) |
| `oriental_shorthair` / `breed_oriental_shorthair_introduction` | [CFA](https://cfa.org/breed/oriental/)、[TICA](https://tica.org/breed/oriental-shorthair/) |
| `persian` / `breed_persian_introduction` | [CFA](https://cfa.org/breed/persian/)、[TICA](https://tica.org/breed/persian/) |
| `ragdoll` / `breed_ragdoll_introduction` | [CFA](https://cfa.org/breed/ragdoll/)、[TICA](https://tica.org/breed/ragdoll/) |
| `russian_blue` / `breed_russian_blue_introduction` | [CFA](https://cfa.org/breed/russian-blue/)、[TICA](https://tica.org/breed/russian-blue/) |
| `scottish_fold` / `breed_scottish_fold_introduction` | [CFA](https://cfa.org/breed/scottish-fold/)、[TICA](https://tica.org/breed/scottish-fold/) |
| `siamese` / `breed_siamese_introduction` | [CFA](https://cfa.org/breed/siamese/)、[TICA](https://tica.org/breed/siamese/) |
| `sphynx` / `breed_sphynx_introduction` | [CFA](https://cfa.org/breed/sphynx/)、[TICA](https://tica.org/breed/sphynx/) |
| `turkish_angora` / `breed_turkish_angora_introduction` | [CFA](https://cfa.org/breed/turkish-angora/)、[TICA](https://tica.org/breed/turkish-angora/) |
| `american_bulldog` / `breed_american_bulldog_introduction` | [AKC](https://www.akc.org/dog-breeds/american-bulldog/)、[UKC](https://www.ukcdogs.com/american-bulldog) |
| `american_pit_bull_terrier` / `breed_american_pit_bull_terrier_introduction` | [UKC](https://www.ukcdogs.com/american-pit-bull-terrier)、[ADBA](https://adbadog.com/heritage-american-pit-bull-terrier-conformation-standard/) |
| `basset_hound` / `breed_basset_hound_introduction` | [AKC](https://www.akc.org/dog-breeds/basset-hound/)、[UKC](https://www.ukcdogs.com/basset-hound) |
| `beagle` / `breed_beagle_introduction` | [AKC](https://www.akc.org/dog-breeds/beagle/)、[UKC](https://www.ukcdogs.com/beagle) |
| `border_collie` / `breed_border_collie_introduction` | [AKC](https://www.akc.org/dog-breeds/border-collie/)、[UKC](https://www.ukcdogs.com/border-collie) |
| `boxer` / `breed_boxer_introduction` | [AKC](https://www.akc.org/dog-breeds/boxer/)、[UKC](https://www.ukcdogs.com/boxer) |
| `chihuahua` / `breed_chihuahua_introduction` | [AKC](https://www.akc.org/dog-breeds/chihuahua/)、[UKC](https://www.ukcdogs.com/chihuahua) |
| `corgi` / `breed_corgi_introduction` | [AKC](https://www.akc.org/dog-breeds/pembroke-welsh-corgi/)、[UKC](https://www.ukcdogs.com/corgi)、[AKC Cardigan](https://www.akc.org/dog-breeds/cardigan-welsh-corgi/)、[UKC Cardigan](https://www.ukcdogs.com/cardigan-welsh-corgi) |
| `english_cocker_spaniel` / `breed_english_cocker_spaniel_introduction` | [AKC](https://www.akc.org/dog-breeds/english-cocker-spaniel/)、[UKC](https://www.ukcdogs.com/english-cocker-spaniel) |
| `english_setter` / `breed_english_setter_introduction` | [AKC](https://www.akc.org/dog-breeds/english-setter/)、[UKC](https://www.ukcdogs.com/english-setter) |
| `french_bulldog` / `breed_french_bulldog_introduction` | [AKC](https://www.akc.org/dog-breeds/french-bulldog/)、[UKC](https://www.ukcdogs.com/french-bulldog) |
| `german_shorthaired_pointer` / `breed_german_shorthaired_pointer_introduction` | [AKC](https://www.akc.org/dog-breeds/german-shorthaired-pointer/)、[UKC](https://www.ukcdogs.com/german-shorthaired-pointer) |
| `golden_retriever` / `breed_golden_retriever_introduction` | [AKC](https://www.akc.org/dog-breeds/golden-retriever/)、[UKC](https://www.ukcdogs.com/golden-retriever) |
| `great_pyrenees` / `breed_great_pyrenees_introduction` | [AKC](https://www.akc.org/dog-breeds/great-pyrenees/)、[UKC](https://www.ukcdogs.com/great-pyrenees) |
| `havanese` / `breed_havanese_introduction` | [AKC](https://www.akc.org/dog-breeds/havanese/)、[UKC](https://www.ukcdogs.com/havanese) |
| `japanese_chin` / `breed_japanese_chin_introduction` | [AKC](https://www.akc.org/dog-breeds/japanese-chin/)、[UKC](https://www.ukcdogs.com/japanese-chin) |
| `keeshond` / `breed_keeshond_introduction` | [AKC](https://www.akc.org/dog-breeds/keeshond/)、[UKC](https://www.ukcdogs.com/keeshond) |
| `labrador_retriever` / `breed_labrador_retriever_introduction` | [AKC](https://www.akc.org/dog-breeds/labrador-retriever/)、[UKC](https://www.ukcdogs.com/labrador-retriever) |
| `leonberger` / `breed_leonberger_introduction` | [AKC](https://www.akc.org/dog-breeds/leonberger/)、[UKC](https://www.ukcdogs.com/leonberger) |
| `maltese` / `breed_maltese_introduction` | [AKC](https://www.akc.org/dog-breeds/maltese/)、[UKC](https://www.ukcdogs.com/maltese) |
| `miniature_pinscher` / `breed_miniature_pinscher_introduction` | [AKC](https://www.akc.org/dog-breeds/miniature-pinscher/)、[Miniature Pinscher Club of America / AKC](https://cdn.akc.org/MiniaturePinscher-club-flier.pdf)、[AKC history](https://www.akc.org/expert-advice/dog-breeds/miniature-pinscher-history/) |
| `newfoundland` / `breed_newfoundland_introduction` | [AKC](https://www.akc.org/dog-breeds/newfoundland/)、[UKC](https://www.ukcdogs.com/newfoundland) |
| `pomeranian` / `breed_pomeranian_introduction` | [AKC](https://www.akc.org/dog-breeds/pomeranian/)、[UKC](https://www.ukcdogs.com/pomeranian) |
| `poodle` / `breed_poodle_introduction` | [AKC](https://www.akc.org/dog-breeds/poodle-standard/)、[UKC](https://www.ukcdogs.com/standard-poodle)、[AKC Miniature](https://www.akc.org/dog-breeds/poodle-miniature/)、[AKC Toy](https://www.akc.org/dog-breeds/poodle-toy/)、[AKC history](https://www.akc.org/expert-advice/dog-breeds/poodle-history/) |
| `pug` / `breed_pug_introduction` | [AKC](https://www.akc.org/dog-breeds/pug/)、[UKC](https://www.ukcdogs.com/pug) |
| `saint_bernard` / `breed_saint_bernard_introduction` | [AKC](https://www.akc.org/dog-breeds/st-bernard/)、[UKC](https://www.ukcdogs.com/saint-bernard) |
| `samoyed` / `breed_samoyed_introduction` | [AKC](https://www.akc.org/dog-breeds/samoyed/)、[UKC](https://www.ukcdogs.com/samoyed) |
| `schnauzer` / `breed_schnauzer_introduction` | [AKC](https://www.akc.org/dog-breeds/standard-schnauzer/)、[UKC](https://www.ukcdogs.com/standard-schnauzer)、[AKC Miniature](https://www.akc.org/dog-breeds/miniature-schnauzer/)、[AKC Giant](https://www.akc.org/dog-breeds/giant-schnauzer/) |
| `scottish_terrier` / `breed_scottish_terrier_introduction` | [AKC](https://www.akc.org/dog-breeds/scottish-terrier/)、[UKC](https://www.ukcdogs.com/scottish-terrier) |
| `shetland_sheepdog` / `breed_shetland_sheepdog_introduction` | [AKC](https://www.akc.org/dog-breeds/shetland-sheepdog/)、[UKC](https://www.ukcdogs.com/shetland-sheepdog) |
| `shiba_inu` / `breed_shiba_inu_introduction` | [AKC](https://www.akc.org/dog-breeds/shiba-inu/)、[UKC](https://www.ukcdogs.com/shiba) |
| `staffordshire_bull_terrier` / `breed_staffordshire_bull_terrier_introduction` | [AKC](https://www.akc.org/dog-breeds/staffordshire-bull-terrier/)、[UKC](https://www.ukcdogs.com/staffordshire-bull-terrier) |
| `soft_coated_wheaten_terrier` / `breed_soft_coated_wheaten_terrier_introduction` | [AKC](https://www.akc.org/dog-breeds/soft-coated-wheaten-terrier/)、[UKC](https://www.ukcdogs.com/soft-coated-wheaten-terrier) |
| `yorkshire_terrier` / `breed_yorkshire_terrier_introduction` | [AKC](https://www.akc.org/dog-breeds/yorkshire-terrier/)、[UKC](https://www.ukcdogs.com/yorkshire-terrier) |

補充查核：[English Setter 花紋](https://www.akc.org/expert-advice/lifestyle/fun-facts-english-setter/)、[Basset Hound 工作背景](https://www.akc.org/expert-advice/dog-breeds/basset-hound-history/)、[Newfoundland 水上工作](https://www.akc.org/expert-advice/news/lifesaving-water-rescue/)。

