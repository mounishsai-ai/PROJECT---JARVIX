package com.example.aisecretary.ui.routine

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aisecretary.data.DailyRoutine
import com.example.aisecretary.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineScreen(
    viewModel: RoutineViewModel,
    onBack: () -> Unit
) {
    val routines by viewModel.routines.collectAsState()

    var dayOfWeek by remember { mutableStateOf("") }
    var startTime by remember { mutableStateOf("") }
    var endTime by remember { mutableStateOf("") }
    var activityName by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Daily Directives", color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = ArcBlue)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SurfaceNavy.copy(alpha = 0.95f)
                )
            )
        },
        containerColor = DeepNavy
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (routines.isEmpty()) {
                    item {
                        Text(
                            "No directives active.",
                            color = TextSecondary,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
                items(routines, key = { it.id }) { routine ->
                    RoutineCard(routine, onDelete = { viewModel.deleteRoutine(it) })
                }
            }

            // Glowing Input Form
            Card(
                colors = CardDefaults.cardColors(containerColor = CardNavy),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        Brush.verticalGradient(listOf(ArcBlue.copy(0.5f), Color.Transparent)),
                        RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                    )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("NEW DIRECTIVE", color = IronGold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        JarvisTextField(
                            value = dayOfWeek,
                            onValueChange = { dayOfWeek = it },
                            label = "Day",
                            modifier = Modifier.weight(1f)
                        )
                        JarvisTextField(
                            value = activityName,
                            onValueChange = { activityName = it },
                            label = "Activity",
                            modifier = Modifier.weight(1.5f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        JarvisTextField(
                            value = startTime,
                            onValueChange = { startTime = it },
                            label = "Start (e.g. 09:00)",
                            modifier = Modifier.weight(1f)
                        )
                        JarvisTextField(
                            value = endTime,
                            onValueChange = { endTime = it },
                            label = "End (e.g. 16:00)",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Button(
                        onClick = {
                            viewModel.addRoutine(dayOfWeek, startTime, endTime, activityName)
                            dayOfWeek = ""
                            startTime = ""
                            endTime = ""
                            activityName = ""
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ArcBlue, contentColor = DeepNavy),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Text("INITIALIZE DIRECTIVE", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun RoutineCard(routine: DailyRoutine, onDelete: (Int) -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardNavyElevated),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(routine.activityName, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(4.dp))
                Text("${routine.dayOfWeek}  //  ${routine.startTime} - ${routine.endTime}", color = AquaAccent, fontSize = 13.sp)
            }
            IconButton(onClick = { onDelete(routine.id) }) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = ErrorRed)
            }
        }
    }
}

@Composable
private fun JarvisTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = TextMuted, fontSize = 12.sp) },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = ArcBlue,
            unfocusedBorderColor = ArcBlueDim,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextSecondary,
            cursorColor = ArcBlue
        ),
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        modifier = modifier
    )
}
