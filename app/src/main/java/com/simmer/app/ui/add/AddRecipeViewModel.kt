package com.simmer.app.ui.add

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import android.content.Context
import android.content.res.Configuration
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simmer.app.data.AddRecipeSession
import com.simmer.app.data.CaptionFetcher
import com.simmer.app.data.ListenSession
import com.simmer.app.data.ReelCard
import com.simmer.app.data.ReelImport
import com.simmer.app.data.ShareInbox
import com.simmer.app.domain.ListenState
import com.simmer.app.domain.SharedContent
import com.simmer.app.domain.readSharedText
import com.simmer.app.domain.withCaption
import kotlinx.coroutines.flow.filterNotNull
import com.simmer.app.data.ImportJob
import com.simmer.app.reader.ReaderState
import com.simmer.app.reader.ReaderStateSource
import com.simmer.app.data.PrefsRepository
import com.simmer.app.data.RecipeDraftStore
import com.simmer.app.data.draftFromJson
import com.simmer.app.data.toDraftJson
import com.simmer.app.domain.AddRecipeReview
import com.simmer.app.domain.AddStep
import com.simmer.app.domain.addStepFromKey
import com.simmer.app.domain.back
import com.simmer.app.domain.EXAMPLE_TEXT
import com.simmer.app.domain.KitchenLine
import com.simmer.app.domain.ImportRequest
import com.simmer.app.domain.ImportState
import com.simmer.app.domain.ImportStyle
import com.simmer.app.domain.KitchenMode
import com.simmer.app.domain.PipelineStage
import com.simmer.app.domain.ReaderStatus
import com.simmer.app.domain.ReaderAction
import com.simmer.app.domain.ReplyError
import com.simmer.app.domain.ReplyResult
import com.simmer.app.domain.addRecipeReview
import com.simmer.app.domain.canConvert
import com.simmer.app.domain.kitchenLine
import com.simmer.app.domain.orderAppliances
import com.simmer.app.domain.newRecipeId
import com.simmer.app.domain.readerAction
import com.simmer.app.domain.usesExampleShortcut
import com.simmer.app.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

private const val KEY_TEXT = "text"
private const val KEY_REPLY = "reply"
private const val KEY_REPLY_ERROR = "reply_error"
private const val KEY_REPLY_ERROR_DETAIL = "reply_error_detail"
private const val KEY_PROMPT_TEXT = "prompt_text"
private const val KEY_STEP = "step"
private const val KEY_TAB = "tab"
private const val KEY_DRAFT = "draft"
private const val KEY_SHARED = "shared"

/** The cook's Appliance keys in catalogue order (custom ones after) and Kitchen mode, as the Reader prompt needs them. */
data class PromptKitchen(val appliances: List<String>, val mode: KitchenMode)

private data class ChatbotInputs(val kitchen: PromptKitchen, val lang: String, val promptText: String, val reply: String, val error: ReplyResult.Failed?, val style: ImportStyle)

/** How the review shows amounts and labels. */
private data class DisplayPrefs(val units: String, val spices: String, val lang: String)

/** The review's two tabs. */
enum class ReviewTab { Ingredients, Steps }

sealed interface AddRecipeUi {
    data object Loading : AddRecipeUi

    /** The start stage: how to add the recipe. [reader] is the engine and the Reader model's state, shown on "On this phone". */
    data class Start(val reader: ReaderState) : AddRecipeUi

    /**
     * The chatbot path: the optional recipe text for the Reader prompt, what the prompt needs besides it
     * (the Kitchen and the Settings language), the pasted [reply] and whether "Add recipe" is offered (it needs some text).
     */
    data class Chatbot(
        val promptText: String,
        val kitchen: PromptKitchen,
        val lang: String,
        val reply: String,
        val canAdd: Boolean,
        /** Why the last "Add recipe" failed; shown with "Copy fix prompt" until the reply changes. */
        val error: ReplyResult.Failed?,
        val style: ImportStyle = ImportStyle.Faithful,
    ) : AddRecipeUi

    /** The paste stage. */
    data class Paste(
        val text: String,
        /** Convert for the example, otherwise the Download placeholder. */
        val action: ReaderAction,
        /** False below the minimum text length: the bottom button is disabled. */
        val canConvert: Boolean,
        val kitchen: KitchenLine,
        val style: ImportStyle = ImportStyle.Faithful,
    ) : AddRecipeUi

    /** The Working stage: the Pipeline is running the pass at [stage]. */
    data class Working(val stage: PipelineStage) : AddRecipeUi

    /** The error stage: the Pipeline could not read the text. */
    data object Error : AddRecipeUi

