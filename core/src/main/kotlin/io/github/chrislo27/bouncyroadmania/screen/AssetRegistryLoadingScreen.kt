package io.github.chrislo27.bouncyroadmania.screen

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.math.Interpolation
import io.github.chrislo27.bouncyroadmania.BRMania
import io.github.chrislo27.bouncyroadmania.BRManiaApp
import io.github.chrislo27.bouncyroadmania.PreferenceKeys
import io.github.chrislo27.bouncyroadmania.util.scaleFont
import io.github.chrislo27.bouncyroadmania.util.transition.WipeFrom
import io.github.chrislo27.bouncyroadmania.util.transition.WipeTo
import io.github.chrislo27.bouncyroadmania.util.unscaleFont
import io.github.chrislo27.toolboks.ToolboksScreen
import io.github.chrislo27.toolboks.registry.AssetRegistry
import io.github.chrislo27.toolboks.transition.TransitionScreen
import io.github.chrislo27.toolboks.util.gdxutils.drawRect
import io.github.chrislo27.toolboks.util.gdxutils.fillRect
import io.github.chrislo27.toolboks.util.gdxutils.getTextWidth
import io.github.chrislo27.toolboks.util.gdxutils.scaleMul


class AssetRegistryLoadingScreen(main: BRManiaApp)
    : ToolboksScreen<BRManiaApp, AssetRegistryLoadingScreen>(main) {

    companion object {
        private val INTRO_LENGTH = 0.985f
    }

    private val camera: OrthographicCamera = OrthographicCamera().apply {
        setToOrtho(false, BRMania.WIDTH * 1f, BRMania.HEIGHT * 1f)
    }
    //    private var nextScreen: (() -> ToolboksScreen<*, *>?)? = null
    private var finishCallback: () -> Unit = {}

    private var finishedLoading = false
    private var stingPlayed = false
    private var transition: Float = 0f

    private var lastProgress = 0f

    private lateinit var mainMenuScreen: MainMenuScreen
    private lateinit var transitionScreen: TransitionScreen<BRManiaApp>
    private var lastTitleX: Float = -1f
    private var lastTitleY: Float = -1f
    private val titleWiggle: FloatArray = FloatArray(3) { 0f }

    init {
        titleWiggle[0] = 1f + 0.297f * 8f
        titleWiggle[1] = 1f + 0.569f * 8f
        titleWiggle[2] = 1f + 0.687f * 8f
    }

    override fun render(delta: Float) {
        super.render(delta)
        lastProgress = AssetRegistry.load(delta)

        val cam = camera
        val batch = main.batch
        batch.projectionMatrix = cam.combined
        val width = cam.viewportWidth * 0.75f
        val height = cam.viewportHeight * 0.05f
        val line = height / 8f

        batch.begin()

        batch.setColor(1f, 1f, 1f, 1f)

        val progress = lastProgress
        batch.fillRect(cam.viewportWidth * 0.5f - width * 0.5f,
                cam.viewportHeight * 0.3f - (height) * 0.5f,
                width * progress, height)
        batch.drawRect(cam.viewportWidth * 0.5f - width * 0.5f - line * 2,
                cam.viewportHeight * 0.3f - (height) * 0.5f - line * 2,
                width + (line * 4), height + (line * 4),
                line)

        batch.setColor(1f, 1f, 1f, 1f)

        // title
        if (main.screen === this) {
            val titleFont = main.cometBorderedFont
            titleFont.scaleFont(camera)
            titleFont.scaleMul(0.6f)
            val textW = titleFont.getTextWidth(BRMania.TITLE)
            if (lastTitleX == -1f) {
                lastTitleX = camera.viewportWidth / 2f - textW / 2
            }
            if (lastTitleY == -1f) {
                lastTitleY = camera.viewportHeight / 2f + titleFont.capHeight / 2f
            }
            var titleX = lastTitleX
            MainMenuScreen.TITLE.forEachIndexed { i, s ->
                val titleY = lastTitleY + titleFont.capHeight * if (titleWiggle[i] > 1) 0f else titleWiggle[i]
                titleX += titleFont.draw(batch, s, titleX, titleY).width
            }
            titleFont.unscaleFont()
        }

        batch.end()
        batch.projectionMatrix = main.defaultCamera.combined
    }

    override fun renderUpdate() {
        super.renderUpdate()

        if (lastProgress >= 1f) {
            if (!finishedLoading) {
                finishedLoading = true
                finishCallback()
                mainMenuScreen = MainMenuScreen(main, playMusic = false).apply {
                    this.hideTitle = true
                }
                transitionScreen = object : TransitionScreen<BRManiaApp>(main, this@AssetRegistryLoadingScreen, mainMenuScreen, WipeTo(Color.BLACK, 0.25f), WipeFrom(Color.BLACK, 0.25f)) {
                    override fun render(delta: Float) {
                        super.render(delta)

                        val batch = main.batch
                        batch.projectionMatrix = camera.combined
                        batch.begin()
                        val titleFont = main.cometBorderedFont
                        titleFont.scaleFont(camera)
                        titleFont.scaleMul(0.6f)
                        val titleX = Interpolation.smooth.apply(lastTitleX, mainMenuScreen.titleXStart, percentageTotal)
                        val titleY = Interpolation.smooth.apply(lastTitleY, mainMenuScreen.menuTop + titleFont.lineHeight, percentageTotal)
                        titleFont.draw(batch, BRMania.TITLE, titleX, titleY)
                        titleFont.unscaleFont()
                        batch.end()
                        batch.projectionMatrix = main.defaultCamera.combined
                    }
                }
            } else {
                if (!stingPlayed) {
                    stingPlayed = true
                    AssetRegistry.get<Sound>("sfx_main_menu_intro").play(if (main.preferences.getBoolean(PreferenceKeys.MUTE_MUSIC, false)) 0f else 1f)
                } else {
                    transition += Gdx.graphics.deltaTime
                    for (i in 0 until titleWiggle.size) {
                        if (titleWiggle[i] != 0f) {
                            val sign = Math.signum(titleWiggle[i])
                            titleWiggle[i] -= sign * Gdx.graphics.deltaTime * 8f
                            if (Math.signum(titleWiggle[i]) != sign && titleWiggle[i] != 0f) {
                                titleWiggle[i] = 0f
                            }
                        }
                    }
                    if (transition >= INTRO_LENGTH) {
                        mainMenuScreen.music.play()
                        main.screen = transitionScreen
                    }
                }
            }
        }
    }

//    fun setNextScreen(next: (() -> ToolboksScreen<*, *>?)?): AssetRegistryLoadingScreen {
//        nextScreen = next
//        return this
//    }

    fun onFinishLoading(next: () -> Unit): AssetRegistryLoadingScreen {
        finishCallback = next
        return this
    }

    override fun tickUpdate() {
    }

    override fun dispose() {
    }
}