package io.github.chrislo27.bouncyroadmania.engine

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.Disposable
import io.github.chrislo27.bouncyroadmania.BRMania
import io.github.chrislo27.bouncyroadmania.BRManiaApp
import io.github.chrislo27.bouncyroadmania.engine.entity.*
import io.github.chrislo27.bouncyroadmania.engine.event.*
import io.github.chrislo27.bouncyroadmania.engine.input.InputResult
import io.github.chrislo27.bouncyroadmania.engine.input.InputType
import io.github.chrislo27.bouncyroadmania.engine.input.Score
import io.github.chrislo27.bouncyroadmania.engine.timesignature.TimeSignatures
import io.github.chrislo27.bouncyroadmania.engine.tracker.TrackerContainer
import io.github.chrislo27.bouncyroadmania.engine.tracker.musicvolume.MusicVolumes
import io.github.chrislo27.bouncyroadmania.renderer.PaperProjection
import io.github.chrislo27.bouncyroadmania.soundsystem.beads.BeadsSound
import io.github.chrislo27.bouncyroadmania.soundsystem.beads.BeadsSoundSystem
import io.github.chrislo27.bouncyroadmania.util.scaleFont
import io.github.chrislo27.bouncyroadmania.util.unscaleFont
import io.github.chrislo27.toolboks.i18n.Localization
import io.github.chrislo27.toolboks.registry.AssetRegistry
import io.github.chrislo27.toolboks.util.MathHelper
import io.github.chrislo27.toolboks.util.gdxutils.*
import io.github.chrislo27.toolboks.version.Version
import java.util.*
import kotlin.math.roundToInt
import kotlin.properties.Delegates


class Engine : Clock(), Disposable {

    companion object {
        private val TMP_MATRIX = Matrix4()

        val MAX_OFFSET_SEC: Float = 7f / 60
        val ACE_OFFSET: Float = 1f / 60
        val GOOD_OFFSET: Float = 3.5f / 60
        val BARELY_OFFSET: Float = 5f / 60

        val MIN_TRACK_COUNT: Int = 4
        val MAX_TRACK_COUNT: Int = 8
        val DEFAULT_TRACK_COUNT: Int = MIN_TRACK_COUNT

        val MIN_BOUNCER_COUNT: Int = 5
        val MAX_BOUNCER_COUNT: Int = 15
        val DEFAULT_BOUNCER_COUNT: Int = 15

        val MIN_DIFFICULTY: Int = 1
        val MAX_DIFFICULTY: Int = 5
        val DEFAULT_DIFFICULTY: Int = 0

        val DEFAULT_GRADIENT: Color = Color.valueOf("0296FFFF")
        val DEFAULT_NORMAL_BOUNCER: Color = Color.valueOf("08BDFFFF")
        val DEFAULT_A_BOUNCER: Color = Color.valueOf("FFFF00FF")
        val DEFAULT_DPAD_BOUNCER: Color = Color.valueOf("FF0200FF")
    }

    var version: Version = BRMania.VERSION.copy()
    val camera: OrthographicCamera = OrthographicCamera().apply {
        setToOrtho(false, 1280f, 720f)
    }
    val projector = PaperProjection(2f)