    /** The review stage for the unsaved recipe. */
    data class Review(val review: AddRecipeReview, val tab: ReviewTab) : AddRecipeUi
}

/**
 * The pasted text, the pasted chatbot reply, the review tab and the converted recipe (the [RecipeDraftStore] draft, as JSON)
 * live in [SavedStateHandle], so the screen comes back at the same stage after process death.
 */
@HiltViewModel
class AddRecipeViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val session: AddRecipeSession,
    private val drafts: RecipeDraftStore,
    private val job: ImportJob,
    private val prefs: PrefsRepository,
    readerState: ReaderStateSource,
    fetcher: CaptionFetcher,
    private val inbox: ShareInbox,
    private val listenSession: ListenSession,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    /** Straight from saved state so typing never lags behind the screen state. */
    val text: StateFlow<String> = savedState.getStateFlow(KEY_TEXT, "")

    /** Straight from saved state, like [text]. */
    val reply: StateFlow<String> = savedState.getStateFlow(KEY_REPLY, "")

    /**
     * Why the last "Add recipe" failed, until the reply is edited (kept in saved state as the [ReplyError] name
     * and the parser's detail). A saved name that is no longer a [ReplyError] (an older build) deliberately
     * restores as no error: the cook simply taps Add recipe again to see the current one.
     */
    private val replyError: Flow<ReplyResult.Failed?> =
        combine(
            savedState.getStateFlow<String?>(KEY_REPLY_ERROR, null),
            savedState.getStateFlow(KEY_REPLY_ERROR_DETAIL, ""),
        ) { name, detail ->
            ReplyError.entries.firstOrNull { it.name == name }?.let { ReplyResult.Failed(it, detail) }
        }

    /** Straight from saved state, like [text]: the optional recipe text for the Reader prompt. */
    val promptText: StateFlow<String> = savedState.getStateFlow(KEY_PROMPT_TEXT, "")

    /** The Reel import: the card's state and the shared link; a found caption goes into the text field. */
    private val reelImport = ReelImport(savedState, fetcher, viewModelScope) { caption ->
        setText(withCaption(text.value, caption, captionLabel(prefs.lang.first())))
        fillChatbotPrompt()
    }

    /** The caption's label line in the Settings language, which may differ from the device's. */
    private fun captionLabel(lang: String): String = labelIn(lang, R.string.reel_caption_label)

    private fun labelIn(lang: String, id: Int): String {
        val config = Configuration(context.resources.configuration).apply { setLocale(Locale(lang)) }
        return context.createConfigurationContext(config).getString(id)
    }

    /** Listen: the shared run, its state shown on the Reel card. */
    val listen: StateFlow<ListenState> = listenSession.state

    /** The shared Reel link Listen opens in Instagram; null when the text did not come from a Reel. */
    val reelUrl: String? get() = reelImport.sourceUrl

    fun listen() = listenSession.listen()
    fun confirmListenDownload() = listenSession.confirmDownload()
    fun cancelListen() = listenSession.cancel()
    fun stopListen() = listenSession.stop()
    fun listenPermissionGranted() = listenSession.permissionGranted()
    fun listenPermissionDenied() = listenSession.permissionDenied()
    fun listenConsentDenied() = listenSession.consentDenied()
    fun dismissListen() = listenSession.dismiss()

    /** The Reel card's Listen buttons; [paste] puts the clipboard into the stage's text field. */
    internal fun listenActions(paste: () -> Unit) = ListenCardActions(
        onListen = ::listen,
        onConfirmDownload = ::confirmListenDownload,
        onStop = ::stopListen,
        onCancel = ::cancelListen,
        onDismiss = ::dismissListen,
        onPaste = {
            dismissListen()
            paste()
        },
    )

    /** The Reel card on the paste stage; null unless the user arrived by sharing an Instagram link. */
    val reel: StateFlow<ReelCard?> = reelImport.card.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val tab = savedState.getStateFlow(KEY_TAB, ReviewTab.Ingredients.name)

    private val display = combine(prefs.units, prefs.spices, prefs.lang, ::DisplayPrefs)
    private val kitchen = combine(prefs.appliances, prefs.kitchenMode) { appliances, mode ->
        kitchenLine(appliances, KitchenMode.fromKey(mode))
    }

    private val readerFlow = readerState.state
    private val readerReady = readerFlow.map { it.status == ReaderStatus.Ready }

    private val step = savedState.getStateFlow(KEY_STEP, AddStep.Start.key)

    private val style = prefs.importStyle.map { ImportStyle.fromKey(it) }

    private val paste = combine(text, kitchen, readerReady, style) { text, line, ready, style ->
        AddRecipeUi.Paste(text, readerAction(text, ready), canConvert(text), line, style)
    }

    private val replyAndError = combine(reply, replyError, style, ::Triple)

    private val chatbotInputs = combine(prefs.appliances, prefs.kitchenMode, prefs.lang, promptText, replyAndError) { appliances, mode, lang, promptText, (reply, error, style) ->
        ChatbotInputs(PromptKitchen(orderAppliances(appliances), KitchenMode.fromKey(mode)), lang, promptText, reply, error, style)
    }

    private val start = combine(step, paste, readerFlow, chatbotInputs) { step, paste, reader, chatbot ->
        when (addStepFromKey(step)) {
            AddStep.Start -> AddRecipeUi.Start(reader)
            AddStep.Chatbot -> AddRecipeUi.Chatbot(chatbot.promptText, chatbot.kitchen, chatbot.lang, chatbot.reply, chatbot.reply.isNotBlank(), chatbot.error, chatbot.style)
            AddStep.Phone -> paste
        }
    }

    val ui: StateFlow<AddRecipeUi> = combine(start, drafts.current, tab, display, job.state) { paste, draft, tab, display, state ->
        when {
            draft != null ->
                AddRecipeUi.Review(addRecipeReview(draft, display.units, display.spices, display.lang), ReviewTab.valueOf(tab))
            state is ImportState.Working -> AddRecipeUi.Working(state.stage)
            state == ImportState.Failed -> AddRecipeUi.Error
            else -> paste
        }
    }.onEach { if (it is AddRecipeUi.Review) reviewShown = true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AddRecipeUi.Loading)

    /** A conversion is running or a recipe is open in the review: leaving them for a share would lose work. */
    private val busy = combine(drafts.current, job.state) { draft, state -> draft != null || state is ImportState.Working }

    private var saving = false
    private var adding = false

    /** True once this screen has shown the review: only then is leaving it a decision to drop the recipe. */
    private var reviewShown = false

    init {
        // A conversion that was running when the process died resumes (or shows the error stage).
        job.restore()
        reelImport.resume()
        listenSession.restore()
        // A finished transcript (also one that was waiting since before process death) goes into the field once.
        viewModelScope.launch {
            listenSession.state.collect { state ->
                if (state is ListenState.Done) {
                    setText(withCaption(text.value, state.transcript, labelIn(prefs.lang.first(), R.string.listen_transcript_label)))
                    fillChatbotPrompt()
                    listenSession.acknowledge()
                }
            }
        }
        // A share waits in the inbox while a recipe is being converted or reviewed, so it cannot destroy that work.
        viewModelScope.launch {
            combine(inbox.pending.filterNotNull(), busy) { shared, busy -> shared.takeUnless { busy } }
                .filterNotNull()
                .collect {
                    receiveShared(it)
                    inbox.taken(it)
                }
        }
        // Back after process death: put the saved draft in the store, then keep the saved copy current.
        savedState.get<String>(KEY_DRAFT)?.let { if (drafts.current.value == null) drafts.set(draftFromJson(it)) }
        viewModelScope.launch {
            drafts.current.collect { savedState[KEY_DRAFT] = it?.toDraftJson() }
        }
    }

    /** True once something was shared to Simmer; the paste stage then offers the chatbot path directly. */
    val shared = savedState.getStateFlow(KEY_SHARED, false)

    /** While the chatbot path is open after a share, its prompt text follows the field (the caption arrives later). */
    private fun fillChatbotPrompt() {
        if (addStepFromKey(savedState.get<String>(KEY_STEP)) == AddStep.Chatbot && savedState.get<Boolean>(KEY_SHARED) == true) {
            setPromptText(text.value)
        }
    }

    fun choose(step: AddStep) {
        savedState[KEY_STEP] = step.key
        // After a share the chatbot path starts with the field's text, ready to copy into the prompt.
        if (step == AddStep.Chatbot && savedState.get<Boolean>(KEY_SHARED) == true && promptText.value.isBlank()) {
            setPromptText(text.value)
        }
    }

    /**
     * Text shared to Simmer: an Instagram link shows the Reel card and fetches its caption into the field;
     * any other text goes into the field as if pasted. Either way Add recipe opens the stage the Instagram import setting picks (chatbot by default).
     */
    suspend fun receiveShared(shared: String) {
        val content = readSharedText(shared) ?: return
        job.dismissError()
        savedState[KEY_SHARED] = true
        val engine = if (prefs.shareEngine.first() == "phone") AddStep.Phone else AddStep.Chatbot
        savedState[KEY_STEP] = engine.key
        setPromptText("")
        when (content) {
            is SharedContent.Reel -> {
                // Like plain shared text, a Reel replaces what was in the field.
                setText("")
                reelImport.start(content.url)
            }
            is SharedContent.Text -> {
                reelImport.clear()
                setText(content.text)
                fillChatbotPrompt()
            }
        }
    }

    /** Back on the start, chatbot and paste stages: returns to the start stage, or [onLeave] from the start stage. */
    fun back(onLeave: () -> Unit) {
        val previous = addStepFromKey(savedState.get<String>(KEY_STEP)).back()
        if (previous == null) onLeave() else choose(previous)
    }

    fun setText(text: String) {
        savedState[KEY_TEXT] = text
    }

    /** Empties the field; a shared Reel's card goes with it, so its link cannot end up on another recipe. */
    fun clear() {
        reelImport.clear()
        setText("")
    }

    fun setPromptText(text: String) {
        savedState[KEY_PROMPT_TEXT] = text
    }

    fun clearPromptText() = setPromptText("")

    fun setReply(reply: String) {
        savedState[KEY_REPLY] = reply
        savedState[KEY_REPLY_ERROR] = null
        savedState[KEY_REPLY_ERROR_DETAIL] = ""
    }

    fun clearReply() = setReply("")

    /**
     * Turns the pasted chatbot reply into the Add recipe draft, which shows the review. A reply that
     * holds no usable recipe changes nothing but sets the [ReplyError] the chatbot screen shows, which
     * the next edit of the reply clears. No Pipeline or ImportJob.
     * One add at a time, so a second tap cannot overwrite the draft.
     */
    private val replyFailedEvents = Channel<Unit>(Channel.CONFLATED)

    /** Fires each time Add recipe finds no usable recipe, so the screen can bring the error into view. */
    val replyFailed: Flow<Unit> = replyFailedEvents.receiveAsFlow()

    fun addFromReply() {
        if (adding) return
        adding = true
        val reply = reply.value
        viewModelScope.launch {
            try {
                val error = session.addFromReply(
                    reply = reply,
                    id = newRecipeId(),
                    appliances = prefs.appliances.first(),
                    kitchenMode = KitchenMode.fromKey(prefs.kitchenMode.first()),
                )
                // An edit made meanwhile has already cleared the error of the reply that was read.
                if (this@AddRecipeViewModel.reply.value == reply) {
                    savedState[KEY_REPLY_ERROR_DETAIL] = error?.detail.orEmpty()
                    savedState[KEY_REPLY_ERROR] = error?.error?.name
                    if (error != null) replyFailedEvents.trySend(Unit)
                }
            } finally {
                adding = false
            }
        }
    }

    fun useExample() {
        reelImport.clear()
        setText(EXAMPLE_TEXT)
    }

    /** Converts the text when it is long enough: the example directly, other text through the Pipeline. */
    fun convert() {
        val text = text.value
        if (!canConvert(text)) return
        if (usesExampleShortcut(text)) {
            session.convert(text, id = newRecipeId())
            return
        }
        viewModelScope.launch {
            job.start(
                ImportRequest(
                    text = text,
                    lang = prefs.lang.first(),
                    appliances = prefs.appliances.first(),
                    kitchenMode = KitchenMode.fromKey(prefs.kitchenMode.first()),
                    style = ImportStyle.fromKey(prefs.importStyle.first()),
                ),
            )
        }
    }

    /** The Faithful/Liberty toggle: remembered for the next import. */
    fun setImportStyle(style: ImportStyle) {
        viewModelScope.launch { prefs.setImportStyle(style.key) }
    }

    /** Cancel on the Working stage: stops generation and returns to the paste stage with the text kept. */
    fun cancel() = job.cancel()

    /** The error stage's button: back to the paste stage with the text kept. */
    fun dismissError() = job.dismissError()

    /** Edit text: back to the paste stage, with the text kept. */
    fun editText() {
        session.discard()
        job.acknowledge()
    }

    fun selectTab(tab: ReviewTab) {
        savedState[KEY_TAB] = tab.name
    }

    /** Saves the recipe, then reports its id. One save at a time. */
    fun save(onSaved: (recipeId: String) -> Unit) {
        if (saving) return
        saving = true
        viewModelScope.launch {
            try {
                session.save(reelImport.sourceUrl)?.let {
                    job.acknowledge()
                    reelImport.clear()
                    onSaved(it.id)
                }
            } finally {
                saving = false
            }
        }
    }

    /** Leaving the screen without saving drops the draft. */
    override fun onCleared() {
        job.leaveScreen(reviewShown)
    }
}
