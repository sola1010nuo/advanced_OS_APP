package com.example.petcollection.data

import android.content.res.Resources
import androidx.annotation.StringRes
import com.example.petcollection.R

/** Resource-backed breed catalog; encounter and cover state live in Room. */
class MockBreedRepository(private val resources: Resources) : BreedRepository {
    override fun getCollectionEntries(): List<Breed> = getBreeds() + listOf(
        category("other_cat", Species.CAT, R.string.other_cats, R.string.other_cats_description),
        category("other_dog", Species.DOG, R.string.other_dogs, R.string.other_dogs_description),
        category("unregistered", null, R.string.unregistered, R.string.unregistered_description)
    )

    private fun category(id: String, species: Species?, name: Int, description: Int) = Breed(
        id, resources.getString(name), species,
        if (species != null) resources.getString(description, total(species)) else resources.getString(description),
        "", "", "", "",
        isCollectible = false
    )
    override fun getBreeds(): List<Breed> = entries.map { entry ->
        val name = resources.getString(entry.name)
        val coat = resources.getString(entry.coat)
        val personality = resources.getString(entry.personality)
        Breed(
            id = entry.id, name = name, species = entry.species,
            description = resources.getString(entry.introduction),
            origin = resources.getString(entry.origin), personality = personality,
            size = resources.getString(entry.size), coat = coat, isHiddenUntilDiscovered = entry.hidden
        )
    }

    private data class Entry(
        val id: String, val species: Species?,
        @param:StringRes val name: Int, @param:StringRes val origin: Int,
        @param:StringRes val personality: Int, @param:StringRes val size: Int,
        @param:StringRes val coat: Int, @param:StringRes val introduction: Int,
        val hidden: Boolean = false
    )

