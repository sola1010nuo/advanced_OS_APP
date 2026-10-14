package com.example.petcollection.data

import androidx.annotation.StringRes
import com.example.petcollection.R

enum class BreedRarity(val stars: Int, @param:StringRes val labelRes: Int) {
    COMMON(1, R.string.rarity_common), UNCOMMON(2, R.string.rarity_uncommon),
    RARE(3, R.string.rarity_rare), EPIC(4, R.string.rarity_epic), LEGENDARY(5, R.string.rarity_legendary)
}

data class BreedRarityProfile(val rarity: BreedRarity, val order: Int)

/** Authored game balance for everyday encounters in Taiwan, not population statistics.
 * Order within each tier is fixed and independent of labels, language or discovery state. */
object BreedRarityCatalog {
    private fun tiers(vararg groups: Pair<BreedRarity, List<String>>): Map<String, BreedRarityProfile> =
        groups.flatMap { (rarity, ids) -> ids.mapIndexed { index, id -> id to BreedRarityProfile(rarity, index) } }.toMap()

    val cats = tiers(
        BreedRarity.COMMON to listOf("british_shorthair", "american_shorthair", "scottish_fold", "persian"),
        BreedRarity.UNCOMMON to listOf("ragdoll", "munchkin", "siamese", "exotic_shorthair"),
        BreedRarity.RARE to listOf("maine_coon", "bengal", "russian_blue", "norwegian_forest_cat"),
        BreedRarity.EPIC to listOf("sphynx", "abyssinian", "birman", "turkish_angora", "oriental_shorthair"),
        BreedRarity.LEGENDARY to listOf("devon_rex", "cornish_rex", "bombay", "egyptian_mau", "manx")
    )
    val dogs = tiers(
        BreedRarity.COMMON to listOf("poodle", "maltese", "pomeranian", "shiba_inu", "corgi", "taiwan_dog", "golden_retriever", "labrador_retriever"),
        BreedRarity.UNCOMMON to listOf("schnauzer", "dachshund", "french_bulldog", "chihuahua", "shih_tzu", "bichon_frise", "beagle", "yorkshire_terrier"),
        BreedRarity.RARE to listOf("border_collie", "siberian_husky", "pug", "japanese_spitz", "samoyed", "shetland_sheepdog", "west_highland_white_terrier", "miniature_pinscher"),
        BreedRarity.EPIC to listOf("bernese_mountain_dog", "english_cocker_spaniel", "american_pit_bull_terrier", "boxer", "scottish_terrier", "basset_hound"),
        BreedRarity.LEGENDARY to listOf("saint_bernard", "newfoundland", "american_bulldog", "staffordshire_bull_terrier", "soft_coated_wheaten_terrier", "german_shorthaired_pointer")
    )
    fun profile(id: String) = cats[id] ?: dogs[id]
    val order: Comparator<Breed> = compareBy<Breed> { it.rarity?.stars ?: Int.MAX_VALUE }.thenBy { it.rarityOrder }
}
