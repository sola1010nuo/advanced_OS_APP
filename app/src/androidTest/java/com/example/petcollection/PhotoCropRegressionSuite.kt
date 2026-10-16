package com.example.petcollection

import com.example.petcollection.ui.PhotoCropUiTest
import com.example.petcollection.ui.collection.PhotoSortingUiTest
import com.example.petcollection.ui.collection.MonkeyPresentationTest
import com.example.petcollection.ui.effects.RecognitionEffectsUiTest
import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(Suite::class)
@Suite.SuiteClasses(PhotoCropIOTest::class, PhotoCropPersistenceTest::class, PhotoCropUiTest::class,
    AchievementRegressionSuite::class, PhotoSortingUiTest::class, MonkeyPresentationTest::class, RecognitionEffectsUiTest::class)
class PhotoCropRegressionSuite
