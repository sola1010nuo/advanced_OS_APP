# 中山大學長 / NSYSU Senior

## Recognition entry
The temporary Home test button and its Debug/Release implementations have been removed.
Existing saved encounters are preserved. Automatic Monkey inference now uses the Cat / Dog / Monkey model; see model-integration.md.
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

## Cat / Dog / Monkey model integration
The first-stage model is now connected through AnimalClassifier and RecognitionPolicy.
Monkey at/above RecognitionPolicy.MIN_ANIMAL_CONFIDENCE uses the production canonical entry.
Low-confidence results use Unregistered and skip both breed models.
See model-integration.md for the inspected Tensor contract, preprocessing, and real-photo test steps.