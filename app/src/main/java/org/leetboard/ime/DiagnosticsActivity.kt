package org.leetboard.ime

import android.os.Bundle
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity

class DiagnosticsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        content.addView(label("IME smoke and behavior checks"))
        content.addView(label("Use this screen to test typing, modifier keys, Enter actions, privacy suppression, rotation, and landscape numpad mode."))

        content.addView(label("Normal text"))
        content.addView(field("Type here", InputType.TYPE_CLASS_TEXT, EditorInfo.IME_ACTION_DONE))

        content.addView(label("Terminal/editor combos"))
        content.addView(field("Try Ctrl-C, Ctrl-D, Tab, Esc, arrows", InputType.TYPE_CLASS_TEXT, EditorInfo.IME_ACTION_NONE))

        content.addView(label("Multiline"))
        content.addView(field("Line one\nLine two", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE, EditorInfo.IME_ACTION_NONE))

        content.addView(label("Search action"))
        content.addView(field("search query", InputType.TYPE_CLASS_TEXT, EditorInfo.IME_ACTION_SEARCH))

        content.addView(label("URL"))
        content.addView(field("https://example.org", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI, EditorInfo.IME_ACTION_GO))

        content.addView(label("Email"))
        content.addView(field("user@example.org", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, EditorInfo.IME_ACTION_SEND))

        content.addView(label("Number"))
        content.addView(field("1337", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL, EditorInfo.IME_ACTION_NEXT))

        content.addView(label("Password"))
        content.addView(field("password", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD, EditorInfo.IME_ACTION_DONE))

        content.addView(label("No personalized learning"))
        content.addView(
            field(
                "private note",
                InputType.TYPE_CLASS_TEXT,
                EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING,
            ),
        )

        content.addView(Button(this).apply {
            text = "Finish diagnostics"
            setOnClickListener { finish() }
        })

        setContentView(ScrollView(this).apply { addView(content) })
    }

    private fun label(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 16f
            setPadding(0, 24, 0, 6)
        }
    }

    private fun field(hint: String, inputType: Int, imeOptions: Int): EditText {
        return EditText(this).apply {
            this.hint = hint
            this.inputType = inputType
            this.imeOptions = imeOptions
            minLines = if (inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0) 3 else 1
            setSingleLine(inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE == 0)
        }
    }
}
