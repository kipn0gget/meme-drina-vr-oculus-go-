package com.example.gl

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.opengl.GLES30
import android.opengl.GLUtils
import kotlin.random.Random

data class TextureSet(
    val wallHouse: Int,
    val wallYard: Int,
    val wallHotel: Int,
    val floorHouse: Int,
    val floorYard: Int,
    val floorHotel: Int,
    val ceilingIndoors: Int,
    val ceilingSky: Int,
    val door: Int,
    val doorLocked: Int,
    val prop: Int,
    val book: Int,
    val key: Int,
    val slendrina: Int,
    val exitPortal: Int
) {
    fun allIds(): IntArray = intArrayOf(
        wallHouse, wallYard, wallHotel,
        floorHouse, floorYard, floorHotel,
        ceilingIndoors, ceilingSky,
        door, doorLocked, prop,
        book, key, slendrina, exitPortal
    )
}

object ProceduralTextures {

    const val SLENDRINA_ASSET_PATH = "assets/aistudio/Gemini_Generated_Image_vipyruvipyruvipy_de71a99a.jpeg"

    fun loadSlendrinaPortraitBitmap(context: Context?): Bitmap? {
        if (context == null) return null
        return try {
            context.assets.open(SLENDRINA_ASSET_PATH).use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        } catch (_: Throwable) {
            null
        }
    }

    fun createAll(context: Context? = null): TextureSet {
        return TextureSet(
            wallHouse = uploadBitmap(makeWallpaper(base = Color.rgb(68, 56, 48), stripe = Color.rgb(48, 38, 33), wainscot = Color.rgb(34, 22, 16)), repeat = true),
            wallYard = uploadBitmap(makeStoneWall(), repeat = true),
            wallHotel = uploadBitmap(makeWallpaper(base = Color.rgb(74, 34, 38), stripe = Color.rgb(52, 22, 26), wainscot = Color.rgb(30, 18, 16)), repeat = true),
            floorHouse = uploadBitmap(makeWoodPlanks(Color.rgb(58, 38, 25), Color.rgb(34, 20, 13)), repeat = true),
            floorYard = uploadBitmap(makeGroundYard(), repeat = true),
            floorHotel = uploadBitmap(makeHotelCarpet(), repeat = true),
            ceilingIndoors = uploadBitmap(makePlasterCeiling(), repeat = true),
            ceilingSky = uploadBitmap(makeNightSky(), repeat = true),
            door = uploadBitmap(makeDoor(locked = false), repeat = false),
            doorLocked = uploadBitmap(makeDoor(locked = true), repeat = false),
            prop = uploadBitmap(makePropStone(), repeat = true),
            book = uploadBitmap(makeOccultBook(), repeat = false),
            key = uploadBitmap(makeBrassKey(), repeat = false),
            slendrina = uploadBitmap(makeSlendrinaSprite(context), repeat = false),
            exitPortal = uploadBitmap(makeExitSeal(), repeat = false)
        )
    }

