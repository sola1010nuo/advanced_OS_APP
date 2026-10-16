package com.example.petcollection

import com.example.petcollection.ui.PhotoCropUiTest
import com.example.petcollection.ui.PhotoCropLocalizationTest
import com.example.petcollection.ui.FreeCoverCropUiTest
import com.example.petcollection.ui.collection.CollectionCrudUiTest
import com.example.petcollection.ui.collection.PhotoSortingUiTest
import com.example.petcollection.ui.collection.MonkeyPresentationTest
import com.example.petcollection.ui.effects.RecognitionEffectsUiTest
import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(Suite::class)
@Suite.SuiteClasses(PhotoCropFeatureSuite::class,
    AchievementRegressionSuite::class, PhotoSortingUiTest::class, MonkeyPresentationTest::class, RecognitionEffectsUiTest::class)
class PhotoCropRegressionSuite

@RunWith(Suite::class)
@Suite.SuiteClasses(PhotoCropIOTest::class, PhotoCropPersistenceTest::class, PhotoCropUiTest::class,
    PhotoCropLocalizationTest::class, FreeCoverCropUiTest::class, CoverCropMigrationTest::class)
class PhotoCropFeatureSuite

@RunWith(Suite::class)
@Suite.SuiteClasses(AchievementPersistenceTest::class, PhotoCropLocalizationTest::class, CollectionCrudUiTest::class)
class CoverCropIntegrationSuite
