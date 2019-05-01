package io.github.chrislo27.bouncyroadmania.screen

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.audio.Music
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.utils.Align
import io.github.chrislo27.bouncyroadmania.BRManiaApp
import io.github.chrislo27.bouncyroadmania.engine.Ball
import io.github.chrislo27.bouncyroadmania.engine.Engine
import io.github.chrislo27.bouncyroadmania.engine.clock.Clock
import io.github.chrislo27.bouncyroadmania.engine.clock.Swing
import io.github.chrislo27.bouncyroadmania.engine.clock.tempo.TempoChange
import io.github.chrislo27.bouncyroadmania.engine.input.InputType
import io.github.chrislo27.toolboks.ToolboksScreen
import io.github.chrislo27.toolboks.registry.AssetRegistry
import io.github.chrislo27.toolboks.util.gdxutils.drawCompressed
import io.github.chrislo27.toolboks.util.gdxutils.isShiftDown
import io.github.chrislo27.toolboks.util.gdxutils.scaleMul


class TestEngineScreen(main: BRManiaApp) : ToolboksScreen<BRManiaApp, TestEngineScreen>(main) {

    val clock: Clock = Clock()
    val engine = Engine(clock)

    var sendBallCycle = 0

    init {
        reload()
    }

    fun reload() {
        engine.entities.clear()
        clock.seconds = 0f
        clock.tempos.clear()
        clock.tempos.add(TempoChange(clock.tempos, 0f, 154f, Swing.STRAIGHT, 0f))
        sendBallCycle = 0

        engine.addBouncers()
        AssetRegistry.get<Music>("music_br").stop()
    }

    override fun render(delta: Float) {
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        val batch = main.batch

        batch.begin()
        batch.draw(AssetRegistry.get<Texture>("tex_gradient"), 0f, 0f, Gdx.graphics.width * 1f, Gdx.graphics.height * 1f)
        batch.end()

        engine.render(batch)

        batch.begin()
        val comet = main.cometBorderedFont
        comet.scaleMul(0.5f)
        comet.drawCompressed(batch, "Bouncy Road Mania", engine.camera.viewportWidth - 1280f / 2, comet.lineHeight * 1.75f, 600f, Align.right)
        comet.drawCompressed(batch, "ホッピングロードマニア", engine.camera.viewportWidth - 1280f / 2, comet.lineHeight, 600f, Align.right)
        comet.scaleMul(1f / 0.5f)
        batch.end()
    }

