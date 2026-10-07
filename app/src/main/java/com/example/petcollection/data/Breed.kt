package com.example.petcollection.data

/** Photo references can later come from app storage or a Room encounter record. */
data class BreedPhoto(val id: String, val uri: String? = null)
enum class Species { CAT, DOG }

data class Breed(
    val id: String,
    val name: String,
    val species: Species,
    val description: String,
    val origin: String,
    val personality: String,
    val size: String,
    val coat: String,
    val lifespan: String? = null,
    val isDiscovered: Boolean = false,
    val encounterCount: Int = 0,
    val coverPhoto: BreedPhoto? = null,
    val collectedPhotos: List<BreedPhoto> = emptyList()
)

/** Replace this implementation with a database-backed repository later. */
interface BreedRepository {
    fun getBreeds(): List<Breed>
    fun getBreed(id: String): Breed? = getBreeds().find { it.id == id }
}