    val timeSignatures: TimeSignatures = TimeSignatures()
    val musicVolumes: MusicVolumes = MusicVolumes()
    val trackers: List<TrackerContainer<*>> = listOf(tempos, musicVolumes)
    val trackersReverseView: List<TrackerContainer<*>> = trackers.asReversed()
    override var playState: PlayState by Delegates.vetoable(super.playState) { _, old, value ->
        if (old != value) {
            if (value == PlayState.PLAYING && tempos.secondsMap.isEmpty()) {
//                return@vetoable false
            }
            val music = music
            entities.forEach { it.onPlayStateChanged(old, value) }
            lastBounceTinkSound.clear()

            when (value) {
                PlayState.STOPPED -> {
                    AssetRegistry.stopAllSounds()
                    music?.music?.pause()
                    BeadsSoundSystem.stop()
                    entities.clear()
                    addBouncers()
                    currentTextBox = null
                }
                PlayState.PAUSED -> {
                    AssetRegistry.pauseAllSounds()
                    music?.music?.pause()
                    BeadsSoundSystem.pause()
                }
                PlayState.PLAYING -> {
                    resetMusic()
                    AssetRegistry.resumeAllSounds()
                    if (old == PlayState.STOPPED) {
                        recomputeCachedData()
                        resetInputs()
                        seconds = tempos.beatsToSeconds(playbackStart)
                        gradientStart.reset()
                        gradientEnd.reset()
                        normalBouncerTint.reset()
                        aBouncerTint.reset()
                        dpadBouncerTint.reset()
                        events.forEach {
                            if (it.getUpperUpdateableBound() < beat) {
                                if (it.shouldAlwaysBeSimulated) {
                                    it.playbackCompletion = PlaybackCompletion.PLAYING
                                    it.onStart()
                                    it.whilePlaying()
                                    it.playbackCompletion = PlaybackCompletion.FINISHED
                                    it.onEnd()
                                }
                                it.playbackCompletion = PlaybackCompletion.FINISHED
                            } else {
                                it.playbackCompletion = PlaybackCompletion.WAITING
                            }
                        }

                        lastMetronomeMeasure = Math.ceil(playbackStart - 1.0).toInt()
                        lastMetronomeMeasurePart = -1
                    }
                    BeadsSoundSystem.resume()
                    if (music != null) {
                        if (seconds >= musicStartSec) {
                            music.music.play()
                            setMusicVolume()
                            seekMusic()
                        } else {
                            music.music.stop()
                        }
                    }
                }
            }

            listeners.keys.forEach { it.onPlayStateChanged(old, value) }
        }
        true
    }

    var trackCount: Int = DEFAULT_TRACK_COUNT
        set(value) {
            field = value.coerceAtLeast(1)
            events.filterIsInstance<EndEvent>().forEach {
                it.bounds.y = 0f
                it.bounds.height = value.toFloat()
            }
        }
    var bouncerCount: Int = DEFAULT_BOUNCER_COUNT
        set(value) {
            field = if (value < 0) DEFAULT_BOUNCER_COUNT else value.coerceAtLeast(MIN_BOUNCER_COUNT)
            addBouncers()
        }
    var difficulty: Int = DEFAULT_DIFFICULTY
        set(value) {
            field = value.coerceIn(0, MAX_DIFFICULTY)
        }
    var duration: Float = Float.POSITIVE_INFINITY
        private set
    var lastPoint: Float = 0f
        private set
    var eventsTouchTrackTop: Boolean = false
        private set

    var playbackStart: Float = 0f
    var musicStartSec: Float = 0f
    var metronome: Boolean = false
    private var lastMetronomeMeasure: Int = -1
    private var lastMetronomeMeasurePart: Int = -1
    var music: MusicData? = null
        set(value) {
            field?.dispose()
            field = value
        }
    var isMusicMuted: Boolean = false
    private var lastMusicPosition: Float = -1f
    private var scheduleMusicPlaying = true
    @Volatile
    var musicSeeking = false
    var loopIndex: Int = 0
        private set

    val events: List<Event> = mutableListOf()
    val eventsReversed: List<Event> = events.asReversed()
    val inputResults: List<InputResult> = mutableListOf()
    var expectedNumInputs: Int = 0
    var skillStarInput: Float = Float.POSITIVE_INFINITY
        private set
    var gotSkillStar: Boolean = false
    var resultsText: ResultsText = ResultsText()

    val entities: MutableList<Entity> = mutableListOf()
    var bouncers: List<Bouncer> = listOf()
    lateinit var yellowBouncer: YellowBouncer
        private set
    lateinit var redBouncer: RedBouncer
        private set
    val lastBounceTinkSound: MutableMap<String, Float> = mutableMapOf()

    var requiresPlayerInput: Boolean = true
    val listeners: WeakHashMap<EngineEventListener, Unit> = WeakHashMap()

    // Visuals
    val textures: Map<String, Texture> = mutableMapOf()
    var gradientDirection: GradientDirection = GradientDirection.VERTICAL
    val gradientEnd: InterpolatableColor = InterpolatableColor(DEFAULT_GRADIENT)
    val gradientStart: InterpolatableColor = InterpolatableColor(Color.BLACK)
    val normalBouncerTint: InterpolatableColor = InterpolatableColor(DEFAULT_NORMAL_BOUNCER)
    val aBouncerTint: InterpolatableColor = InterpolatableColor(DEFAULT_A_BOUNCER)
    val dpadBouncerTint: InterpolatableColor = InterpolatableColor(DEFAULT_DPAD_BOUNCER)
    private var skillStarSpinAnimation: Float = 0f
    private var skillStarPulseAnimation: Float = 0f

