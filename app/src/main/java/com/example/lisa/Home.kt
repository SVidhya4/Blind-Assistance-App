package com.example.lisa

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun Home(modifier: Modifier, startVoiceService: ()->Unit, stopVoiceService: ()->Unit ){


    var service by remember { mutableStateOf(true)}
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (service){
                Button(onClick = { startVoiceService()
                service=false}) {
                    Text("Start Voice")
                }
            }
            else
            {
                Button(onClick = { stopVoiceService()
                service=true}) {
                    Text("Stop Voice")
                }
            }
        }

}
