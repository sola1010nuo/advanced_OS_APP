# Cat / Dog / Monkey model integration

## Inspected export contract
The assets were read without modifying them using scripts/InspectTflite.py.
- First-stage asset: cat_dog_monkey_model.tflite
- Input: serving_default_input_layer_1:0, FLOAT32 [1,224,224,3], no quantization.
- Output: StatefulPartitionedCall_1:0, FLOAT32 [1,3], no quantization.
- Labels / indices: 0 cat, 1 dog, 2 monkey.
- First graph operations: MUL by 0.007843137718737125 (~1/127.5), then ADD -1.
- Last graph operation: SOFTMAX. Do not apply a second Softmax.
- Cat and Dog breed assets also have FLOAT32 [1,224,224,3] inputs and embedded rescaling.
  Their outputs remain [1,22] and [1,34], respectively.
- Runtime checks enforce shapes, types, label order, finite probabilities, and probability sum.

## Preprocessing
The app retains the existing orientation-aware URI decoding (Android 28+) and filtered bitmap resize.
Supply RGB channel order, 224x224, FLOAT32 pixels in [0,255]. Do not divide pixels by 255 or normalize to [-1,1] outside the model.
The export confirms embedded normalization, but does not encode the external training image loader,
resize/crop policy, or augmentation recipe. No Colab training script was supplied; compare those details
with this resize policy when evaluating real photos.

## Routing
RecognitionPolicy.MIN_ANIMAL_CONFIDENCE is the single adjustable threshold (0.70f).
- Below threshold: Unregistered, no breed model and no Monkey preview.
- Cat at/above threshold: existing cat breed model and canonical mapping.
- Dog at/above threshold: existing dog breed model and canonical mapping.
- Unknown mapped breed: existing Other Cats / Other Dogs album.
- Monkey at/above threshold: canonical monkey ID, no second-stage model.
PhotoRecognitionState passes the result and original photo URI to CollectionUnlockState.onClassificationResult.
Polaroid -> encounter editor -> user-confirmed Room save remains unchanged.
Preview/cancellation never inserts records. A saved second monkey photo does not add another discovered kind.
Softmax confidence is a routing score, not a guarantee of correct identification; error/retry and existing
collection/encounter controls are retained.

## Android Studio real-photo check
1. Run the app normally; no Debug simulation control is needed or present.
2. Home -> Add Photo -> select a real monkey photo from the system photo picker.
   For a newly captured photo, save it to the gallery and select it through the existing picker.
3. If the model chooses Monkey at/above threshold, the existing NSYSU Senior Polaroid appears.
4. Continue to the encounter editor. Cancel once to verify no new discovery.
5. Repeat and save. Other shows NSYSU Senior; check the saved photo and dynamic overall progress.
6. Select another monkey photo: repeat greeting, same album, no second discovered kind.
7. Select representative cat and dog photos: check their localized breed results and save/edit/cover functions.
8. Try ambiguous/non-animal images: low-confidence results should use Unregistered rather than Monkey.
   These images can still receive a high Softmax score; evaluate a labeled real-photo set before tuning the threshold.

## Verification scope
Unit tests exercise routing, boundary/low confidence, output index order, and fallback albums.
Android tests load and run the actual three-class model and original breed models, verify RGB byte values,
and exercise URI -> draft -> save plus existing Room/Collection behavior.
Synthetic inference smoke inputs are not an accuracy benchmark for real Cat/Dog/Monkey photographs.
No Room migration or data reset is required. No model training or rewriting is performed.