    // Practice related
    var currentTextBox: TextBox? = null
    var xMoreTimes: Int = 0
    var clearText: Float = 0f

    fun addBouncers() {
        entities.removeAll(bouncers)
        bouncers = listOf()

        val radius = 1200f
        val bouncers = mutableListOf<Bouncer>()
        val numVisibleBouncers = this.bouncerCount
        val midpoint = ((numVisibleBouncers / 2f).roundToInt() * 1f).coerceAtMost(numVisibleBouncers - 3f)
        for (i in -1..numVisibleBouncers) {
            val angle = (180.0 * (i / (numVisibleBouncers - 1.0))) - 90
            val bouncer: Bouncer = if (i == numVisibleBouncers - 2) {
                RedBouncer(this).apply {
                    redBouncer = this
                }
            } else if (i == numVisibleBouncers - 3) {
                YellowBouncer(this).apply {
                    yellowBouncer = this
                }
            } else {
                Bouncer(this)
            }

            if (i !in 0 until numVisibleBouncers) {
                bouncer.isSilent = true
            } else if (i == numVisibleBouncers - 1) {
                bouncer.soundHandle = "sfx_cymbal"
            }

            val sin = Math.sin(Math.toRadians(angle)).toFloat()
            val z: Float = (-sin + 1) * 0.3f

            bouncer.posY = Interpolation.sineIn.apply(640f, 150f, i / (numVisibleBouncers - 1f))

            bouncer.posX = radius * if (i <= midpoint) Interpolation.sineOut.apply(i / midpoint) else Interpolation.sineOut.apply(1f - ((i - midpoint) / (numVisibleBouncers - 1 - midpoint)))
            bouncer.posZ = z

            bouncers += bouncer
        }
        this.bouncers = bouncers
        entities.addAll(bouncers)
    }

    init {
        addBouncers()
    }

    fun addEvent(event: Event) {
        if (event !in events) {
            (events as MutableList) += event
            events.sortWith(EventPosComparator)
            recomputeCachedData()
            listeners.keys.forEach { it.onEventAdded(event) }
        }
    }

    fun removeEvent(event: Event) {
        val oldSize = events.size
        (events as MutableList) -= event
        events.sortWith(EventPosComparator)
        if (events.size != oldSize) {
            recomputeCachedData()
            listeners.keys.forEach { it.onEventRemoved(event) }
        }
    }

    fun addAllEvents(events: Collection<Event>) {
        this.events as MutableList
        val oldSize = this.events.size
        events.forEach {
            if (it !in this.events) {
                this.events += it
                listeners.keys.forEach { l -> l.onEventAdded(it) }
            }
        }
        if (this.events.size != oldSize) {
            this.events.sortWith(EventPosComparator)
            recomputeCachedData()
        }
    }

    fun removeAllEvents(events: Collection<Event>) {
        this.events as MutableList
        val oldSize = this.events.size
        this.events.removeAll(events)
        events.forEach { listeners.keys.forEach { l -> l.onEventRemoved(it) } }
        if (this.events.size != oldSize) {
            this.events.sortWith(EventPosComparator)
            recomputeCachedData()
        }
    }

    fun addInputResult(input: InputResult) {
        inputResults as MutableList
        inputResults += input
        listeners.keys.forEach { l -> l.onInputReceived(input) }
    }

    fun computeScore(): Score {
        val scoreRaw = (inputResults.sumByDouble { it.inputScore.weight.toDouble() } / expectedNumInputs.coerceAtLeast(1) * 100).toFloat()
        val scoreInt = scoreRaw.roundToInt().coerceIn(0, 100)
        val resultsText = this.resultsText
        val line: String = when (scoreInt) {
            in 0 until 50 -> resultsText.secondNegative
            in 50 until 60 -> resultsText.firstNegative
            in 60 until 75 -> resultsText.ok
            in 75 until 85 -> resultsText.firstPositive
            in 85..100 -> resultsText.secondPositive
            else -> "<score was not in range from 0..100>"
        }
        return Score(scoreInt, scoreRaw, gotSkillStar, inputResults.size == expectedNumInputs, resultsText.title, line)
    }

