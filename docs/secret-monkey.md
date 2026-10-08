# 中山大學長 / NSYSU Senior

## Recognition entry
The temporary Home test button and its Debug/Release implementations have been removed.
Existing saved encounters are preserved. Automatic Monkey inference awaits the three-class model.
The production entry remains:
unlockState.onClassificationResult(MockBreedRepository.MONKEY_ID, catalog, uri.toString())

This opens the Polaroid preview, then the encounter editor. Only a successful editor save writes to Room.
Regression tests still exercise this production entry without adding an in-app simulation control.
## Architecture
- Stable canonical ID: MockBreedRepository.MONKEY_ID ("monkey").
- The existing catalog includes this official collectible, with species = null.
- Species remains CAT/DOG so their progress is unchanged; null routes the collectible to Other.
- isHiddenUntilDiscovered only controls card/detail visibility, never catalog statistics.
- CollectionData.statistics(fullCatalog) calculates overall progress and achievement totals.
- Room encounters are the only discovery source of truth. Preview state never writes.
- The existing breed_covers table stores the first saved photo and later user-selected cover.
- No Room entity, schema version, migration, or destructive reset is introduced.

## Future Cat / Dog / Monkey model integration
Do not alter existing assets for this feature. Once the new model is ready:
1. Update the animal detector / model loading contract in BreedClassifier.kt for the new model's actual tensors and class order.
2. Update PhotoRecognitionState.recognize to branch on Monkey before calling cat/dog breed classifiers.
3. For Monkey, send the original selected URI and the full catalog through:
   unlockState.onClassificationResult(MockBreedRepository.MONKEY_ID, catalog, uri.toString())
4. Continue using the existing cat/dog mapping and breed classifiers in their own branches.
5. No monkey species label mapping or confidence threshold is added here. The Formosan macaque is the Easter egg inspiration, not a claim of species-level inference.
6. Do not automatically call saveEncounter: the existing editor confirmation owns Room persistence.