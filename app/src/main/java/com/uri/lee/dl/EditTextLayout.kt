package com.uri.lee.dl

import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.widget.EditText
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isInvisible
import androidx.core.widget.addTextChangedListener
import androidx.transition.TransitionManager
import com.uri.lee.dl.databinding.EditTextLayoutBinding

class EditTextLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    ConstraintLayout(context, attrs, defStyleAttr) {

    private val binding = EditTextLayoutBinding.inflate(layoutInflater, this)
    private var isTextBeingAssigned = false
    private lateinit var editText: EditText
    private lateinit var errorTextView: TextView

    override fun onFinishInflate() {
        super.onFinishInflate()
        editText = getChildAt(1) as EditText
        errorTextView = getChildAt(2) as TextView
        editText.addTextChangedListener {
            onTextChange?.invoke(it?.toString().orEmpty())
            if (!isTextBeingAssigned) requestRectangleOnScreen(bounds(), true)
        }
        editText.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                requestRectangleOnScreen(bounds(), true)
            } else {
                onFocusChange?.invoke()
            }
        }
        editText.setOnEditorActionListener { _, actionId, _ -> onEditorAction?.invoke(actionId) ?: false }
        updateVisibility()
        updateEnabled()
        updateBackground()
        binding.flow.apply {
            addView(editText)
            addView(errorTextView)
        }
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        updateEnabled()
    }

    private fun updateEnabled() {
        editText.isEnabled = isEnabled
    }

    var text: String
        get() = editText.text?.toString().orEmpty()
        set(value) {
            if (value != editText.text?.toString().orEmpty()) {
                isTextBeingAssigned = true
                try {
                    editText.setText(value)
                } finally {
                    isTextBeingAssigned = false
                }
            }
        }

    var error: String = ""
        set(value) {
            if (value != field) {
                field = value
                errorTextView.text = value
            }
        }

    var errorColor: Int? = null
        set(value) {
            if (value != field) {
                field = value
                errorTextView.setTextColor(value ?: ContextCompat.getColor(context, android.R.color.holo_red_light))
                updateBackground()
            }
        }

    var isErrorEnabled: Boolean = false
        set(value) {
            if (value != field) {
                field = value
                TransitionManager.beginDelayedTransition(this)
                updateVisibility()
                updateBackground()
            }
        }

    private fun updateVisibility() {
        errorTextView.isInvisible = !isErrorEnabled
    }

    private fun updateBackground() {
        val background = if (isErrorEnabled) {
            context.errorBackgroundDrawable(errorColor)
        } else {
            ContextCompat.getDrawable(context, R.drawable.onboarding_edit_text_background)
        }
        val editText = editText
        editText.background = background
    }

    var onTextChange: ((text: String) -> Unit)? = null
    var onFocusChange: (() -> Unit)? = null
    var onEditorAction: ((actionId: Int) -> Boolean)? = null
}

private fun Context.errorBackgroundDrawable(@ColorInt strokeColor: Int? = null): Drawable {
    val strokeWidth = resources.getDimensionPixelSize(R.dimen.bounding_box_stroke_width)
    return (ContextCompat.getDrawable(
        this,
        R.drawable.onboarding_edit_text_background_error
    ) as GradientDrawable).apply {
        setStroke(
            strokeWidth,
            strokeColor ?: ContextCompat.getColor(this@errorBackgroundDrawable, android.R.color.holo_red_light)
        )
    }
}