    fun getDifficultyString(): String = if (difficulty == 0) "" else "[YELLOW]${"★".repeat(difficulty)}[][GRAY]${"★".repeat(Engine.MAX_DIFFICULTY - difficulty)}[]"

    private fun setMusicVolume() {
        val music = music ?: return
        val shouldBe = if (isMusicMuted) 0f else musicVolumes.volumeAt(beat)
        if (music.music.getVolume() != shouldBe) {
            music.music.setVolume(shouldBe)
        }
    }

    fun seekMusic() {
        val music = music ?: return
        musicSeeking = true
        val loops = music.music.isLooping()
        val s = seconds
        if (loops) {
            val loopPos = (s - musicStartSec) % music.music.getDuration()
            music.music.setPosition(loopPos)
            loopIndex = ((seconds - musicStartSec) / music.music.getDuration()).toInt()
        } else {
            music.music.setPosition(s - musicStartSec)
        }
        musicSeeking = false
    }

    fun resetMusic() {
        lastMusicPosition = 0f
        scheduleMusicPlaying = true
    }

    fun recomputeCachedData() {
        lastPoint = events.firstOrNull { it is EndEvent }?.bounds?.x ?: events.maxBy { it.bounds.maxX }?.bounds?.maxX ?: 0f
        duration = events.firstOrNull { it is EndEvent }?.bounds?.x ?: Float.POSITIVE_INFINITY
        skillStarInput = events.filterIsInstance<SkillStarEvent>().firstOrNull()?.bounds?.x ?: Float.POSITIVE_INFINITY
        eventsTouchTrackTop = events.filterNot { it is EndEvent }.firstOrNull { (it.bounds.y + it.bounds.height).toInt() >= trackCount } != null
        Gdx.app.postRunnable {
            textures as MutableMap
            textures.keys.toList().filter { key -> events.none { it is BgImageEvent && it.textureHash == key } }.forEach { key ->
                textures.getValue(key).dispose()
                textures.remove(key)
            }
        }
    }

    fun eventUpdate(event: Event) {
        if (event.playbackCompletion == PlaybackCompletion.WAITING) {
            if (event.isUpdateable(beat)) {
                event.playbackCompletion = PlaybackCompletion.PLAYING
                event.onStart()
            } else if (beat >= event.getUpperUpdateableBound()) {
                if (event.shouldAlwaysBeSimulated) {
                    event.playbackCompletion = PlaybackCompletion.PLAYING
                    event.onStart()
                    event.whilePlaying()
                }
                event.onEnd()
                event.playbackCompletion = PlaybackCompletion.FINISHED
            }
        }

        if (event.playbackCompletion == PlaybackCompletion.PLAYING) {
            event.whilePlaying()
            if (beat >= event.getUpperUpdateableBound()) {
                event.playbackCompletion = PlaybackCompletion.FINISHED
                event.onEnd()
            }
        }
    }

