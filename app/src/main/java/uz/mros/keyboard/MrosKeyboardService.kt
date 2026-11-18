package uz.mros.keyboard

import android.content.ClipboardManager
import android.graphics.drawable.ColorDrawable
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.view.View
import android.view.WindowInsetsController
import android.view.WindowManager
import android.view.inputmethod.EditorInfo

class MrosKeyboardService : InputMethodService() {

    lateinit var prefs: Prefs
        private set
    lateinit var suggest: SuggestionEngine
        private set
    lateinit var feedback: Feedback
        private set

    private var root: KeyboardRootView? = null
    private var clipboard: ClipboardManager? = null
    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener { onClipChanged() }
    private var learnResetSeen = 0L

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        suggest = SuggestionEngine(this)
        feedback = Feedback(this, prefs)
        learnResetSeen = prefs.sp.getLong("learn_reset", 0L)
        suggest.onDictLoaded = { root?.refreshSuggestions() }
        suggest.prepare(prefs.currentLang)
        EmojiData.load(this)
        EmojiImages.init(this)
        UpdateJobService.ensureScheduled(this)
        clipboard = getSystemService(ClipboardManager::class.java)
        try {
            clipboard?.addPrimaryClipChangedListener(clipListener)
        } catch (_: Exception) {
        }
    }

    override fun onCreateInputView(): View {
        val r = KeyboardRootView(this)
        root = r
        return r
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (!restarting) Updater.maybeCheckInBackground(this)
        if (info != null) root?.onStartInput(info, restarting)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        root?.onFinishInput()
        suggest.save()
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        root?.onSelectionUpdate()
    }

    /** Landshaftda ham to'liq ekranli tahrir oynasi ochilmasin. */
    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onDestroy() {
        try {
            clipboard?.removePrimaryClipChangedListener(clipListener)
        } catch (_: Exception) {
        }
        suggest.save()
        root = null
        super.onDestroy()
    }

    /** Sozlamalar oynasida "so'zlarni tozalash" bosilgan bo'lsa — xotiradagilarni ham tozalaymiz. */
    fun checkResets() {
        val token = prefs.sp.getLong("learn_reset", 0L)
        if (token != learnResetSeen) {
            learnResetSeen = token
            suggest.clearLearned()
        }
    }

    /**
     * IME oynasining foni (navigatsiya paneli ostidagi joy ham shu rangda bo'ladi).
     * true qaytarsa — orqa fon xiralashtirish (blur) ishlayapti.
     */
    private var blurWasOn = false
    private var lightNavApplied: Boolean? = null

    fun applyWindowStyle(g: GlassTheme, wantBlur: Boolean): Boolean {
        val w = window?.window ?: return false
        var blur = false
        if (wantBlur && Build.VERSION.SDK_INT >= 31) {
            val wm = getSystemService(WindowManager::class.java)
            if (wm != null && wm.isCrossWindowBlurEnabled) blur = true
        }
        try {
            if (blur) {
                w.setBackgroundDrawable(ColorDrawable(g.blurTint))
                if (Build.VERSION.SDK_INT >= 31) w.setBackgroundBlurRadius((40 * resources.displayMetrics.density).toInt())
                blurWasOn = true
            } else {
                // Fonni klaviaturaning o'zi chizadi (navigatsiya paneli ostini ham) — oyna shaffof qoladi
                w.setBackgroundDrawable(ColorDrawable(0))
                if (blurWasOn && Build.VERSION.SDK_INT >= 31) w.setBackgroundBlurRadius(0)
                blurWasOn = false
            }
            @Suppress("DEPRECATION")
            w.navigationBarColor = if (blur) g.blurTint else g.bgBottom
            val wantLight = !g.dark
            if (lightNavApplied == wantLight) return blur
            lightNavApplied = wantLight
            if (Build.VERSION.SDK_INT >= 30) {
                w.insetsController?.setSystemBarsAppearance(
                    if (g.dark) 0 else WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                    WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                )
            } else {
                @Suppress("DEPRECATION")
                val flags = w.decorView.systemUiVisibility
                @Suppress("DEPRECATION")
                w.decorView.systemUiVisibility = if (g.dark) {
                    flags and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
                } else {
                    flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                }
            }
        } catch (_: Exception) {
        }
        return blur
    }

    // ---------------- Bufer tarixi ----------------

    fun clipHistory(): List<String> = prefs.clips

    private fun onClipChanged() {
        val cb = clipboard ?: return
        val text = try {
            val clip = cb.primaryClip
            val desc = clip?.description
            val sensitive = desc?.extras?.getBoolean("android.content.extra.IS_SENSITIVE", false) == true
            if (clip == null || clip.itemCount == 0 || sensitive) null
            else clip.getItemAt(0).coerceToText(this)?.toString()
        } catch (_: Exception) {
            null
        } ?: return
        if (text.isBlank() || text.length > 10_000) return
        val list = ArrayList<String>()
        list.add(text)
        for (c in prefs.clips) if (c != text && list.size < 25) list.add(c)
        prefs.clips = list
    }

    fun removeClip(text: String) {
        prefs.clips = prefs.clips.filter { it != text }
    }

    fun clearClips() {
        prefs.clips = emptyList()
    }
}
