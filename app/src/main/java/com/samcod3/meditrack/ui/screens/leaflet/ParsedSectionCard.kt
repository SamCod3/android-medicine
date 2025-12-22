package com.samcod3.meditrack.ui.screens.leaflet

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.samcod3.meditrack.domain.model.ContentBlock
import com.samcod3.meditrack.domain.model.ParsedSection
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
@Composable
fun ParsedSectionCard(
    sectionNumber: Int,
    section: ParsedSection,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header
            Text(
                text = "${sectionNumber}. ${cleanSectionTitle(section.title)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Content Blocks
            section.content.forEach { block ->
                RenderContentBlock(block)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun RenderContentBlock(block: ContentBlock) {
    when (block) {
        is ContentBlock.Paragraph -> {
            Text(
                text = block.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        is ContentBlock.Bold -> {
            Text(
                text = block.text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        is ContentBlock.Italic -> {
            Text(
                text = block.text,
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        is ContentBlock.BulletItem -> {
            Row(modifier = Modifier.fillMaxWidth()) {
                BulletPoint()
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = block.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        is ContentBlock.NumberedItem -> {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "${block.number}.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(24.dp)
                )
                Text(
                    text = block.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        is ContentBlock.SubHeading -> {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = block.text,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
             Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun BulletPoint() {
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier = Modifier.size(6.dp).padding(top = 8.dp)) {
        drawCircle(color = color)
    }
}

// Helper to clean section titles (duplicated logic, could be shared util)
private fun cleanSectionTitle(title: String): String {
    return title.replace(Regex("^\\d+\\.?\\d*\\.?\\s*"), "").trim()
}