    override fun update(delta: Float) {
        val textBox = currentTextBox
        if (textBox != null && textBox.requiresInput) {
            if (textBox.secsBeforeCanInput > 0f) {
                textBox.secsBeforeCanInput -= Gdx.graphics.deltaTime // Don't use delta
            }
            if (textBox.secsBeforeCanInput <= 0f) {
                if (!requiresPlayerInput) {
                    // Robot mode
                    this.currentTextBox = null
                    playState = PlayState.PLAYING
                }
            }
        }

        if (skillStarInput.isFinite()) {
            if (skillStarSpinAnimation > 0) {
                skillStarSpinAnimation -= Gdx.graphics.deltaTime / 1f
                if (skillStarSpinAnimation < 0)
                    skillStarSpinAnimation = 0f
            }
            if (skillStarPulseAnimation > 0) {
                skillStarPulseAnimation -= Gdx.graphics.deltaTime / 0.5f
                if (skillStarPulseAnimation < 0)
                    skillStarPulseAnimation = 0f
            } else {
                // Pulse before skill star input
                val threshold = 0.1f
                for (i in 0 until 4) {
                    val beatPoint = tempos.beatsToSeconds(skillStarInput - i)
                    if (seconds in beatPoint..beatPoint + threshold) {
                        skillStarPulseAnimation = 0.5f
                        break
                    }
                }
            }
        }

        // No super update
        if (playState != PlayState.PLAYING)
            return

        val music: MusicData? = music
        music?.music?.update(if (playState == PlayState.PLAYING) (delta * 0.75f) else delta)

        // Timing
        seconds += delta

        if (music != null) {
            if (scheduleMusicPlaying && seconds >= musicStartSec) {
                music.music.play()
                scheduleMusicPlaying = false
            }
            if (music.music.isPlaying()) {
                val oldPosition = lastMusicPosition
                val newPosition = music.music.getPosition()
                lastMusicPosition = newPosition

                if (oldPosition != newPosition) {
                    seconds = if (music.music.isLooping()) {
                        if (newPosition < oldPosition) {
                            loopIndex++
                        }
                        newPosition + musicStartSec + loopIndex * music.music.getDuration()
                    } else {
                        newPosition + musicStartSec
                    }
                }

                setMusicVolume()
            }
        }

        events.forEach { event ->
            if (event.playbackCompletion != PlaybackCompletion.FINISHED) {
                eventUpdate(event)
            }
        }

        // Updating
        entities.forEach {
            it.renderUpdate(delta)
        }
        entities.removeIf { it.kill }

        val measure = timeSignatures.getMeasure(beat).takeIf { it >= 0 } ?: Math.floor(beat.toDouble()).toInt()
        val measurePart = timeSignatures.getMeasurePart(beat)
        if (lastMetronomeMeasure != measure || lastMetronomeMeasurePart != measurePart) {
            lastMetronomeMeasure = measure
            lastMetronomeMeasurePart = measurePart
            if (metronome) {
                val isStartOfMeasure = measurePart == 0
                AssetRegistry.get<BeadsSound>("sfx_cowbell").play(loop = false, volume = 1.25f, pitch = if (isStartOfMeasure) 1.5f else 1.1f)
            }
        }

        if (playState != PlayState.STOPPED && beat >= duration) {
            playState = PlayState.STOPPED
        }
    }

    private fun renderBgImageEvent(batch: SpriteBatch, evt: BgImageEvent) {
        val image = textures[evt.textureHash]
        val alpha = evt.getImageAlpha()
        if (image != null && alpha > 0f) {
            batch.setColor(1f, 1f, 1f, alpha)
            when (val rType = evt.renderType) {
                BgImageEvent.RenderType.FILL -> {
                    batch.draw(image, 0f, 0f, camera.viewportWidth, camera.viewportHeight)
                }
                else -> {
                    val aspectWidth = camera.viewportWidth / image.width
                    val aspectHeight = camera.viewportHeight / image.height
                    val aspectRatio = if (rType == BgImageEvent.RenderType.SCALE_TO_FIT) Math.min(aspectWidth, aspectHeight) else Math.max(aspectWidth, aspectHeight)
                    val x: Float
                    val y: Float
                    val w: Float
                    val h: Float

                    w = image.width * aspectRatio
                    h = image.height * aspectRatio
                    x = camera.viewportWidth / 2 - (w / 2)
                    y = camera.viewportHeight / 2 - (h / 2)

                    batch.draw(image, x, y, w, h)
                }
            }
            batch.setColor(1f, 1f, 1f, 1f)
        }
    }

