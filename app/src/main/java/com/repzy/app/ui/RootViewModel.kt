package com.repzy.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.repzy.app.core.Perf
import com.repzy.app.core.onboardingPayload
import com.repzy.app.core.planFor
import com.repzy.app.data.local.OnboardingDraft
import com.repzy.app.data.local.OnboardingDraftStore
import com.repzy.app.data.repo.AuthRepository
import com.repzy.app.data.repo.ProfileRepository
import com.repzy.app.widget.WidgetSnapshotStore
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toKotlinLocalDate
import java.util.Locale
import javax.inject.Inject

enum class AppDestination { LOADING, ONBOARDING, AUTH, PAYWALL, HOME, ERROR }

/**
 * Akışın tek karar noktası. Onboarding giriş ÖNCESİNDE olduğu için yönlendirme
 * üç girdinin bileşimi: oturum durumu, cihazdaki onboarding taslağı, ve kullanıcının
 * onboarding'i atlayıp "hesabım var" demesi.
 *
 *   oturum var                          → HOME (profil tamamsa) / ONBOARDING (değilse)
 *   oturum var  + taslak tamamlanmış    → taslağı sunucuya yaz, sonra HOME
 *   oturum yok  + taslak tamamlanmış    → AUTH  ("planını kaydet")
 *   oturum yok  + "hesabım var" dendi   → AUTH  (giriş modunda, iptal edilebilir)
 *   oturum yok                          → ONBOARDING
 */
@HiltViewModel
class RootViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
    private val draftStore: OnboardingDraftStore,
    private val widgetSnapshotStore: WidgetSnapshotStore,
) : ViewModel() {

    private val _destination = MutableStateFlow(AppDestination.LOADING)
    val destination: StateFlow<AppDestination> = _destination.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    /** Auth ekranı "hesap oluştur ki planın kaydolsun" çerçevesiyle mi açılıyor? */
    private val _pendingPlan = MutableStateFlow(false)
    val pendingPlan: StateFlow<Boolean> = _pendingPlan.asStateFlow()

    /** Kullanıcı onboarding'in başında "hesabım var" dedi mi? */
    private val signInRequested = MutableStateFlow(false)
    val isSignInCancellable: StateFlow<Boolean> = signInRequested.asStateFlow()

    private val today: LocalDate get() = java.time.LocalDate.now().toKotlinLocalDate()

    init {
        viewModelScope.launch {
            Perf.mark("RootViewModel.init")
            // Kayıtlı oturum yüklenmeden yönlendirme yapılmaz, yoksa onboarding parlar.
            Perf.time("auth.awaitInitialization") { authRepository.awaitInitialization() }

            combine(
                authRepository.sessionStatus,
                draftStore.draft,
                signInRequested,
            ) { status, draft, wantsSignIn ->
                Triple(status, draft, wantsSignIn)
            }.collect { (status, draft, wantsSignIn) -> route(status, draft, wantsSignIn) }
        }
    }

    /** Onboarding'in ilk adımındaki "hesabın var mı?" bağlantısı. */
    fun requestSignIn() {
        signInRequested.value = true
    }

    /** Giriş ekranından onboarding'e dönüş. */
    fun cancelSignIn() {
        signInRequested.value = false
    }

    fun retry() {
        viewModelScope.launch {
            route(authRepository.sessionStatus.first(), draftStore.load(), signInRequested.value)
        }
    }

    fun signOut() {
        viewModelScope.launch {
            // Önbellekteki profil temizlenmezse başka hesapla girişte eski isim görünür.
            profileRepository.invalidateProfile()
            draftStore.setProfileReady(false)
            // Widget başkasının verisini göstermesin.
            widgetSnapshotStore.clear()
            authRepository.signOut()
        }
    }

    /** Paywall kapatıldı — bir daha otomatik açılmaz. */
    fun dismissPaywall() {
        viewModelScope.launch {
            draftStore.setPaywallSeen()
            _destination.value = AppDestination.HOME
        }
    }

    private suspend fun route(
        status: SessionStatus,
        draft: OnboardingDraft?,
        wantsSignIn: Boolean,
    ) {
        when (status) {
            is SessionStatus.Initializing -> _destination.value = AppDestination.LOADING

            is SessionStatus.NotAuthenticated, is SessionStatus.RefreshFailure -> {
                val planReady = draft?.completed == true
                _pendingPlan.value = planReady
                _destination.value = if (planReady || wantsSignIn) {
                    AppDestination.AUTH
                } else {
                    AppDestination.ONBOARDING
                }
            }

            is SessionStatus.Authenticated -> {
                _pendingPlan.value = false
                signInRequested.value = false
                if (draft != null && draft.completed) syncDraft(draft) else resolveProfile(draft)
            }
        }
    }

    /** Hesap açıldı — cihazdaki cevapları tek RPC ile sunucuya taşı. */
    private suspend fun syncDraft(draft: OnboardingDraft) {
        val summary = planFor(draft, today)
        if (summary == null) {
            // Taslak eksik/bozuk: "tamamlandı" işaretini kaldır, kullanıcı adımlara dönsün.
            draftStore.save(draft.copy(completed = false))
            return
        }

        _destination.value = AppDestination.LOADING
        val payload = onboardingPayload(
            draft = draft,
            summary = summary,
            today = today,
            locale = Locale.getDefault().language,
        )
        profileRepository.completeOnboarding(payload)
            .onSuccess {
                _errorMessage.value = null
                // Taslağı silmek akışı yeniden tetikler; profil artık tamam olduğu için HOME'a gider.
                draftStore.clear()
            }
            .onFailure { e ->
                _errorMessage.value = e.message
                _destination.value = AppDestination.ERROR
            }
    }

    private suspend fun resolveProfile(draft: OnboardingDraft?) {
        // Daha önce profilin tam olduğunu gördüysek beklemeden Home'u açıyoruz.
        // Profil zaten Home'un kendi yüklemesinde geliyor; eksik çıkarsa aşağıda düzeltilir.
        if (draftStore.isProfileReady()) {
            _errorMessage.value = null
            _destination.value = AppDestination.HOME
        } else {
            _destination.value = AppDestination.LOADING
        }

        Perf.time("profiles.select") { profileRepository.getProfile() }
            .onSuccess { profile ->
                _errorMessage.value = null
                if (profile?.isOnboardingComplete == true) {
                    // Sunucudaki profil zaten tam: yarım kalmış taslak artık geçersiz,
                    // durursa çıkış yapıldığında tekrar ortaya çıkardı.
                    if (draft != null) draftStore.clear()
                    draftStore.setProfileReady(true)
                    // Onboarding paywall'ı bir kez gösterilir; kapatılabilir, ücretsiz
                    // katman çalışmaya devam eder.
                    _destination.value = if (draftStore.isPaywallSeen()) {
                        AppDestination.HOME
                    } else {
                        AppDestination.PAYWALL
                    }
                } else {
                    // İşaret yanlışmış (hesap silinmiş, profil sıfırlanmış): geri al.
                    draftStore.setProfileReady(false)
                    _destination.value = AppDestination.ONBOARDING
                }
            }
            .onFailure { e ->
                // Profil okunamadıysa kullanıcıyı auth'a atmak yanlış olur.
                // En olası sebep: migration'lar Supabase'e uygulanmamış.
                // Home'u zaten açtıysak orada da aynı hata görünecek, ekranı çalmayalım.
                if (_destination.value != AppDestination.HOME) {
                    _errorMessage.value = e.message
                    _destination.value = AppDestination.ERROR
                }
            }
    }
}
