package com.example.petcollection.data

import kotlinx.coroutines.CancellationException

enum class AnimalCategory(val modelLabel: String) { CAT("cat"), DOG("dog"), MONKEY("monkey") }
data class AnimalPrediction(val category: AnimalCategory, val confidence: Float)
data class RecognitionClassification(val breedId: String, val confidence: Float)

/** Animal uncertainty means Unregistered; breed uncertainty uses the species album. */
object RecognitionPolicy {
    const val MIN_ANIMAL_CONFIDENCE = 0.70f
    const val CAT_BREED_THRESHOLD = 0.75f
    const val DOG_BREED_THRESHOLD = 0.75f
    const val CAT_COLOR_THRESHOLD = 0.60f
    val colorAlbumIds = linkedMapOf("orange" to "coat_orange_tabby", "calico" to "coat_calico",
        "tortoiseshell" to "coat_tortoiseshell", "tabby" to "coat_tabby",
        "tuxedo" to "coat_tuxedo", "black" to "coat_black")

    fun route(
        prediction: AnimalPrediction,
        catalog: List<Breed>,
        classifyColor: (() -> Pair<String, Float>)? = null,
        trace: (String) -> Unit = {},
        classifyBreed: (Species) -> Pair<String, Float>
    ): RecognitionClassification {
        require(prediction.confidence.isFinite() && prediction.confidence in 0f..1f)
        if (prediction.confidence < MIN_ANIMAL_CONFIDENCE) {
            trace("color skipped: animal confidence below $MIN_ANIMAL_CONFIDENCE; final=unregistered")
            return RecognitionClassification("unregistered", prediction.confidence)
        }
        if (prediction.category == AnimalCategory.MONKEY) {
            trace("color skipped: animal is monkey; final=monkey")
            return RecognitionClassification(MockBreedRepository.MONKEY_ID, prediction.confidence)
        }
        val species = if (prediction.category == AnimalCategory.CAT) Species.CAT else Species.DOG
        val (label, confidence) = classifyBreed(species)
        trace("breed species=$species top=$label confidence=$confidence")
        require(confidence.isFinite() && confidence in 0f..1f)
        val threshold = if (species == Species.CAT) CAT_BREED_THRESHOLD else DOG_BREED_THRESHOLD
        if (confidence < threshold) {
            if (species == Species.CAT && classifyColor != null) {
                try {
                    val (color, score) = classifyColor()
                    trace("color top=$color confidence=$score")
                    require(score.isFinite() && score in 0f..1f)
                    val id = if (score >= CAT_COLOR_THRESHOLD) colorAlbumIds[color] ?: "other_cat" else "other_cat"
                    trace("final=$id; color threshold=$CAT_COLOR_THRESHOLD")
                    return RecognitionClassification(id, score)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    trace("color failed: ${error.javaClass.simpleName}: ${error.message}; final=other_cat")
                    return RecognitionClassification("other_cat", confidence)
                }
            }
            trace("color skipped: ${if (species == Species.DOG) "dog" else "classifier unavailable"}; final=${if (species == Species.CAT) "other_cat" else "other_dog"}")
            return RecognitionClassification(if (species == Species.CAT) "other_cat" else "other_dog", confidence)
        }
        val id = ModelBreedMapping.resolve(label, species, catalog)?.id
            ?: if (species == Species.CAT) "other_cat" else "other_dog"
        trace("color skipped: ${if (species == Species.CAT) "breed confidence >= $CAT_BREED_THRESHOLD" else "dog"}; final=$id")
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