    fun render(batch: SpriteBatch) {
        camera.update()
        TMP_MATRIX.set(batch.projectionMatrix)
        batch.projectionMatrix = camera.combined
        batch.begin()
        // gradient
        if (gradientDirection == GradientDirection.VERTICAL) {
            batch.drawQuad(-400f, 0f, gradientStart.current, camera.viewportWidth, 0f, gradientStart.current, camera.viewportWidth, camera.viewportHeight, gradientEnd.current, -400f, camera.viewportHeight, gradientEnd.current)
        } else {
            batch.drawQuad(-400f, 0f, gradientStart.current, camera.viewportWidth, 0f, gradientEnd.current, camera.viewportWidth, camera.viewportHeight, gradientEnd.current, -400f, camera.viewportHeight, gradientStart.current)
        }

        if (playState != PlayState.STOPPED) {
            // Background images
            events.forEach { evt ->
                if (evt is BgImageEvent && !evt.foreground) {
                    renderBgImageEvent(batch, evt)
                }
            }
        }

        projector.render(batch, entities)

        if (playState != PlayState.STOPPED) {
            // Foreground images
            events.forEach { evt ->
                if (evt is BgImageEvent && evt.foreground) {
                    renderBgImageEvent(batch, evt)
                }
            }
            // Spotlights
            val spotlight = eventsReversed.firstOrNull { it is SpotlightEvent && it.playbackCompletion == PlaybackCompletion.PLAYING } as SpotlightEvent?
            if (spotlight != null) {
                val shapeRenderer = BRManiaApp.instance.shapeRenderer
                shapeRenderer.prepareStencilMask(batch, inverted = true) {
                    shapeRenderer.projectionMatrix = camera.combined
                    shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
                    val spotlightRadius = 128f
                    val segments = (8f * Math.cbrt(spotlightRadius * (BRManiaApp.instance.defaultCamera.viewportWidth / 1280.0)).toFloat()).roundToInt().coerceAtLeast(32)
                    shapeRenderer.circle(yellowBouncer.posX, yellowBouncer.posY, spotlightRadius, segments)
                    shapeRenderer.circle(redBouncer.posX, redBouncer.posY, spotlightRadius, segments)
                    shapeRenderer.end()
                    shapeRenderer.projectionMatrix = BRManiaApp.instance.defaultCamera.combined
                }.useStencilMask {
                    batch.color = spotlight.shadow
                    batch.fillRect(-400f, 0f, camera.viewportWidth + 800f, camera.viewportHeight)
                    batch.setColor(1f, 1f, 1f, 1f)
                }
            }
        }

        val textBox = currentTextBox
        if (textBox != null) {
            val font = BRManiaApp.instance.defaultFontLarge
            font.scaleFont(camera)
            font.scaleMul(0.5f)
            font.setColor(0f, 0f, 0f, 1f)
            // Render text box
            val backing = AssetRegistry.get<Texture>("ui_textbox")
            val texW = backing.width
            val texH = backing.height
            val sectionX = texW / 3
            val sectionY = texH / 3
            val screenW = camera.viewportWidth
            val screenH = camera.viewportHeight
            val x = screenW * 0.1f
            val y = screenH * 0.75f
            val w = screenW * 0.8f
            val h = screenH / 5f
            // Corners
            batch.draw(backing, x, y, sectionX * 1f, sectionY * 1f, 0f, 1f, 1 / 3f, 2 / 3f)
            batch.draw(backing, x, y + h - sectionY, sectionX * 1f, sectionY * 1f, 0f, 2 / 3f, 1 / 3f, 1f)
            batch.draw(backing, x + w - sectionX, y, sectionX * 1f, sectionY * 1f, 2 / 3f, 1f, 1f, 2 / 3f)
            batch.draw(backing, x + w - sectionX, y + h - sectionY, sectionX * 1f, sectionY * 1f, 2 / 3f, 2 / 3f, 1f, 1f)

            // Sides
            batch.draw(backing, x, y + sectionY, sectionX * 1f, h - sectionY * 2, 0f, 2 / 3f, 1 / 3f, 1 / 3f)
            batch.draw(backing, x + w - sectionX, y + sectionY, sectionX * 1f, h - sectionY * 2, 2 / 3f, 2 / 3f, 1f, 1 / 3f)
            batch.draw(backing, x + sectionX, y, w - sectionX * 2, sectionY * 1f, 1 / 3f, 0f, 2 / 3f, 1 / 3f)
            batch.draw(backing, x + sectionX, y + h - sectionY, w - sectionX * 2, sectionY * 1f, 1 / 3f, 2 / 3f, 2 / 3f, 1f)

            // Centre
            batch.draw(backing, x + sectionX, y + sectionY, w - sectionX * 2, h - sectionY * 2, 1 / 3f, 1 / 3f, 2 / 3f, 2 / 3f)

            // Render text
            val textWidth = font.getTextWidth(textBox.text, w - sectionX * 2, false)
            val textHeight = font.getTextHeight(textBox.text)
            font.drawCompressed(batch, textBox.text, (x + w / 2f - textWidth / 2f).coerceAtLeast(x + sectionX), y + h / 2f + textHeight / 2,
                    w - sectionX * 2, Align.left)

            if (textBox.requiresInput) {
                if (textBox.secsBeforeCanInput <= 0f) {
                    val bordered = MathHelper.getSawtoothWave(1.25f) >= 0.25f && lastInputMap[InputType.A] != true
                    font.draw(batch, if (bordered) "\uE0A0" else "\uE0E0", x + w - sectionX * 0.75f, y + font.capHeight + sectionY * 0.35f, 0f, Align.center, false)
                }
            }
            font.scaleMul(1f / 0.5f)
            font.unscaleFont()
        }

        if (skillStarInput.isFinite()) {
            val texColoured = AssetRegistry.get<Texture>("tex_skill_star")
            val texGrey = AssetRegistry.get<Texture>("tex_skill_star_grey")

            val scale = Interpolation.exp10.apply(1f, 2f, (skillStarPulseAnimation).coerceAtMost(1f))
            val rotation = Interpolation.exp10Out.apply(0f, 360f, 1f - skillStarSpinAnimation)
            batch.draw(if (gotSkillStar) texColoured else texGrey, 1184f, 32f, 32f, 32f, 64f, 64f, scale, scale, rotation,
                    0, 0, texColoured.width, texColoured.height, false, false)
        }

        if (xMoreTimes > 0) {
            val font = BRManiaApp.instance.defaultBorderedFontLarge
            font.scaleFont(camera)
            font.scaleMul(0.65f)
            font.setColor(1f, 1f, 1f, 1f)
            font.drawCompressed(batch, Localization["practice.moreTimes", xMoreTimes], 64f, 64f + font.capHeight, camera.viewportWidth - 128f, Align.right)
            font.scaleMul(1f / 0.65f)
            font.unscaleFont()
        }
        val songInfoEvent = events.firstOrNull { it is SongInfoEvent && beat in it.bounds.x..it.bounds.maxX } as SongInfoEvent?
        if (songInfoEvent != null) {
            val font = if (songInfoEvent.staticMode) BRManiaApp.instance.defaultBorderedFont else BRManiaApp.instance.defaultFont
            font.scaleFont(camera)
            val texture = AssetRegistry.get<Texture>("ui_song_title_artist")
            val scale = 1.15f
            val texWidth = texture.width.toFloat() * scale * camera.zoom
            val texHeight = texture.height.toFloat() * scale * camera.zoom

            if (!songInfoEvent.staticMode) {
                val xPercent: Float = songInfoEvent.getProgress()
                fun renderBar(timedString: String, bottom: Boolean) {
                    val x = if (!bottom) texWidth * xPercent - texWidth else camera.viewportWidth * camera.zoom - xPercent * texWidth
                    val y = (camera.viewportHeight * 0.125f * camera.zoom) - if (bottom) texHeight * 1.1f else 0f

                    batch.draw(texture, x, y, texWidth, texHeight,
                            0, 0, texture.width, texture.height,
                            bottom, bottom)
                    font.setColor(1f, 1f, 1f, 1f)
                    val padding = 8f * camera.zoom
                    val textWidth = (texture.width.toFloat() - texture.height) * scale * camera.zoom - padding * 2
                    val triangleWidth = texture.height.toFloat() * scale * camera.zoom
                    font.drawCompressed(batch, timedString,
                            (if (!bottom) x else x + triangleWidth) + padding,
                            y + font.capHeight + texHeight / 2 - font.capHeight / 2,
                            textWidth,
                            if (bottom) Align.left else Align.right)
                }

                if (songInfoEvent.songTitle.isNotEmpty())
                    renderBar(songInfoEvent.songTitle, false)
                if (songInfoEvent.songArtist.isNotEmpty())
                    renderBar(songInfoEvent.songArtist, true)
            } else {
                font.setColor(1f, 1f, 1f, songInfoEvent.getProgress())
                font.drawCompressed(batch, songInfoEvent.concatenated, 4f, camera.viewportHeight - 4f, camera.viewportWidth - 8f, Align.right)
                font.setColor(1f, 1f, 1f, 1f)
            }

            font.unscaleFont()
        }
        if (clearText > 0f) {
            clearText -= Gdx.graphics.deltaTime / 1.5f
            if (clearText < 0f)
                clearText = 0f

            val normalScale = 0.65f
            val transitionEnd = 0.15f
            val transitionStart = 0.2f
            val scale: Float = when (val progress = 1f - clearText) {
                in 0f..transitionStart -> {
                    Interpolation.exp10Out.apply(normalScale * 2f, normalScale, progress / transitionStart)
                }
                in (1f - transitionEnd)..1f -> {
                    Interpolation.exp10Out.apply(normalScale, normalScale * 1.5f, (progress - (1f - transitionEnd)) / transitionEnd)
                }
                else -> normalScale
            }
            val alpha: Float = when (val progress = 1f - clearText) {
                in 0f..transitionStart -> {
                    Interpolation.exp10Out.apply(0f, 1f, progress / transitionStart)
                }
                in (1f - transitionEnd)..1f -> {
                    Interpolation.exp10Out.apply(1f, 0f, (progress - (1f - transitionEnd)) / transitionEnd)
                }
                else -> 1f
            }
            val white: Float = when (val progress = 1f - clearText) {
                in 0f..transitionStart * 0.75f -> {
                    Interpolation.linear.apply(1f, 0f, progress / (transitionStart * 0.75f))
                }
                else -> 0f
            }

            val font = BRManiaApp.instance.kurokaneBorderedFont
            font.scaleFont(camera)
            font.scaleMul(scale)
            font.setColor(1f, 1f, MathUtils.lerp(0.125f, 1f, white), alpha)
            font.drawCompressed(batch, Localization["practice.clear"], 0f, camera.viewportHeight / 2f + font.capHeight / 2, camera.viewportWidth, Align.center)
            font.scaleMul(1f / scale)
            font.unscaleFont()
        }

        batch.end()
        batch.projectionMatrix = TMP_MATRIX
    }

