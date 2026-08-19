package com.repzy.app.ui.photos

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.repzy.app.data.model.BodyPhoto
import com.repzy.app.data.model.PhotoPose
import com.repzy.app.data.repo.BodyPhotoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import dagger.hilt.android.qualifiers.ApplicationContext
import com.repzy.app.data.repo.toUserMessage

/**
 * Karşılaştırma seçimi. İki fotoğraf seçilince slider açılıyor; tarih sırasına
 * göre eskisi "önce", yenisi "sonra" oluyor — kullanıcı sırayı düşünmek zorunda kalmasın.
 */
data class Comparison(
    val before: BodyPhoto,
    val after: BodyPhoto,
    val beforeUrl: String,
    val afterUrl: String,
)

data class BodyPhotosUiState(
    val isLoading: Boolean = true,
    val hasConsent: Boolean = false,
    val photos: List<BodyPhoto> = emptyList(),
    /** storage_path -> imzalı URL. Kısa ömürlü, o yüzden her yüklemede yenileniyor. */
    val urls: Map<String, String> = emptyMap(),
    val pose: PhotoPose = PhotoPose.FRONT,
    val isUploading: Boolean = false,
    val selected: Set<String> = emptySet(),
    val comparison: Comparison? = null,
    val error: String? = null,
) {
    /** Aynı poz içinde karşılaştırmak anlamlı; farklı pozu yan yana koymak yanıltıcı olurdu. */
    val photosOfPose: List<BodyPhoto> get() = photos.filter { it.pose == pose }

    val canCompare: Boolean get() = selected.size == 2
}

@HiltViewModel
class BodyPhotosViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val repository: BodyPhotoRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(BodyPhotosUiState())
    val state: StateFlow<BodyPhotosUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            val consent = repository.hasConsent()
            if (!consent) {
                _state.update { it.copy(isLoading = false, hasConsent = false) }
                return@launch
            }

            repository.list()
                .onSuccess { photos ->
                    _state.update {
                        it.copy(isLoading = false, hasConsent = true, photos = photos)
                    }
                    refreshUrls(photos)
                }
                .onFailure { e ->
                    _state.update { it.copy(isLoading = false, hasConsent = true, error = e.toUserMessage(appContext)) }
                }
        }
    }

    /** İmzalı URL'ler tek tek üretiliyor; galeride birkaç fotoğraf olur, toplu API yok. */
    private suspend fun refreshUrls(photos: List<BodyPhoto>) {
        val urls = buildMap {
            photos.forEach { photo ->
                repository.signedUrl(photo.storagePath).onSuccess { put(photo.storagePath, it) }
            }
        }
        _state.update { it.copy(urls = urls) }
    }

    fun grantConsent() {
        viewModelScope.launch {
            repository.grantConsent()
                .onSuccess { load() }
                .onFailure { e -> _state.update { it.copy(error = e.toUserMessage(appContext)) } }
        }
    }

    fun setPose(pose: PhotoPose) = _state.update {
        // Poz değişince seçim anlamını yitiriyor — karşılaştırma hep aynı poz içinde.
        it.copy(pose = pose, selected = emptySet())
    }

    fun addPhoto(context: Context, uri: Uri) {
        if (_state.value.isUploading) return
        val pose = _state.value.pose
        _state.update { it.copy(isUploading = true, error = null) }

        viewModelScope.launch {
            repository.add(context, uri, pose)
                .onSuccess {
                    _state.update { it.copy(isUploading = false) }
                    load()
                }
                .onFailure { e ->
                    _state.update { it.copy(isUploading = false, error = e.toUserMessage(appContext)) }
                }
        }
    }

    fun toggleSelection(photo: BodyPhoto) = _state.update { state ->
        val path = photo.storagePath
        when {
            path in state.selected -> state.copy(selected = state.selected - path)
            // Üçüncüye basınca en eski seçimi düşürmek, "önce seçimi temizle" demekten akıcı.
            state.selected.size >= 2 -> state.copy(selected = setOf(state.selected.last(), path))
            else -> state.copy(selected = state.selected + path)
        }
    }

    fun compareSelected() {
        val state = _state.value
        if (!state.canCompare) return

        val chosen = state.photos
            .filter { it.storagePath in state.selected }
            .sortedBy { it.takenOn }
        if (chosen.size != 2) return

        val before = chosen.first()
        val after = chosen.last()
        val beforeUrl = state.urls[before.storagePath] ?: return
        val afterUrl = state.urls[after.storagePath] ?: return

        _state.update {
            it.copy(comparison = Comparison(before, after, beforeUrl, afterUrl))
        }
    }

    fun closeComparison() = _state.update { it.copy(comparison = null) }

    fun delete(photo: BodyPhoto) {
        viewModelScope.launch {
            repository.delete(photo)
                .onSuccess {
                    _state.update {
                        it.copy(selected = it.selected - photo.storagePath, comparison = null)
                    }
                    load()
                }
                .onFailure { e -> _state.update { it.copy(error = e.toUserMessage(appContext)) } }
        }
    }

    fun dismissError() = _state.update { it.copy(error = null) }
}
