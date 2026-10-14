package com.example.petcollection

import com.example.petcollection.ui.AchievementsUiTest
import com.example.petcollection.ui.BottomNavigationUiTest
import org.junit.runner.RunWith
import org.junit.runners.Suite

/** A single runner filter keeps the entire achievement/collection regression group together. */
@RunWith(Suite::class)
@Suite.SuiteClasses(AchievementPersistenceTest::class, AchievementsUiTest::class, BottomNavigationUiTest::class,
    CollectionPersistenceTest::class, OtherAlbumsTest::class, PhotoSortingPersistenceTest::class, DogModelUpdateTest::class)
class AchievementRegressionSuite