    companion object {
        const val MONKEY_ID = "monkey"
        val normalBreedIds: Set<String> get() = entries.map { it.id }.toSet()
        val collectionIds: Set<String> get() = normalBreedIds + setOf("other_cat", "other_dog", "unregistered")
        fun total(species: Species): Int = entries.count { it.species == species }

        private val entries = listOf(
            Entry(MONKEY_ID, null, R.string.monkey_name, R.string.monkey_origin,
                R.string.monkey_personality, R.string.monkey_size, R.string.monkey_coat,
                R.string.monkey_description, hidden = true),
            Entry("abyssinian", Species.CAT, R.string.breed_abyssinian_name, R.string.breed_abyssinian_origin, R.string.breed_abyssinian_personality, R.string.breed_abyssinian_size, R.string.breed_abyssinian_coat, R.string.breed_abyssinian_introduction),
            Entry("american_shorthair", Species.CAT, R.string.breed_american_shorthair_name, R.string.breed_american_shorthair_origin, R.string.breed_american_shorthair_personality, R.string.breed_american_shorthair_size, R.string.breed_american_shorthair_coat, R.string.breed_american_shorthair_introduction),
            Entry("bengal", Species.CAT, R.string.breed_bengal_name, R.string.breed_bengal_origin, R.string.breed_bengal_personality, R.string.breed_bengal_size, R.string.breed_bengal_coat, R.string.breed_bengal_introduction),
            Entry("birman", Species.CAT, R.string.breed_birman_name, R.string.breed_birman_origin, R.string.breed_birman_personality, R.string.breed_birman_size, R.string.breed_birman_coat, R.string.breed_birman_introduction),
            Entry("bombay", Species.CAT, R.string.breed_bombay_name, R.string.breed_bombay_origin, R.string.breed_bombay_personality, R.string.breed_bombay_size, R.string.breed_bombay_coat, R.string.breed_bombay_introduction),
            Entry("british_shorthair", Species.CAT, R.string.breed_british_shorthair_name, R.string.breed_british_shorthair_origin, R.string.breed_british_shorthair_personality, R.string.breed_british_shorthair_size, R.string.breed_british_shorthair_coat, R.string.breed_british_shorthair_introduction),
            Entry("cornish_rex", Species.CAT, R.string.breed_cornish_rex_name, R.string.breed_cornish_rex_origin, R.string.breed_cornish_rex_personality, R.string.breed_cornish_rex_size, R.string.breed_cornish_rex_coat, R.string.breed_cornish_rex_introduction),
            Entry("devon_rex", Species.CAT, R.string.breed_devon_rex_name, R.string.breed_devon_rex_origin, R.string.breed_devon_rex_personality, R.string.breed_devon_rex_size, R.string.breed_devon_rex_coat, R.string.breed_devon_rex_introduction),
            Entry("egyptian_mau", Species.CAT, R.string.breed_egyptian_mau_name, R.string.breed_egyptian_mau_origin, R.string.breed_egyptian_mau_personality, R.string.breed_egyptian_mau_size, R.string.breed_egyptian_mau_coat, R.string.breed_egyptian_mau_introduction),
            Entry("exotic_shorthair", Species.CAT, R.string.breed_exotic_shorthair_name, R.string.breed_exotic_shorthair_origin, R.string.breed_exotic_shorthair_personality, R.string.breed_exotic_shorthair_size, R.string.breed_exotic_shorthair_coat, R.string.breed_exotic_shorthair_introduction),
            Entry("maine_coon", Species.CAT, R.string.breed_maine_coon_name, R.string.breed_maine_coon_origin, R.string.breed_maine_coon_personality, R.string.breed_maine_coon_size, R.string.breed_maine_coon_coat, R.string.breed_maine_coon_introduction),
            Entry("manx", Species.CAT, R.string.breed_manx_name, R.string.breed_manx_origin, R.string.breed_manx_personality, R.string.breed_manx_size, R.string.breed_manx_coat, R.string.breed_manx_introduction),
            Entry("munchkin", Species.CAT, R.string.breed_munchkin_name, R.string.breed_munchkin_origin, R.string.breed_munchkin_personality, R.string.breed_munchkin_size, R.string.breed_munchkin_coat, R.string.breed_munchkin_introduction),
            Entry("norwegian_forest_cat", Species.CAT, R.string.breed_norwegian_forest_cat_name, R.string.breed_norwegian_forest_cat_origin, R.string.breed_norwegian_forest_cat_personality, R.string.breed_norwegian_forest_cat_size, R.string.breed_norwegian_forest_cat_coat, R.string.breed_norwegian_forest_cat_introduction),
            Entry("oriental_shorthair", Species.CAT, R.string.breed_oriental_shorthair_name, R.string.breed_oriental_shorthair_origin, R.string.breed_oriental_shorthair_personality, R.string.breed_oriental_shorthair_size, R.string.breed_oriental_shorthair_coat, R.string.breed_oriental_shorthair_introduction),
            Entry("persian", Species.CAT, R.string.breed_persian_name, R.string.breed_persian_origin, R.string.breed_persian_personality, R.string.breed_persian_size, R.string.breed_persian_coat, R.string.breed_persian_introduction),
            Entry("ragdoll", Species.CAT, R.string.breed_ragdoll_name, R.string.breed_ragdoll_origin, R.string.breed_ragdoll_personality, R.string.breed_ragdoll_size, R.string.breed_ragdoll_coat, R.string.breed_ragdoll_introduction),
            Entry("russian_blue", Species.CAT, R.string.breed_russian_blue_name, R.string.breed_russian_blue_origin, R.string.breed_russian_blue_personality, R.string.breed_russian_blue_size, R.string.breed_russian_blue_coat, R.string.breed_russian_blue_introduction),
            Entry("scottish_fold", Species.CAT, R.string.breed_scottish_fold_name, R.string.breed_scottish_fold_origin, R.string.breed_scottish_fold_personality, R.string.breed_scottish_fold_size, R.string.breed_scottish_fold_coat, R.string.breed_scottish_fold_introduction),
            Entry("siamese", Species.CAT, R.string.breed_siamese_name, R.string.breed_siamese_origin, R.string.breed_siamese_personality, R.string.breed_siamese_size, R.string.breed_siamese_coat, R.string.breed_siamese_introduction),
            Entry("sphynx", Species.CAT, R.string.breed_sphynx_name, R.string.breed_sphynx_origin, R.string.breed_sphynx_personality, R.string.breed_sphynx_size, R.string.breed_sphynx_coat, R.string.breed_sphynx_introduction),
            Entry("turkish_angora", Species.CAT, R.string.breed_turkish_angora_name, R.string.breed_turkish_angora_origin, R.string.breed_turkish_angora_personality, R.string.breed_turkish_angora_size, R.string.breed_turkish_angora_coat, R.string.breed_turkish_angora_introduction),
            Entry("american_bulldog", Species.DOG, R.string.breed_american_bulldog_name, R.string.breed_american_bulldog_origin, R.string.breed_american_bulldog_personality, R.string.breed_american_bulldog_size, R.string.breed_american_bulldog_coat, R.string.breed_american_bulldog_introduction),
            Entry("american_pit_bull_terrier", Species.DOG, R.string.breed_american_pit_bull_terrier_name, R.string.breed_american_pit_bull_terrier_origin, R.string.breed_american_pit_bull_terrier_personality, R.string.breed_american_pit_bull_terrier_size, R.string.breed_american_pit_bull_terrier_coat, R.string.breed_american_pit_bull_terrier_introduction),
            Entry("basset_hound", Species.DOG, R.string.breed_basset_hound_name, R.string.breed_basset_hound_origin, R.string.breed_basset_hound_personality, R.string.breed_basset_hound_size, R.string.breed_basset_hound_coat, R.string.breed_basset_hound_introduction),
            Entry("beagle", Species.DOG, R.string.breed_beagle_name, R.string.breed_beagle_origin, R.string.breed_beagle_personality, R.string.breed_beagle_size, R.string.breed_beagle_coat, R.string.breed_beagle_introduction),
            Entry("border_collie", Species.DOG, R.string.breed_border_collie_name, R.string.breed_border_collie_origin, R.string.breed_border_collie_personality, R.string.breed_border_collie_size, R.string.breed_border_collie_coat, R.string.breed_border_collie_introduction),
            Entry("boxer", Species.DOG, R.string.breed_boxer_name, R.string.breed_boxer_origin, R.string.breed_boxer_personality, R.string.breed_boxer_size, R.string.breed_boxer_coat, R.string.breed_boxer_introduction),
            Entry("chihuahua", Species.DOG, R.string.breed_chihuahua_name, R.string.breed_chihuahua_origin, R.string.breed_chihuahua_personality, R.string.breed_chihuahua_size, R.string.breed_chihuahua_coat, R.string.breed_chihuahua_introduction),
            Entry("corgi", Species.DOG, R.string.breed_corgi_name, R.string.breed_corgi_origin, R.string.breed_corgi_personality, R.string.breed_corgi_size, R.string.breed_corgi_coat, R.string.breed_corgi_introduction),
            Entry("english_cocker_spaniel", Species.DOG, R.string.breed_english_cocker_spaniel_name, R.string.breed_english_cocker_spaniel_origin, R.string.breed_english_cocker_spaniel_personality, R.string.breed_english_cocker_spaniel_size, R.string.breed_english_cocker_spaniel_coat, R.string.breed_english_cocker_spaniel_introduction),
            Entry("english_setter", Species.DOG, R.string.breed_english_setter_name, R.string.breed_english_setter_origin, R.string.breed_english_setter_personality, R.string.breed_english_setter_size, R.string.breed_english_setter_coat, R.string.breed_english_setter_introduction),
            Entry("french_bulldog", Species.DOG, R.string.breed_french_bulldog_name, R.string.breed_french_bulldog_origin, R.string.breed_french_bulldog_personality, R.string.breed_french_bulldog_size, R.string.breed_french_bulldog_coat, R.string.breed_french_bulldog_introduction),
            Entry("german_shorthaired_pointer", Species.DOG, R.string.breed_german_shorthaired_pointer_name, R.string.breed_german_shorthaired_pointer_origin, R.string.breed_german_shorthaired_pointer_personality, R.string.breed_german_shorthaired_pointer_size, R.string.breed_german_shorthaired_pointer_coat, R.string.breed_german_shorthaired_pointer_introduction),
            Entry("golden_retriever", Species.DOG, R.string.breed_golden_retriever_name, R.string.breed_golden_retriever_origin, R.string.breed_golden_retriever_personality, R.string.breed_golden_retriever_size, R.string.breed_golden_retriever_coat, R.string.breed_golden_retriever_introduction),
            Entry("great_pyrenees", Species.DOG, R.string.breed_great_pyrenees_name, R.string.breed_great_pyrenees_origin, R.string.breed_great_pyrenees_personality, R.string.breed_great_pyrenees_size, R.string.breed_great_pyrenees_coat, R.string.breed_great_pyrenees_introduction),
            Entry("havanese", Species.DOG, R.string.breed_havanese_name, R.string.breed_havanese_origin, R.string.breed_havanese_personality, R.string.breed_havanese_size, R.string.breed_havanese_coat, R.string.breed_havanese_introduction),
            Entry("japanese_chin", Species.DOG, R.string.breed_japanese_chin_name, R.string.breed_japanese_chin_origin, R.string.breed_japanese_chin_personality, R.string.breed_japanese_chin_size, R.string.breed_japanese_chin_coat, R.string.breed_japanese_chin_introduction),
            Entry("keeshond", Species.DOG, R.string.breed_keeshond_name, R.string.breed_keeshond_origin, R.string.breed_keeshond_personality, R.string.breed_keeshond_size, R.string.breed_keeshond_coat, R.string.breed_keeshond_introduction),
            Entry("labrador_retriever", Species.DOG, R.string.breed_labrador_retriever_name, R.string.breed_labrador_retriever_origin, R.string.breed_labrador_retriever_personality, R.string.breed_labrador_retriever_size, R.string.breed_labrador_retriever_coat, R.string.breed_labrador_retriever_introduction),
            Entry("leonberger", Species.DOG, R.string.breed_leonberger_name, R.string.breed_leonberger_origin, R.string.breed_leonberger_personality, R.string.breed_leonberger_size, R.string.breed_leonberger_coat, R.string.breed_leonberger_introduction),
            Entry("maltese", Species.DOG, R.string.breed_maltese_name, R.string.breed_maltese_origin, R.string.breed_maltese_personality, R.string.breed_maltese_size, R.string.breed_maltese_coat, R.string.breed_maltese_introduction),
            Entry("miniature_pinscher", Species.DOG, R.string.breed_miniature_pinscher_name, R.string.breed_miniature_pinscher_origin, R.string.breed_miniature_pinscher_personality, R.string.breed_miniature_pinscher_size, R.string.breed_miniature_pinscher_coat, R.string.breed_miniature_pinscher_introduction),
            Entry("newfoundland", Species.DOG, R.string.breed_newfoundland_name, R.string.breed_newfoundland_origin, R.string.breed_newfoundland_personality, R.string.breed_newfoundland_size, R.string.breed_newfoundland_coat, R.string.breed_newfoundland_introduction),
            Entry("pomeranian", Species.DOG, R.string.breed_pomeranian_name, R.string.breed_pomeranian_origin, R.string.breed_pomeranian_personality, R.string.breed_pomeranian_size, R.string.breed_pomeranian_coat, R.string.breed_pomeranian_introduction),
            Entry("poodle", Species.DOG, R.string.breed_poodle_name, R.string.breed_poodle_origin, R.string.breed_poodle_personality, R.string.breed_poodle_size, R.string.breed_poodle_coat, R.string.breed_poodle_introduction),
            Entry("pug", Species.DOG, R.string.breed_pug_name, R.string.breed_pug_origin, R.string.breed_pug_personality, R.string.breed_pug_size, R.string.breed_pug_coat, R.string.breed_pug_introduction),
            Entry("saint_bernard", Species.DOG, R.string.breed_saint_bernard_name, R.string.breed_saint_bernard_origin, R.string.breed_saint_bernard_personality, R.string.breed_saint_bernard_size, R.string.breed_saint_bernard_coat, R.string.breed_saint_bernard_introduction),
            Entry("samoyed", Species.DOG, R.string.breed_samoyed_name, R.string.breed_samoyed_origin, R.string.breed_samoyed_personality, R.string.breed_samoyed_size, R.string.breed_samoyed_coat, R.string.breed_samoyed_introduction),
            Entry("schnauzer", Species.DOG, R.string.breed_schnauzer_name, R.string.breed_schnauzer_origin, R.string.breed_schnauzer_personality, R.string.breed_schnauzer_size, R.string.breed_schnauzer_coat, R.string.breed_schnauzer_introduction),
            Entry("scottish_terrier", Species.DOG, R.string.breed_scottish_terrier_name, R.string.breed_scottish_terrier_origin, R.string.breed_scottish_terrier_personality, R.string.breed_scottish_terrier_size, R.string.breed_scottish_terrier_coat, R.string.breed_scottish_terrier_introduction),
            Entry("shetland_sheepdog", Species.DOG, R.string.breed_shetland_sheepdog_name, R.string.breed_shetland_sheepdog_origin, R.string.breed_shetland_sheepdog_personality, R.string.breed_shetland_sheepdog_size, R.string.breed_shetland_sheepdog_coat, R.string.breed_shetland_sheepdog_introduction),
            Entry("shiba_inu", Species.DOG, R.string.breed_shiba_inu_name, R.string.breed_shiba_inu_origin, R.string.breed_shiba_inu_personality, R.string.breed_shiba_inu_size, R.string.breed_shiba_inu_coat, R.string.breed_shiba_inu_introduction),
            Entry("staffordshire_bull_terrier", Species.DOG, R.string.breed_staffordshire_bull_terrier_name, R.string.breed_staffordshire_bull_terrier_origin, R.string.breed_staffordshire_bull_terrier_personality, R.string.breed_staffordshire_bull_terrier_size, R.string.breed_staffordshire_bull_terrier_coat, R.string.breed_staffordshire_bull_terrier_introduction),
            Entry("soft_coated_wheaten_terrier", Species.DOG, R.string.breed_soft_coated_wheaten_terrier_name, R.string.breed_soft_coated_wheaten_terrier_origin, R.string.breed_soft_coated_wheaten_terrier_personality, R.string.breed_soft_coated_wheaten_terrier_size, R.string.breed_soft_coated_wheaten_terrier_coat, R.string.breed_soft_coated_wheaten_terrier_introduction),
            Entry("yorkshire_terrier", Species.DOG, R.string.breed_yorkshire_terrier_name, R.string.breed_yorkshire_terrier_origin, R.string.breed_yorkshire_terrier_personality, R.string.breed_yorkshire_terrier_size, R.string.breed_yorkshire_terrier_coat, R.string.breed_yorkshire_terrier_introduction),
        )
    }
}