    fun getBouncerForInput(inputType: InputType): Bouncer {
        return when (inputType) {
            InputType.A -> yellowBouncer
            InputType.DPAD -> redBouncer
        }
    }

    private val lastInputMap: MutableMap<InputType, Boolean> = mutableMapOf()

    fun fireInput(inputType: InputType, down: Boolean) {
        lastInputMap[inputType] = down
        val textBox = this.currentTextBox
        if (textBox != null && inputType == InputType.A) {
            if (textBox.requiresInput && textBox.secsBeforeCanInput <= 0f) {
                if (down) {
                    AssetRegistry.get<Sound>("sfx_text_advance_1").play()
                } else {
                    AssetRegistry.get<Sound>("sfx_text_advance_2").play()
                    this.currentTextBox = null
                    playState = PlayState.PLAYING
                }
            }
        } else if (playState == PlayState.PLAYING && down) {
            val bouncer = getBouncerForInput(inputType)
            val any = entities.filterIsInstance<Ball>().fold(false) { acc, it ->
                it.onInput(inputType) || acc
            }
            bouncer.bounceAnimation()
            if (!any) {
                // play dud sound
                AssetRegistry.get<BeadsSound>("sfx_dud_${if (inputType == InputType.A) "right" else "left"}").play(volume = 0.75f)
            }
        }
    }

