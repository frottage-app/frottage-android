package com.frottage

import android.content.Context
import android.graphics.Bitmap
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur

/**
 * Applies a Gaussian blur to a bitmap with support for radius > 25.0f.
 *
 * Due to RenderScript's limitation of max 25.0f radius per pass, this function applies multiple
 * passes to achieve stronger blur effects. The total blur radius compounds according to the
 * formula: total_radius = sqrt(r1² + r2² + ... + rn²) where r1...rn are the radii of individual
 * passes.
 *
 * For example, to achieve a 50.0f blur:
 * - First pass (25.0f): currentRadius = sqrt(0² + 25²) = 25.0f
 * - Second pass (25.0f): currentRadius = sqrt(25² + 25²) = 35.4f
 * - Third pass (25.0f): currentRadius = sqrt(35.4² + 25²) = 43.3f
 * - Fourth pass (25.0f): currentRadius = sqrt(43.3² + 25²) = 50.0f
 *
 * @param context Android context
 * @param bitmap Input bitmap to blur
 * @param targetRadius Desired blur radius (can be > 25.0f)
 * @return Blurred bitmap
 */
fun blurBitmap(
    context: Context,
    bitmap: Bitmap,
    targetRadius: Float,
): Bitmap {
    // Maximum radius allowed by RenderScript's ScriptIntrinsicBlur
    val maxPassRadius = 25.0f
    // Track the current effective radius as we compound multiple passes
    var currentRadius = 0.0f

    var currentBitmap = bitmap
    var outputBitmap: Bitmap? = null
    val rs = RenderScript.create(context)

    // Keep applying blur passes until we reach or exceed the target radius
    // Each pass compounds with previous passes according to: new_radius = sqrt(current² + pass²)
    while (currentRadius < targetRadius) {
        outputBitmap =
            Bitmap.createBitmap(currentBitmap.width, currentBitmap.height, currentBitmap.config ?: Bitmap.Config.ARGB_8888)
        val inputAllocation = Allocation.createFromBitmap(rs, currentBitmap)
        val outputAllocation = Allocation.createFromBitmap(rs, outputBitmap)
        val blurScript = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))

        blurScript.setRadius(maxPassRadius)
        blurScript.setInput(inputAllocation)
        blurScript.forEach(outputAllocation)
        outputAllocation.copyTo(outputBitmap)

        // Clean up RenderScript resources to prevent memory leaks
        inputAllocation.destroy()
        outputAllocation.destroy()
        blurScript.destroy()

        // Recycle intermediate bitmaps to free memory, but keep the original
        if (currentBitmap != bitmap) {
            currentBitmap.recycle()
        }
        currentBitmap = outputBitmap

        // Calculate the new effective radius using the compound effect formula
        // sqrt(current_radius² + pass_radius²)
        currentRadius =
            kotlin.math.sqrt(currentRadius * currentRadius + maxPassRadius * maxPassRadius)
    }

    rs.destroy()
    return outputBitmap ?: bitmap
}
