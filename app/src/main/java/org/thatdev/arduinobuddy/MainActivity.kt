package org.thatdev.arduinobuddy

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import org.thatdev.arduinobuddy.ui.theme.ArduinoBuddyTheme
import arduinobuddycli.Arduinobuddycli
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize JNI
        Arduinobuddycli.touch()

        // Initialize Arduinobuddycli
        val dataDir = filesDir.absolutePath
        Arduinobuddycli.init(dataDir)

        setContent {
            ArduinoBuddyTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    BoardListScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun BoardListScreen(modifier: Modifier = Modifier) {
    var boardList by remember { mutableStateOf("Loading...") }

    // Run the CLI command once when this composable is first launched
    LaunchedEffect(Unit) {
        boardList = withContext(Dispatchers.IO) {
            try {
                Arduinobuddycli.boardList()
            } catch (e: Exception) {
                "Error: ${e.message}"
            }
        }
    }

    val context = LocalContext.current

    Column() {
        Text(
            text = boardList,
            modifier = modifier
        )


        Button(onClick = {
            context.startActivity(
                Intent(context, SerialTestActivity::class.java)
            )
        }) {
            Text("Open Serial Test")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    ArduinoBuddyTheme {
        BoardListScreen()
    }
}