    private fun uploadBitmap(bmp: Bitmap, repeat: Boolean): Int {
        val texIds = IntArray(1)
        GLES30.glGenTextures(1, texIds, 0)
        val id = texIds[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, id)
        val wrap = if (repeat) GLES30.GL_REPEAT else GLES30.GL_CLAMP_TO_EDGE
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, wrap)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, wrap)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bmp, 0)
        bmp.recycle()
        return id
    }

    private fun makeWallpaper(base: Int, stripe: Int, wainscot: Int): Bitmap {
        val size = 256
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(base)
        val p = Paint()
        p.color = stripe
        for (x in 0 until size step 32) {
            c.drawRect(x.toFloat(), 0f, (x + 12).toFloat(), size.toFloat(), p)
        }
        // Grime & peeling stains
        val rng = Random(base)
        for (i in 0 until 90) {
            p.color = Color.argb(rng.nextInt(25, 70), 12, 10, 10)
            c.drawCircle(rng.nextFloat() * size, rng.nextFloat() * size, rng.nextFloat() * 18f + 4f, p)
        }
        // Wood wainscoting on lower third
        p.color = wainscot
        c.drawRect(0f, size * 0.68f, size.toFloat(), size.toFloat(), p)
        p.color = Color.rgb(22, 14, 10)
        p.strokeWidth = 4f
        c.drawLine(0f, size * 0.68f, size.toFloat(), size * 0.68f, p)
        for (x in 0 until size step 64) {
            c.drawRect(x + 8f, size * 0.73f, x + 56f, size * 0.94f, Paint().apply {
                style = Paint.Style.STROKE
                strokeWidth = 3f
                color = Color.rgb(65, 44, 30)
            })
        }
        return bmp
    }

    private fun makeStoneWall(): Bitmap {
        val size = 256
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.rgb(42, 46, 52))
        val p = Paint()
        val rng = Random(99)
        for (row in 0 until 8) {
            val y = row * 32f
            val offset = if (row % 2 == 0) 0f else 32f
            for (col in -1 until 5) {
                val x = col * 64f + offset
                val shade = rng.nextInt(48, 78)
                p.color = Color.rgb(shade, shade + 4, shade + 8)
                c.drawRect(x + 3f, y + 3f, x + 61f, y + 29f, p)
            }
        }
        // Moss patches
        for (i in 0 until 30) {
            p.color = Color.argb(65, 32, 66, 40)
            c.drawCircle(rng.nextFloat() * size, rng.nextFloat() * size, rng.nextFloat() * 14f + 6f, p)
        }
        return bmp
    }

    private fun makeWoodPlanks(light: Int, dark: Int): Bitmap {
        val size = 256
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(light)
        val p = Paint()
        val rng = Random(17)
        for (row in 0 until 8) {
            val y = row * 32f
            p.color = Color.argb(rng.nextInt(25, 70), 20, 12, 8)
            c.drawRect(0f, y, size.toFloat(), y + 32f, p)
            p.color = dark
            p.strokeWidth = 3f
            c.drawLine(0f, y, size.toFloat(), y, p)
            val jointX = ((row * 97) % 200) + 28f
            c.drawLine(jointX, y, jointX, y + 32f, p)
        }
        return bmp
    }

    private fun makeGroundYard(): Bitmap {
        val size = 256
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.rgb(26, 33, 28))
        val p = Paint()
        val rng = Random(303)
        for (i in 0 until 320) {
            val g = rng.nextInt(25, 58)
            p.color = Color.argb(140, g - 8, g + 4, g - 10)
            c.drawCircle(rng.nextFloat() * size, rng.nextFloat() * size, rng.nextFloat() * 8f + 2f, p)
        }
        return bmp
    }

    private fun makeHotelCarpet(): Bitmap {
        val size = 256
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.rgb(66, 18, 22))
        val p = Paint().apply {
            color = Color.rgb(96, 68, 34)
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        for (y in 0 until size step 64) {
            for (x in 0 until size step 64) {
                val path = Path().apply {
                    moveTo(x + 32f, y + 8f)
                    lineTo(x + 56f, y + 32f)
                    lineTo(x + 32f, y + 56f)
                    lineTo(x + 8f, y + 32f)
                    close()
                }
                c.drawPath(path, p)
            }
        }
        val rng = Random(515)
        val stain = Paint()
        for (i in 0 until 45) {
            stain.color = Color.argb(55, 16, 8, 8)
            c.drawCircle(rng.nextFloat() * size, rng.nextFloat() * size, rng.nextFloat() * 20f + 5f, stain)
        }
        return bmp
    }

    private fun makePlasterCeiling(): Bitmap {
        val size = 256
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.rgb(34, 30, 32))
        val rng = Random(77)
        val p = Paint()
        for (i in 0 until 60) {
            p.color = Color.argb(50, 15, 13, 15)
            c.drawCircle(rng.nextFloat() * size, rng.nextFloat() * size, rng.nextFloat() * 24f + 6f, p)
        }
        return bmp
    }

    private fun makeNightSky(): Bitmap {
        val size = 256
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.rgb(10, 15, 28))
        val rng = Random(888)
        val p = Paint()
        for (i in 0 until 65) {
            val b = rng.nextInt(140, 240)
            p.color = Color.rgb(b, b, 255)
            c.drawCircle(rng.nextFloat() * size, rng.nextFloat() * size, rng.nextFloat() * 1.8f + 0.6f, p)
        }
        return bmp
    }

    private fun makeDoor(locked: Boolean): Bitmap {
        val size = 256
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(if (locked) Color.rgb(66, 22, 22) else Color.rgb(74, 46, 28))
        val frame = Paint().apply {
            color = Color.rgb(36, 20, 12)
            style = Paint.Style.STROKE
            strokeWidth = 10f
        }
        c.drawRect(10f, 10f, 246f, 246f, frame)
        frame.strokeWidth = 5f
        c.drawRect(34f, 28f, 114f, 118f, frame)
        c.drawRect(142f, 28f, 222f, 118f, frame)
        c.drawRect(34f, 140f, 114f, 226f, frame)
        c.drawRect(142f, 140f, 222f, 226f, frame)

        // Brass doorknob
        val brass = Paint().apply { color = Color.rgb(214, 172, 82) }
        c.drawCircle(216f, 134f, 11f, brass)

        if (locked) {
            val lockPaint = Paint().apply {
                color = Color.rgb(228, 54, 66)
                style = Paint.Style.FILL
            }
            c.drawRect(104f, 112f, 152f, 156f, lockPaint)
            val shackle = Paint().apply {
                color = Color.rgb(235, 190, 95)
                style = Paint.Style.STROKE
                strokeWidth = 8f
            }
            c.drawCircle(128f, 108f, 16f, shackle)
        }
        return bmp
    }

    private fun makePropStone(): Bitmap {
        val size = 128
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.rgb(52, 48, 46))
        val p = Paint().apply {
            color = Color.rgb(36, 32, 30)
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        c.drawRect(4f, 4f, 124f, 124f, p)
        c.drawLine(4f, 42f, 124f, 42f, p)
        c.drawLine(4f, 84f, 124f, 84f, p)
        return bmp
    }

    private fun makeOccultBook(): Bitmap {
        val size = 256
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.TRANSPARENT)

        // Soft crimson aura
        val aura = Paint().apply {
            shader = RadialGradient(128f, 128f, 118f, Color.argb(170, 220, 38, 56), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        }
        c.drawCircle(128f, 128f, 118f, aura)

        // Book cover
        val cover = Paint().apply { color = Color.rgb(142, 20, 32) }
        c.drawRoundRect(56f, 42f, 200f, 214f, 14f, 14f, cover)

        // Spine & pages edge
        val pages = Paint().apply { color = Color.rgb(226, 210, 180) }
        c.drawRect(184f, 52f, 200f, 204f, pages)

        val trim = Paint().apply {
            color = Color.rgb(224, 178, 86)
            style = Paint.Style.STROKE
            strokeWidth = 6f
        }
        c.drawRoundRect(68f, 54f, 182f, 202f, 8f, 8f, trim)
        // Occult diamond emblem
        c.drawCircle(125f, 128f, 26f, trim)
        c.drawLine(125f, 92f, 125f, 164f, trim)
        c.drawLine(92f, 128f, 158f, 128f, trim)
        return bmp
    }

    private fun makeBrassKey(): Bitmap {
        val size = 256
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.TRANSPARENT)

        val aura = Paint().apply {
            shader = RadialGradient(128f, 128f, 112f, Color.argb(150, 232, 185, 78), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        }
        c.drawCircle(128f, 128f, 112f, aura)

        val brass = Paint().apply {
            color = Color.rgb(235, 188, 82)
            style = Paint.Style.STROKE
            strokeWidth = 14f
            strokeCap = Paint.Cap.ROUND
        }
        c.drawCircle(128f, 74f, 30f, brass)
        c.drawLine(128f, 104f, 128f, 206f, brass)
        c.drawLine(128f, 168f, 162f, 168f, brass)
        c.drawLine(128f, 194f, 156f, 194f, brass)
        return bmp
    }

    internal fun makeSlendrinaSprite(context: Context? = null): Bitmap {
        val w = 384
        val h = 640
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.TRANSPARENT)

        // Warm demonic candlelight & crimson aura around the nun chihuahua
        val aura = Paint().apply {
            shader = RadialGradient(
                w * 0.5f, h * 0.34f, w * 0.65f,
                intArrayOf(
                    Color.argb(175, 195, 32, 28),
                    Color.argb(95, 110, 22, 18),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), aura)

        // Flowing tattered black nun habit / cassock from shoulders down to jagged hem
        val habitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                w * 0.5f, h * 0.30f, w * 0.5f, h * 0.98f,
                intArrayOf(
                    Color.rgb(18, 16, 20),
                    Color.rgb(14, 12, 16),
                    Color.argb(30, 10, 8, 12)
                ),
                floatArrayOf(0f, 0.68f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        val habitPath = Path().apply {
            moveTo(w * 0.20f, h * 0.32f)
            lineTo(w * 0.80f, h * 0.32f)
            lineTo(w * 0.92f, h * 0.92f)
            lineTo(w * 0.78f, h * 0.97f)
            lineTo(w * 0.66f, h * 0.89f)
            lineTo(w * 0.54f, h * 0.98f)
            lineTo(w * 0.42f, h * 0.90f)
            lineTo(w * 0.28f, h * 0.98f)
            lineTo(w * 0.16f, h * 0.91f)
            lineTo(w * 0.08f, h * 0.95f)
            close()
        }
        c.drawPath(habitPath, habitPaint)

        // Subtle tattered habit folds
        val foldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(85, 42, 36, 40)
            strokeWidth = 4f
            style = Paint.Style.STROKE
        }
        c.drawLine(w * 0.34f, h * 0.44f, w * 0.26f, h * 0.88f, foldPaint)
        c.drawLine(w * 0.66f, h * 0.44f, w * 0.74f, h * 0.88f, foldPaint)

        // Attempt to composite the uploaded Chihuahua-nun artwork cleanly onto the upper torso & head
        val sourcePortrait = loadSlendrinaPortraitBitmap(context)
        if (sourcePortrait != null) {
            val layerBounds = RectF(0f, 0f, w.toFloat(), h * 0.68f)
            val saved = c.saveLayer(layerBounds, null)
            val srcRect = Rect(
                (sourcePortrait.width * 0.16f).toInt(),
                (sourcePortrait.height * 0.02f).toInt(),
                (sourcePortrait.width * 0.84f).toInt(),
                (sourcePortrait.height * 0.98f).toInt()
            )
            val dstRect = RectF(w * 0.08f, h * 0.02f, w * 0.92f, h * 0.60f)
            c.drawBitmap(sourcePortrait, srcRect, dstRect, Paint(Paint.FILTER_BITMAP_FLAG))

            // Feathered elliptical alpha mask so the crypt corners blend seamlessly into the black habit
            val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
                shader = RadialGradient(
                    w * 0.5f, h * 0.31f, w * 0.44f,
                    intArrayOf(Color.BLACK, Color.BLACK, Color.TRANSPARENT),
                    floatArrayOf(0f, 0.62f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            c.drawRect(layerBounds, maskPaint)
            c.restoreToCount(saved)
            sourcePortrait.recycle()
        } else {
            drawProceduralChihuahuaNunHead(c, w, h)
        }

        // Weathered wooden cross pendant resting on the black habit below the white collar
        val cordPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(72, 52, 36)
            strokeWidth = 4f
        }
        c.drawLine(w * 0.38f, h * 0.45f, w * 0.50f, h * 0.55f, cordPaint)
        c.drawLine(w * 0.62f, h * 0.45f, w * 0.50f, h * 0.55f, cordPaint)

        val crossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(112, 86, 56)
            strokeWidth = 10f
            strokeCap = Paint.Cap.SQUARE
        }
        c.drawLine(w * 0.50f, h * 0.54f, w * 0.50f, h * 0.67f, crossPaint)
        c.drawLine(w * 0.44f, h * 0.585f, w * 0.56f, h * 0.585f, crossPaint)

        return bmp
    }

    private fun drawProceduralChihuahuaNunHead(c: Canvas, w: Int, h: Int) {
        val furDark = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(92, 62, 40) }
        val furTan = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(176, 138, 96) }
        val wimple = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(214, 206, 194) }
        val veil = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(16, 14, 18) }

        // Large pointed Chihuahua ears protruding out of the nun hood
        val leftEar = Path().apply {
            moveTo(w * 0.32f, h * 0.14f)
            lineTo(w * 0.12f, h * 0.03f)
            lineTo(w * 0.24f, h * 0.24f)
            close()
        }
        val rightEar = Path().apply {
            moveTo(w * 0.68f, h * 0.14f)
            lineTo(w * 0.88f, h * 0.03f)
            lineTo(w * 0.76f, h * 0.24f)
            close()
        }
        c.drawPath(leftEar, furDark)
        c.drawPath(rightEar, furDark)

        // Black nun veil & white wimple collar
        c.drawOval(w * 0.20f, h * 0.09f, w * 0.80f, h * 0.45f, veil)
        c.drawOval(w * 0.27f, h * 0.12f, w * 0.73f, h * 0.46f, wimple)
        c.drawOval(w * 0.31f, h * 0.16f, w * 0.69f, h * 0.40f, furTan)

        // Glowing demonic red eyes
        val redEye = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(232, 18, 28) }
        val pupil = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(18, 4, 6) }
        c.drawCircle(w * 0.41f, h * 0.25f, 16f, redEye)
        c.drawCircle(w * 0.59f, h * 0.25f, 16f, redEye)
        c.drawCircle(w * 0.41f, h * 0.25f, 6f, pupil)
        c.drawCircle(w * 0.59f, h * 0.25f, 6f, pupil)

        // Dark nose + snarling mouth with fangs
        c.drawOval(w * 0.45f, h * 0.28f, w * 0.55f, h * 0.315f, pupil)
        c.drawOval(w * 0.38f, h * 0.32f, w * 0.62f, h * 0.39f, pupil)
        val fang = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(235, 224, 198) }
        c.drawLine(w * 0.42f, h * 0.33f, w * 0.43f, h * 0.365f, fang.apply { strokeWidth = 5f })
        c.drawLine(w * 0.58f, h * 0.33f, w * 0.57f, h * 0.365f, fang)
    }

    private fun makeExitSeal(): Bitmap {
        val size = 256
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.TRANSPARENT)

        val aura = Paint().apply {
            shader = RadialGradient(128f, 128f, 120f, Color.argb(210, 86, 215, 190), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        }
        c.drawCircle(128f, 128f, 120f, aura)

        val ring = Paint().apply {
            color = Color.rgb(220, 255, 240)
            style = Paint.Style.STROKE
            strokeWidth = 8f
        }
        c.drawCircle(128f, 128f, 86f, ring)
        c.drawCircle(128f, 128f, 54f, ring)
        return bmp
    }
}