    override fun renderUpdate() {
        super.renderUpdate()

        val delta = Gdx.graphics.deltaTime
        clock.update(delta)

        val ballCycle = 1f
        if (clock.beat > ballCycle * sendBallCycle) {
            sendBallCycle++
        }

        engine.renderUpdate(delta)

        if (Gdx.input.isKeyJustPressed(Input.Keys.R)) {
            reload()
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.E)) {
            engine.entities += Ball(engine, if (Gdx.input.isShiftDown()) 1f else 0.5f, engine.clock.beat).apply {
                val first = engine.bouncers.first()
                posX = first.posX
                posY = first.posY
                bounce(engine.bouncers[0], engine.bouncers[1], false)
            }
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.M)) {
            reload()
            engine.clock.seconds = -0.87f
            engine.clock.paused = true
            AssetRegistry.get<Music>("music_br").play()
            Gdx.app.postRunnable {
                Gdx.app.postRunnable {
                    engine.clock.paused = false
                }
            }

            engine.entities += Ball(engine, 0.5f, 4f - 0.5f).apply {
                val first = engine.bouncers.first()
                posX = first.posX
                posY = first.posY
                bounce(engine.bouncers[0], engine.bouncers[1], false)
            }
            engine.entities += Ball(engine, 0.5f, 12f - 0.5f).apply {
                val first = engine.bouncers.first()
                posX = first.posX
                posY = first.posY
                bounce(engine.bouncers[0], engine.bouncers[1], false)
            }
            engine.entities += Ball(engine, 0.5f, 20f - 0.5f).apply {
                val first = engine.bouncers.first()
                posX = first.posX
                posY = first.posY
                bounce(engine.bouncers[0], engine.bouncers[1], false)
            }
            engine.entities += Ball(engine, 0.5f, 28f - 0.5f).apply {
                val first = engine.bouncers.first()
                posX = first.posX
                posY = first.posY
                bounce(engine.bouncers[0], engine.bouncers[1], false)
            }
            engine.entities += Ball(engine, 0.66666f, 38f, true).apply {
                val first = engine.bouncers.first()
                posX = first.posX
                posY = first.posY
                bounce(engine.bouncers[0], engine.bouncers[1], false)
            }
            engine.entities += Ball(engine, 0.66666f, 54f, true).apply {
                val first = engine.bouncers.first()
                posX = first.posX
                posY = first.posY
                bounce(engine.bouncers[0], engine.bouncers[1], false)
            }
            engine.entities += Ball(engine, 1f, 68f - 1f).apply {
                val first = engine.bouncers.first()
                posX = first.posX
                posY = first.posY
                bounce(engine.bouncers[0], engine.bouncers[1], false)
            }
            engine.entities += Ball(engine, 0.5f, 70f - 0.5f).apply {
                val first = engine.bouncers.first()
                posX = first.posX
                posY = first.posY
                bounce(engine.bouncers[0], engine.bouncers[1], false)
            }
            engine.entities += Ball(engine, 0.5f, 83f - 0.5f).apply {
                val first = engine.bouncers.first()
                posX = first.posX
                posY = first.posY
                bounce(engine.bouncers[0], engine.bouncers[1], false)
            }
            engine.entities += Ball(engine, 0.5f, 84f - 0.5f).apply {
                val first = engine.bouncers.first()
                posX = first.posX
                posY = first.posY
                bounce(engine.bouncers[0], engine.bouncers[1], false)
            }
            engine.entities += Ball(engine, 0.5f, 91f - 0.5f).apply {
                val first = engine.bouncers.first()
                posX = first.posX
                posY = first.posY
                bounce(engine.bouncers[0], engine.bouncers[1], false)
            }
            engine.entities += Ball(engine, 0.5f, 92f - 0.5f).apply {
                val first = engine.bouncers.first()
                posX = first.posX
                posY = first.posY
                bounce(engine.bouncers[0], engine.bouncers[1], false)
            }
            engine.entities += Ball(engine, 0.5f, 98f - 0.5f).apply {
                val first = engine.bouncers.first()
                posX = first.posX
                posY = first.posY
                bounce(engine.bouncers[0], engine.bouncers[1], false)
            }
            engine.entities += Ball(engine, 0.5f, 99f - 0.5f).apply {
                val first = engine.bouncers.first()
                posX = first.posX
                posY = first.posY
                bounce(engine.bouncers[0], engine.bouncers[1], false)
            }
            engine.entities += Ball(engine, 0.5f, 100f - 0.5f).apply {
                val first = engine.bouncers.first()
                posX = first.posX
                posY = first.posY
                bounce(engine.bouncers[0], engine.bouncers[1], false)
            }
            engine.entities += Ball(engine, 0.5f, 108f - 0.5f).apply {
                val first = engine.bouncers.first()
                posX = first.posX
                posY = first.posY
                bounce(engine.bouncers[0], engine.bouncers[1], false)
            }
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.D)) {
            engine.fireInput(InputType.DPAD)
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.J)) {
            engine.fireInput(InputType.A)
        }
    }

    override fun getDebugString(): String? {
        return """beat: ${clock.beat}
            |seconds: ${clock.seconds}
            |bpm: ${clock.tempos.tempoAt(clock.beat)}
        """.trimMargin()
    }

    override fun tickUpdate() {
    }

    override fun dispose() {
    }

}