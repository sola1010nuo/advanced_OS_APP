package com.example.petcollection.data

/** Photo references can later come from app storage or a Room encounter record. */
data class BreedPhoto(val id: String, val uri: String? = null)
enum class Species { CAT, DOG }

data class Breed(
    val id: String,
    val name: String,
    val species: Species?,
    val description: String,
    val origin: String,
    val personality: String,
    val size: String,
    val coat: String,
    val lifespan: String? = null,
    val isDiscovered: Boolean = false,
    val encounterCount: Int = 0,
    val coverPhoto: BreedPhoto? = null,
    val collectedPhotos: List<BreedPhoto> = emptyList(),
    val isCollectible: Boolean = true,
    val isHiddenUntilDiscovered: Boolean = false,
    val isCoatCategory: Boolean = false,
    val rarity: BreedRarity? = null,
    val rarityOrder: Int = Int.MAX_VALUE,
    val isLegacyBreed: Boolean = false
) {
    val showsDiscoveryState: Boolean get() = isCollectible || isCoatCategory
    val countsInCollection: Boolean get() = showsDiscoveryState || id == "other_cat"
    /** Visibility is independent of membership in the collectible catalog. */
    val isVisibleInCollection: Boolean get() = !isHiddenUntilDiscovered || isDiscovered
    val isSecretMonkey: Boolean get() = id == MockBreedRepository.MONKEY_ID
}

/** Replace this implementation with a database-backed repository later. */
interface BreedRepository {
    fun getBreeds(): List<Breed>
    fun getCollectionEntries(): List<Breed> = getBreeds()
    fun getBreed(id: String): Breed? = getCollectionEntries().find { it.id == id }
}
