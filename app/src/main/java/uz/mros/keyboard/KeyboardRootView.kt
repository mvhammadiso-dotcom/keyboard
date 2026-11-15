package uz.mros.keyboard

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Canvas
import android.icu.text.BreakIterator
import android.os.SystemClock
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout

/**
 * Klaviaturaning "miyasi": rejimlar (harflar / emoji / qidiruv / bufer), matn kiritish mantiqi,
 * takliflar, avtomatik bosh harf va hokazo.
 */
class KeyboardRootView(private val ime: MrosKeyboardService) : FrameLayout(ime),
    KeyboardView.Host, ToolbarView.Host, EmojiPanelView.Host, EmojiSearchView.Host, ClipboardPanelView.Host {

    enum class Panel { KEYS, EMOJI, EMOJI_SEARCH, CLIPBOARD }

    override val prefs: Prefs = ime.prefs
    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    override var glass: GlassTheme = GlassTheme(isDark(), density)
        private set
    private var blurActive = false

    private val content = LinearLayout(ime).apply { orientation = LinearLayout.VERTICAL }
    private val toolbar = ToolbarView(ime, this)
    private val searchView = EmojiSearchView(ime, this)
    private val keyboard = KeyboardView(ime, this)
    private val emojiPanel = EmojiPanelView(ime, this)
    private val clipPanel = ClipboardPanelView(ime, this)

    private var panel = Panel.KEYS
    private var kind = LayoutKind.LETTERS
    private var fieldKind = LayoutKind.LETTERS
    private var lang: Lang = prefs.currentLang
    private var variant = FieldVariant.NORMAL
    private var editorInfo: EditorInfo? = null

    private var shiftState = 0
    private var lastShiftTap = 0L
    private var lastSpaceTime = 0L
    private var autoSpaced = false
    private var lastOwnEdit = 0L

    private var suggestionsAllowed = true
    private var learningAllowed = true
    private var autoCapAllowed = true

    private var toolbarH = 0
    private var keysH = 0

    init {
        clipChildren = false
        val wrap = LinearLayout.LayoutParams.WRAP_CONTENT
        val match = LinearLayout.LayoutParams.MATCH_PARENT
        content.addView(toolbar, LinearLayout.LayoutParams(match, dp(46f).toInt()))
        content.addView(searchView, LinearLayout.LayoutParams(match, wrap))
        content.addView(keyboard, LinearLayout.LayoutParams(match, wrap))
        content.addView(emojiPanel, LinearLayout.LayoutParams(match, wrap))
        content.addView(clipPanel, LinearLayout.LayoutParams(match, wrap))
        addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        applySizes()
        applyTheme()
        rebuildLayout()
        showPanel(Panel.KEYS)
    }

    // ================= tashqi chaqiruvlar (service) =================

    fun onStartInput(info: EditorInfo, restarting: Boolean) {
        editorInfo = info
        lang = prefs.currentLang
        ime.suggest.prepare(lang)
        ime.checkResets()
        applySizes()
        applyTheme()

        suggestionsAllowed = prefs.suggestions
        learningAllowed = prefs.learnWords
        autoCapAllowed = prefs.autoCap
        variant = FieldVariant.NORMAL
        val type = info.inputType
        when (type and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_PHONE, InputType.TYPE_CLASS_DATETIME -> {
                fieldKind = LayoutKind.NUMPAD
                suggestionsAllowed = false
                learningAllowed = false
                autoCapAllowed = false
            }
            InputType.TYPE_CLASS_TEXT -> {
                fieldKind = LayoutKind.LETTERS
                when (type and InputType.TYPE_MASK_VARIATION) {
                    InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS -> {
                        variant = FieldVariant.EMAIL
                        suggestionsAllowed = false; learningAllowed = false; autoCapAllowed = false
                    }
                    InputType.TYPE_TEXT_VARIATION_URI -> {
                        variant = FieldVariant.URL
                        suggestionsAllowed = false; learningAllowed = false; autoCapAllowed = false
                    }
                    InputType.TYPE_TEXT_VARIATION_PASSWORD, InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                    InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD -> {
                        variant = FieldVariant.PASSWORD
                        suggestionsAllowed = false; learningAllowed = false; autoCapAllowed = false
                    }
                }
                if (type and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS != 0) suggestionsAllowed = false
            }
            else -> {
                fieldKind = LayoutKind.LETTERS
                suggestionsAllowed = false
                learningAllowed = false
            }
        }
        if (type and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_TEXT &&
            (type and InputType.TYPE_MASK_VARIATION) == InputType.TYPE_TEXT_VARIATION_PASSWORD
        ) learningAllowed = false

        kind = fieldKind
        shiftState = 0
        autoSpaced = false
        lastSpaceTime = 0L
        rebuildLayout()
        showPanel(Panel.KEYS)
        updateAutoShift()
        updateSuggestions()
    }

    fun onFinishInput() {
        keyboard.cancelAll()
    }

    fun onSelectionUpdate() {
        val external = SystemClock.uptimeMillis() - lastOwnEdit > 300L
        if (external) {
            autoSpaced = false
            updateAutoShift()
            updateSuggestions()
        }
    }

    fun refreshSuggestions() = updateSuggestions()

    // ================= ko'rinish =================

    private fun isDark(): Boolean = when (prefs.theme) {
        1 -> false
        2 -> true
        else -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    private fun applyTheme() {
        glass = GlassTheme(isDark(), density)
        blurActive = ime.applyWindowStyle(glass, prefs.blur)
        invalidateAll()
    }

    private fun invalidateAll() {
        invalidate()
        toolbar.invalidate()
        keyboard.invalidate()
        emojiPanel.invalidate()
        searchView.invalidate()
        clipPanel.invalidate()
    }

    private fun applySizes() {
        val scale = prefs.heightPercent.coerceIn(70, 140) / 100f
        val rows = if (prefs.numberRow) 5 else 4
        val rowH = dp(54f) * scale
        toolbarH = dp(46f).toInt()
        keysH = (rowH * rows + dp(8f)).toInt()
        keyboard.preferredHeight = keysH
        emojiPanel.preferredHeight = toolbarH + keysH
        clipPanel.preferredHeight = keysH
        keyboard.showPreviews = prefs.keyPopup
        keyboard.requestLayout()
        emojiPanel.requestLayout()
        clipPanel.requestLayout()
    }

    override fun dispatchDraw(canvas: Canvas) {
        glass.drawBackground(canvas, width, height, blurActive)
        super.dispatchDraw(canvas)
        if (keyboard.visibility == View.VISIBLE) {
            canvas.save()
            canvas.translate((content.left + keyboard.left).toFloat(), (content.top + keyboard.top).toFloat())
            keyboard.drawOverlay(canvas)
            canvas.restore()
        }
    }

    private fun showPanel(p: Panel) {
        val prev = panel
        panel = p
        toolbar.mode = if (p == Panel.CLIPBOARD) ToolbarView.Mode.CLIPBOARD else ToolbarView.Mode.NORMAL
        toolbar.visibility = if (p == Panel.KEYS || p == Panel.CLIPBOARD) View.VISIBLE else View.GONE
        searchView.visibility = if (p == Panel.EMOJI_SEARCH) View.VISIBLE else View.GONE
        keyboard.visibility = if (p == Panel.KEYS || p == Panel.EMOJI_SEARCH) View.VISIBLE else View.GONE
        emojiPanel.visibility = if (p == Panel.EMOJI) View.VISIBLE else View.GONE
        clipPanel.visibility = if (p == Panel.CLIPBOARD) View.VISIBLE else View.GONE
        keyboard.cancelAll()
        when (p) {
            Panel.EMOJI -> {
                emojiPanel.rebuild()
                if (!EmojiData.ready) EmojiData.load(ime) { if (panel == Panel.EMOJI) emojiPanel.rebuild() }
            }
            Panel.EMOJI_SEARCH -> {
                if (!EmojiData.ready) EmojiData.load(ime) { if (panel == Panel.EMOJI_SEARCH) searchView.refresh() }
                searchView.reset()
                kind = LayoutKind.LETTERS
                shiftState = 0
                rebuildLayout()
                keyboard.enterAction = EnterAction.SEARCH
            }
            Panel.KEYS -> {
                if (prev == Panel.EMOJI_SEARCH || prev == Panel.EMOJI) {
                    if (kind != fieldKind) {
                        kind = fieldKind
                        rebuildLayout()
                    }
                }
                keyboard.enterAction = enterActionFor(editorInfo)
                updateAutoShift()
                updateSuggestions()
            }
            Panel.CLIPBOARD -> clipPanel.refresh()
        }
        invalidate()
    }

    private fun rebuildLayout() {
        val opts = LayoutOptions(
            numberRow = prefs.numberRow && kind == LayoutKind.LETTERS,
            showGlobe = prefs.languages.size > 1,
            variant = variant,
            officialApostrophe = prefs.apostropheStyle == 0,
        )
        keyboard.layout = Layouts.build(kind, lang, opts)
        keyboard.spaceLabel = if (kind == LayoutKind.LETTERS) lang.label else ""
        keyboard.shiftState = shiftState
        if (panel != Panel.EMOJI_SEARCH) keyboard.enterAction = enterActionFor(editorInfo)
    }

    private fun setKind(k: LayoutKind) {
        kind = k
        if (k != LayoutKind.LETTERS) shiftState = 0
        rebuildLayout()
        if (k == LayoutKind.LETTERS) updateAutoShift()
    }

    private fun enterActionFor(info: EditorInfo?): EnterAction {
        if (info == null) return EnterAction.NEWLINE
        if (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) return EnterAction.NEWLINE
        return when (info.imeOptions and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_SEARCH -> EnterAction.SEARCH
            EditorInfo.IME_ACTION_SEND -> EnterAction.SEND
            EditorInfo.IME_ACTION_GO -> EnterAction.GO
            EditorInfo.IME_ACTION_NEXT -> EnterAction.NEXT
            EditorInfo.IME_ACTION_DONE -> EnterAction.DONE
            EditorInfo.IME_ACTION_PREVIOUS -> EnterAction.PREVIOUS
            else -> EnterAction.NEWLINE
        }
    }

    // ================= KeyboardView.Host =================

    override fun onKeyFeedback(key: Key) = ime.feedback.key(key.type)

    override fun requestOverlayRedraw() = invalidate()

    override fun overlaySpaceAbove(): Float = (content.top + keyboard.top).toFloat()

    override fun onChar(text: String, key: Key) {
        if (panel == Panel.EMOJI_SEARCH) {
            searchView.setQuery(searchView.query + text.lowercase())
            return
        }
        val ic = ic() ?: return
        markEdit()
        val single = text.length == 1
        if (single && text[0] in ".,!?;:" && autoSpaced) {
            val before = ic.getTextBeforeCursor(1, 0)
            if (before != null && before.toString() == " ") {
                ic.beginBatchEdit()
                ic.deleteSurroundingText(1, 0)
                ic.commitText("$text ", 1)
                ic.endBatchEdit()
                autoSpaced = true
                afterSeparator()
                return
            }
        }
        val separator = single && !text[0].isLetterOrDigit() && !isApostrophe(text[0])
        if (separator) learnPreviousWord(ic)
        ic.commitText(text, 1)
        autoSpaced = false
        if (separator) {
            afterSeparator()
        } else {
            if (shiftState == 1) {
                shiftState = 0
                keyboard.shiftState = 0
            }
            updateSuggestions()
        }
    }

    override fun onFunctionKey(key: Key) {
        when (key.type) {
            KeyType.SHIFT -> handleShift()
            KeyType.SPACE -> handleSpace()
            KeyType.ENTER -> handleEnter()
            KeyType.SYMBOLS -> setKind(LayoutKind.SYMBOLS)
            KeyType.SYMBOLS2 -> setKind(LayoutKind.SYMBOLS2)
            KeyType.LETTERS -> setKind(LayoutKind.LETTERS)
            KeyType.EMOJI -> showPanel(Panel.EMOJI)
            KeyType.GLOBE -> nextLanguage()
            else -> {}
        }
    }

    override fun onBackspace(repeat: Boolean) {
        if (panel == Panel.EMOJI_SEARCH) {
            val q = searchView.query
            if (q.isNotEmpty()) searchView.setQuery(q.substring(0, q.offsetByCodePoints(q.length, -1)))
            return
        }
        deleteBackward()
    }

    override fun onCursorMove(dir: Int) {
        if (panel == Panel.EMOJI_SEARCH) return
        markEdit()
        autoSpaced = false
        ime.sendDownUpKeyEvents(if (dir < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT)
        updateSuggestions()
    }

    override fun onKeyLongPress(key: Key) {
        val imm = ime.getSystemService(InputMethodManager::class.java)
        imm?.showInputMethodPicker()
    }

    // ================= matn amallari =================

    private fun ic(): InputConnection? = ime.currentInputConnection

    private fun markEdit() {
        lastOwnEdit = SystemClock.uptimeMillis()
    }

    private fun isApostrophe(c: Char) = c == '\'' || c == 'ʻ' || c == 'ʼ' || c == '’' || c == '‘' || c == '`'

    private fun isWordChar(c: Char) = c.isLetter() || isApostrophe(c)

    private fun currentWord(before: CharSequence?): String {
        if (before.isNullOrEmpty()) return ""
        var i = before.length
        while (i > 0 && isWordChar(before[i - 1])) i--
        var w = before.subSequence(i, before.length).toString()
        while (w.isNotEmpty() && isApostrophe(w[0])) w = w.substring(1)
        return w
    }

    private fun learnPreviousWord(ic: InputConnection) {
        if (!learningAllowed) return
        val w = currentWord(ic.getTextBeforeCursor(48, 0))
        if (w.length >= 2) ime.suggest.learn(lang, w)
    }

    private fun afterSeparator() {
        updateAutoShift()
        updateSuggestions()
    }

    private fun handleShift() {
        if (kind != LayoutKind.LETTERS) return
        val now = SystemClock.uptimeMillis()
        shiftState = when (shiftState) {
            0 -> 1
            1 -> if (now - lastShiftTap < 380L) 2 else 0
            else -> 0
        }
        lastShiftTap = now
        keyboard.shiftState = shiftState
    }

    private fun handleSpace() {
        if (panel == Panel.EMOJI_SEARCH) {
            searchView.setQuery(searchView.query + " ")
            return
        }
        val ic = ic() ?: return
        markEdit()
        val now = SystemClock.uptimeMillis()
        learnPreviousWord(ic)
        if (prefs.doubleSpacePeriod && variant == FieldVariant.NORMAL && now - lastSpaceTime < 1000L) {
            val before = ic.getTextBeforeCursor(2, 0)
            if (before != null && before.length == 2 && before[1] == ' ' && (before[0].isLetterOrDigit() || before[0] in "\"')»”")) {
                ic.beginBatchEdit()
                ic.deleteSurroundingText(1, 0)
                ic.commitText(". ", 1)
                ic.endBatchEdit()
                lastSpaceTime = 0L
                autoSpaced = false
                afterSeparator()
                return
            }
        }
        ic.commitText(" ", 1)
        lastSpaceTime = now
        autoSpaced = false
        afterSeparator()
    }

    private fun handleEnter() {
        if (panel == Panel.EMOJI_SEARCH) {
            showPanel(Panel.EMOJI)
            return
        }
        val ic = ic() ?: return
        markEdit()
        learnPreviousWord(ic)
        autoSpaced = false
        val ei = editorInfo
        val action = (ei?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION
        val noEnterAction = ei == null || (ei.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        if (!noEnterAction && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(action)
        } else {
            ime.sendKeyChar('\n')
        }
        afterSeparator()
    }

    private fun deleteBackward() {
        val ic = ic() ?: return
        markEdit()
        autoSpaced = false
        lastSpaceTime = 0L
        val sel = ic.getSelectedText(0)
        if (!sel.isNullOrEmpty()) {
            ic.commitText("", 1)
        } else {
            val before = ic.getTextBeforeCursor(32, 0)
            if (before == null || before.isEmpty()) {
                ime.sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
            } else {
                ic.deleteSurroundingText(lastGraphemeLength(before.toString()), 0)
            }
        }
        updateAutoShift()
        updateSuggestions()
    }

    private fun lastGraphemeLength(s: String): Int {
        if (s.isEmpty()) return 0
        return try {
            val bi = BreakIterator.getCharacterInstance()
            bi.setText(s)
            val end = s.length
            val prev = bi.preceding(end)
            if (prev == BreakIterator.DONE || prev < 0) 1 else end - prev
        } catch (_: Exception) {
            if (s.length >= 2 && Character.isLowSurrogate(s[s.length - 1])) 2 else 1
        }
    }

    private fun updateAutoShift() {
        if (kind != LayoutKind.LETTERS || shiftState == 2 || panel == Panel.EMOJI_SEARCH) return
        val ei = editorInfo
        val ic = ic()
        val caps = if (autoCapAllowed && ei != null && ic != null) ic.getCursorCapsMode(ei.inputType) != 0 else false
        shiftState = if (caps) 1 else 0
        keyboard.shiftState = shiftState
    }

    private fun updateSuggestions() {
        if (panel != Panel.KEYS) return
        if (!suggestionsAllowed || kind == LayoutKind.NUMPAD) {
            toolbar.setSuggestions(null, emptyList())
            return
        }
        val ic = ic()
        if (ic == null) {
            toolbar.setSuggestions(null, emptyList())
            return
        }
        val word = currentWord(ic.getTextBeforeCursor(48, 0))
        if (word.isEmpty()) {
            toolbar.setSuggestions(null, emptyList())
            return
        }
        val list = ime.suggest.suggest(lang, word, prefs.apostropheStyle == 0)
        toolbar.setSuggestions(word, list)
    }

    private fun nextLanguage() {
        val langs = prefs.languages
        if (langs.size < 2) return
        val idx = langs.indexOf(lang)
        lang = langs[(idx + 1) % langs.size]
        prefs.currentLang = lang
        ime.suggest.prepare(lang)
        if (kind != LayoutKind.LETTERS) kind = LayoutKind.LETTERS
        rebuildLayout()
        updateAutoShift()
        updateSuggestions()
    }

    // ================= ToolbarView.Host =================

    override val themeMode: Int get() = prefs.theme

    override fun onToolbarAction(action: Int) {
        ime.feedback.key(KeyType.CHAR)
        when (action) {
            ToolbarView.ACTION_SETTINGS -> {
                val i = Intent(ime, SettingsActivity::class.java)
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                try {
                    ime.startActivity(i)
                } catch (_: Exception) {
                }
            }
            ToolbarView.ACTION_CLIPBOARD -> showPanel(Panel.CLIPBOARD)
            ToolbarView.ACTION_THEME -> {
                prefs.theme = (prefs.theme + 1) % 3
                applyTheme()
            }
            ToolbarView.ACTION_HIDE -> ime.requestHideSelf(0)
            ToolbarView.ACTION_BACK -> showPanel(Panel.KEYS)
            ToolbarView.ACTION_CLEAR_CLIPS -> {
                ime.clearClips()
                clipPanel.refresh()
            }
        }
    }

    override fun onSuggestionPicked(text: String, isTyped: Boolean) {
        val ic = ic() ?: return
        ime.feedback.key(KeyType.CHAR)
        markEdit()
        if (isTyped) {
            if (learningAllowed) ime.suggest.learn(lang, text)
            ic.commitText(" ", 1)
        } else {
            val word = currentWord(ic.getTextBeforeCursor(48, 0))
            ic.beginBatchEdit()
            if (word.isNotEmpty()) ic.deleteSurroundingText(word.length, 0)
            ic.commitText("$text ", 1)
            ic.endBatchEdit()
            if (learningAllowed) ime.suggest.learn(lang, text)
        }
        autoSpaced = true
        lastSpaceTime = 0L
        afterSeparator()
    }

    // ================= Emoji =================

    override fun onEmoji(emoji: String) {
        val ic = ic() ?: return
        markEdit()
        ic.commitText(emoji, 1)
        autoSpaced = false
        val recents = ArrayList<String>()
        recents.add(emoji)
        for (r in prefs.recentEmoji) if (r != emoji && recents.size < 36) recents.add(r)
        prefs.recentEmoji = recents
    }

    override fun onEmojiBackspace(repeat: Boolean) = deleteBackward()

    override fun onEmojiAbc() = showPanel(Panel.KEYS)

    override fun onEmojiSearch() = showPanel(Panel.EMOJI_SEARCH)

    override fun onEmojiSearchClose() = showPanel(Panel.EMOJI)

    override fun onEmojiFeedback() = ime.feedback.key(KeyType.CHAR)

    override fun abcLabel(): String = lang.lettersLabel

    // ================= Bufer =================

    override fun clipItems(): List<String> = ime.clipHistory()

    override fun onClipPaste(text: String) {
        val ic = ic() ?: return
        markEdit()
        ic.commitText(text, 1)
        showPanel(Panel.KEYS)
    }

    override fun onClipDelete(text: String) = ime.removeClip(text)

    override fun onClipFeedback() = ime.feedback.key(KeyType.CHAR)
}
