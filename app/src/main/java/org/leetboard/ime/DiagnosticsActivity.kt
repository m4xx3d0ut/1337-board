package org.leetboard.ime

import android.os.Bundle
import android.text.InputType
import android.graphics.Typeface
import android.content.Intent
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import org.leetboard.ime.remote.BluetoothHidSupport

class DiagnosticsActivity : ComponentActivity() {
    private lateinit var glideSnapshot: TextView
    private lateinit var bluetoothSnapshot: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        content.addView(label("IME smoke and behavior checks"))
        content.addView(label("Use this screen to test typing, modifier keys, Enter actions, privacy suppression, rotation, and landscape numpad mode."))

        content.addView(label("Glide debug snapshot"))
        glideSnapshot = TextView(this).apply {
            text = glideDebugText()
            textSize = 13f
            typeface = Typeface.MONOSPACE
            setPadding(0, 6, 0, 6)
        }
        content.addView(glideSnapshot)
        content.addView(Button(this).apply {
            text = "Refresh glide debug"
            setOnClickListener { glideSnapshot.text = glideDebugText() }
        })
        content.addView(Button(this).apply {
            text = "Share glide debug"
            setOnClickListener { shareGlideDebug() }
        })

        content.addView(label("Bluetooth remote debug"))
        bluetoothSnapshot = TextView(this).apply {
            text = BluetoothHidSupport.diagnosticSummary(this@DiagnosticsActivity)
            textSize = 13f
            typeface = Typeface.MONOSPACE
            setPadding(0, 6, 0, 6)
        }
        content.addView(bluetoothSnapshot)
        content.addView(Button(this).apply {
            text = "Refresh Bluetooth debug"
            setOnClickListener {
                bluetoothSnapshot.text = BluetoothHidSupport.diagnosticSummary(this@DiagnosticsActivity)
            }
        })

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

    private fun glideDebugText(): String {
        val debugFile = filesDir.resolve(GLIDE_DEBUG_SNAPSHOT_FILE)
        val modelFile = filesDir.resolve(GLIDE_USER_LANGUAGE_MODEL_FILE)
        val wordCount = assets.open(GLIDE_WORDS_ASSET).bufferedReader().useLines { lines -> lines.count() }
        val modelCount = if (modelFile.isFile) modelFile.useLines { lines -> lines.count() } else 0
        val snapshot = if (debugFile.isFile) {
            debugFile.readText()
        } else {
            "No glide has been recorded yet."
        }
        return buildString {
            appendLine("bundledWords=$wordCount")
            appendLine("userModelRows=$modelCount")
            appendLine()
            append(snapshot)
        }
    }

    private fun shareGlideDebug() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "1337 Board glide debug")
            putExtra(Intent.EXTRA_TEXT, glideDebugText())
        }
        startActivity(Intent.createChooser(intent, "Share glide debug"))
    }

    private companion object {
        const val GLIDE_DEBUG_SNAPSHOT_FILE = "glide_debug_snapshot.txt"
        const val GLIDE_USER_LANGUAGE_MODEL_FILE = "glide_user_language_model.tsv"
        const val GLIDE_WORDS_ASSET = "glide_words_en.txt"
    }
}
