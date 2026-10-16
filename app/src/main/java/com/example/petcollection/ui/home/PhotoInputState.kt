package com.example.petcollection.ui.home

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.*
import com.example.petcollection.data.*
import com.example.petcollection.ui.collection.CollectionUnlockState
import kotlinx.coroutines.*

/** One shared session for Home and album imports, retained by the existing collection ViewModel. */
class PhotoInputState(private val context: Context, private val scope: CoroutineScope, private val owner: CollectionUnlockState) {
    val recognition = PhotoRecognitionState()
    private val directory = PhotoCropIO.sessionDirectory(context)
    private var job: Job? = null
    private var generation = 0
    private var original: Uri? = null
    private var temporary: CroppedPhoto? = null
    private var coverTarget: Pair<String, String>? = null
    var mode by mutableStateOf(CropMode.FREE)
        private set
    var source by mutableStateOf<CropSource?>(null)
        private set
    var opening by mutableStateOf(false)
        private set
    var exporting by mutableStateOf(false)
        private set
    var error by mutableStateOf(false)
        private set
    var displayedUri by mutableStateOf<String?>(null)
        private set

    fun begin(uri: Uri) {
        if (owner.pendingEncounter != null || owner.isWorking) return
        open(uri, null)
    }

    fun beginCover(breedId: String, record: EncounterRecord) {
        if (owner.pendingEncounter != null || owner.isWorking || record.photoUri == null) return
        require(record.breedId == breedId)
        open(Uri.parse(record.photoUri), breedId to record.id)
    }

    private fun open(uri: Uri, target: Pair<String, String>?) {
        clear(keepRecognition = target != null)
        coverTarget = target
        mode = if (target == null) CropMode.FREE else CropMode.COVER
        original = uri
        opening = true
        val current = generation
        job = scope.launch {
            try {
                val loaded = withContext(Dispatchers.IO) { PhotoCropIO.prepare(context, uri) }
                if (generation == current) source = loaded
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (generation == current) error = true }
            finally { if (generation == current) opening = false }
        }
    }

    fun confirm(crop: SquareCrop) {
        val loaded = source ?: return
        if (exporting) return
        exporting = true
        error = false
        val current = generation
        val target = coverTarget
        job = scope.launch {
            // Assign inside IO too, so cancellation during dispatch cannot orphan an exported file.
            var output: CroppedPhoto? = null
            try {
                if (target != null) {
                    val bounds = crop.bounds(loaded.bitmap.width, loaded.bitmap.height)
                    val region = CropRegion(bounds.left / loaded.bitmap.width, bounds.top / loaded.bitmap.height,
                        bounds.side / loaded.bitmap.width, bounds.height / loaded.bitmap.height)
                    owner.saveCroppedCover(target.first, target.second, region)
                    source = null
                    original = null
                    coverTarget = null
                    return@launch
                }
                withContext(Dispatchers.IO) { output = PhotoCropIO.export(loaded, crop, directory) }
                ensureActive()
                val photo = requireNotNull(output)
                temporary = photo
                source = null
                displayedUri = photo.uri.toString()
                exporting = false
                val catalog = MockBreedRepository(context.resources).getCollectionEntries()
                recognition.recognize(context, photo.uri, catalog, owner, photo.metadata)
                if (recognition.failed) {
                    photo.file.delete()
                    temporary = null
                    displayedUri = null
                    recognition.reset()
                    error = true
                }
            } catch (cancelled: CancellationException) {
                output?.file?.delete()
                throw cancelled
            } catch (_: Exception) {
                output?.file?.delete()
                if (generation == current) {
                    temporary = null
                    if (target == null) displayedUri = null
                    error = true
                }
            } finally { if (generation == current) exporting = false }
        }
    }

    fun retry() { original?.let { open(it, coverTarget) } }

    /** Saving copies the confirmed crop into encounter_photos before its cache file is removed. */
    fun saved(record: EncounterRecord) {
        temporary?.file?.delete()
        temporary = null
        original = null
        displayedUri = record.photoUri
    }

    fun discard() {
        clear(keepRecognition = mode == CropMode.COVER)
    }

    private fun clear(keepRecognition: Boolean) {
        generation++
        job?.cancel()
        job = null
        temporary?.file?.delete()
        temporary = null
        source = null
        opening = false
        exporting = false
        error = false
        original = null
        coverTarget = null
        if (!keepRecognition) {
            displayedUri = null
            recognition.reset()
        }
    }

    fun close() { discard(); PhotoCropIO.deleteSession(directory) }
}
