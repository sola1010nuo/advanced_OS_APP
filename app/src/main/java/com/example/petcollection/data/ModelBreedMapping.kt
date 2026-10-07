package com.example.petcollection.data

import java.util.Locale

/** Model aliases resolve into the existing catalog, never into a second breed list. */
object ModelBreedMapping {
    fun canonicalId(label: String): String {
        val normalized = label.trim().lowercase(Locale.ROOT).replace(Regex("[\\s_-]+"), "_")
        return when (normalized) {
            "norwegian_forest" -> "norwegian_forest_cat"
            "german_shorthaired" -> "german_shorthaired_pointer"
            "wheaten_terrier" -> "soft_coated_wheaten_terrier"
            else -> normalized
        }
    }

    fun resolve(label: String, species: Species, breeds: List<Breed>): Breed? =
        breeds.firstOrNull { it.id == canonicalId(label) && it.species == species }
}
