package io.github.chrislo27.bouncyroadmania.engine

import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.Matrix4
import io.github.chrislo27.bouncyroadmania.engine.clock.Clock
import io.github.chrislo27.bouncyroadmania.renderer.PaperProjection
import io.github.chrislo27.toolboks.registry.AssetRegistry


class Engine(val clock: Clock) {

    companion object {
        private val TMP_MATRIX = Matrix4()
    }

    val camera: OrthographicCamera = OrthographicCamera().apply {
        setToOrtho(false, 1280f, 720f)
    }
    val entities: MutableList<Entity> = mutableListOf()
    val projector = PaperProjection(2f)
    var bouncers: List<Bouncer> = listOf()

    fun addBouncers() {
        entities.removeAll(bouncers)
        bouncers = listOf()

        val radius = 1200f
        val bouncers = mutableListOf<Bouncer>()
        // midpoint index = 8
        for (i in -1..15) {
            val angle = (180.0 * (i / 14.0)) - 90
            val bouncer: Bouncer = if (i == 13) {
                RedBouncer(this)
            } else if (i == 12) {
                YellowBouncer(this)
            } else {
                Bouncer(this)
            }

            if (i == -1 || i == 15) {
                bouncer.isSilent = true
            } else if (i == 14) {
                bouncer.soundHandle = "sfx_cymbal"
            }

            val sin = Math.sin(Math.toRadians(angle)).toFloat()
            val z: Float = (-sin + 1) * 0.3f

            bouncer.posY = Interpolation.sineIn.apply(640f, 150f, i / 14f)

            bouncer.posX = radius * if (i <= 8) Interpolation.sineOut.apply(i / 8f) else Interpolation.sineOut.apply(1f - ((i - 8) / 6f))
            bouncer.posZ = z

            entities += bouncer
            bouncers += bouncer
        }
        this.bouncers = bouncers
    }

    fun renderUpdate(delta: Float) {
        entities.forEach {
            it.renderUpdate(delta)
        }
        entities.removeIf { it.kill }
    }

    fun render(batch: SpriteBatch) {
        camera.update()
        TMP_MATRIX.set(batch.projectionMatrix)
        batch.projectionMatrix = camera.combined
        batch.begin()
        batch.draw(AssetRegistry.get<Texture>("tex_gradient"), 0f, 0f, camera.viewportWidth, camera.viewportHeight)
        projector.render(batch, entities)
        batch.end()
        batch.projectionMatrix = TMP_MATRIX
    }

}