package com.example.petcollection.data

enum class AnimalCategory(val modelLabel: String) { CAT("cat"), DOG("dog"), MONKEY("monkey") }
data class AnimalPrediction(val category: AnimalCategory, val confidence: Float)
data class RecognitionClassification(val breedId: String, val confidence: Float)

/** The single adjustable first-stage threshold. Uncertain animal type means Unregistered. */
object RecognitionPolicy {
    const val MIN_ANIMAL_CONFIDENCE = 0.70f

    fun route(
        prediction: AnimalPrediction,
        catalog: List<Breed>,
        classifyBreed: (Species) -> Pair<String, Float>
    ): RecognitionClassification {
        require(prediction.confidence.isFinite() && prediction.confidence in 0f..1f)
        if (prediction.confidence < MIN_ANIMAL_CONFIDENCE) {
            return RecognitionClassification("unregistered", prediction.confidence)
        }
        if (prediction.category == AnimalCategory.MONKEY) {
            return RecognitionClassification(MockBreedRepository.MONKEY_ID, prediction.confidence)
        }
        val species = if (prediction.category == AnimalCategory.CAT) Species.CAT else Species.DOG
        val (label, confidence) = classifyBreed(species)
        require(confidence.isFinite() && confidence in 0f..1f)
        val id = ModelBreedMapping.resolve(label, species, catalog)?.id
            ?: if (species == Species.CAT) "other_cat" else "other_dog"
        return RecognitionClassification(id, confidence)
    }

    fun fromProbabilities(labels: List<String>, probabilities: FloatArray): AnimalPrediction {
        require(labels == AnimalCategory.entries.map { it.modelLabel }) { "Animal labels must be cat, dog, monkey in that order" }
        require(probabilities.size == labels.size)
        require(probabilities.all { it.isFinite() && it in 0f..1f })
        require(kotlin.math.abs(probabilities.sum() - 1f) < .01f) { "Expected Softmax probabilities" }
        val index = probabilities.indices.maxBy { probabilities[it] }
        return AnimalPrediction(AnimalCategory.entries[index], probabilities[index])
    }
}