    fun fireSkillStar() {
        if (gotSkillStar || skillStarInput.isInfinite())
            return
        gotSkillStar = true
        skillStarSpinAnimation = 1f
        skillStarPulseAnimation = 2f
        AssetRegistry.get<BeadsSound>("sfx_skill_star").play()
    }

    fun resetInputs() {
        inputResults as MutableList
        inputResults.clear()
        expectedNumInputs = 0
        lastInputMap.clear()
        gotSkillStar = false
        skillStarSpinAnimation = 0f
        skillStarPulseAnimation = 0f
    }

    fun wouldEventsFitNewTrackCount(newCount: Int): Boolean {
        if (newCount < 1) {
            return false
        }

        return events.filterNot { it is EndEvent }.firstOrNull { (it.bounds.y + it.bounds.height).roundToInt() >= trackCount } == null
    }

    fun canIncreaseTrackCount(): Boolean = trackCount < Engine.MAX_TRACK_COUNT
    fun canDecreaseTrackCount(): Boolean = trackCount > Engine.MIN_TRACK_COUNT

    fun getDebugString(): String {
        return "beat: $beat\nseconds: $seconds\ntempo: ${tempos.tempoAtSeconds(seconds)}\nevents: ${events.size}\nplayState: $playState"
    }

    override fun dispose() {
        listeners.clear()
        music?.dispose()
        BeadsSoundSystem.stop() // Ensures that the GC can collect the music data
        val texs = textures.values.toList()
        textures as MutableMap
        textures.clear()
        texs.filter { it !== AssetRegistry.missingTexture }.forEach { it.dispose() }
    }

}