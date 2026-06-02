package com.codexapp.ui.composer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codexapp.model.ComposerFile
import com.codexapp.ui.theme.CodexTheme

@Composable
internal fun FilePickerPanel(
    query: String,
    files: List<ComposerFile>,
    hasProject: Boolean,
    projectCwd: String,
    onQueryChange: (String) -> Unit,
    onSelect: (ComposerFile) -> Unit
) {
    val filtered = filterComposerFiles(files, query)
    val tree = buildComposerFileTree(filtered, projectCwd)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CodexTheme.colors.surface)
            .border(1.dp, CodexTheme.colors.border.copy(alpha = 0.82f), RoundedCornerShape(18.dp))
            .padding(8.dp)
            .heightIn(max = 210.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        PanelHeader(title = "文件", subtitle = "仅显示当前项目内文件")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(CodexTheme.colors.surfaceSubtle)
                .border(1.dp, CodexTheme.colors.border, RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = CodexTheme.colors.textSecondary,
                modifier = Modifier.size(15.dp)
            )
            Spacer(Modifier.width(7.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = CodexTheme.colors.textPrimary,
                    fontSize = ComposerTextSize,
                    lineHeight = ComposerTextLineHeight,
                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("file_picker_search_field"),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 28.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (query.isBlank()) {
                            Text(
                                "搜索文件",
                                color = CodexTheme.colors.textTertiary,
                                fontSize = ComposerTextSize,
                                lineHeight = ComposerTextLineHeight
                            )
                        }
                        innerTextField()
                    }
                }
            )
        }

        when {
            !hasProject -> FileRow(title = "当前会话无项目", supporting = "只能选择项目内文件", icon = Icons.Filled.FolderOff)
            filtered.isEmpty() -> FileRow(title = "没有匹配的文件", supporting = "修改搜索词", icon = Icons.Filled.Search)
            else -> FileTree(nodes = tree, onSelect = onSelect)
        }
    }
}

internal fun filterComposerFiles(files: List<ComposerFile>, query: String): List<ComposerFile> {
    val trimmedQuery = query.trim()
    return files
        .asSequence()
        .filterNot(::isExcludedComposerFile)
        .filter { file ->
            trimmedQuery.isBlank() ||
                file.label.contains(trimmedQuery, ignoreCase = true) ||
                file.path.contains(trimmedQuery, ignoreCase = true)
        }
        .toList()
}

internal data class ComposerFileDisplay(
    val file: ComposerFile,
    val name: String,
    val directory: String,
    val depth: Int
)

internal sealed interface ComposerFileTreeNode {
    data class Directory(
        val name: String,
        val depth: Int,
        val children: List<ComposerFileTreeNode>
    ) : ComposerFileTreeNode

    data class File(
        val display: ComposerFileDisplay
    ) : ComposerFileTreeNode
}

internal fun buildComposerFileTree(
    files: List<ComposerFile>,
    projectCwd: String = ""
): List<ComposerFileTreeNode> {
    val builder = ComposerFileTreeBuilder()
    files.map { composerFileDisplay(it, projectCwd) }.forEach(builder::add)
    return builder.build()
}

internal fun composerFileDisplay(file: ComposerFile, projectCwd: String = ""): ComposerFileDisplay {
    val relative = normalizeComposerFileLabel(file.label.ifBlank { file.path }, projectCwd)
    val normalized = relative.replace('\\', '/').trim('/')
    val name = normalized.substringAfterLast('/')
    val directory = normalized.substringBeforeLast('/', missingDelimiterValue = "")
    return ComposerFileDisplay(
        file = file,
        name = name.ifBlank { "/" },
        directory = directory,
        depth = normalized.count { it == '/' }
    )
}

internal fun normalizeComposerFileLabel(value: String, projectCwd: String = ""): String {
    val trimmed = value.trim().replace('\\', '/')
    val normalizedCwd = projectCwd.trim().replace('\\', '/').trimEnd('/')
    if (normalizedCwd.isNotBlank() && trimmed.startsWith("$normalizedCwd/", ignoreCase = true)) {
        return trimmed.removePrefix("$normalizedCwd/")
    }
    val marker = "/codexapp/"
    val markerIndex = trimmed.indexOf(marker, ignoreCase = true)
    return if (markerIndex >= 0) trimmed.substring(markerIndex + marker.length) else trimmed
}

internal fun isExcludedComposerFile(file: ComposerFile): Boolean {
    val segments = file.label.replace('\\', '/').split('/').filter { it.isNotBlank() }
    if (segments.any { it.startsWith(".") && it != ".codex" }) {
        return true
    }
    return segments.any { it in setOf("node_modules", ".git", "build", "dist", "out", "target", ".gradle") }
}

@Composable
private fun FileTree(nodes: List<ComposerFileTreeNode>, onSelect: (ComposerFile) -> Unit) {
    nodes.forEach { node ->
        when (node) {
            is ComposerFileTreeNode.Directory -> DirectoryNode(node, onSelect)
            is ComposerFileTreeNode.File -> FileRow(
                title = node.display.name,
                supporting = if (node.display.directory.isBlank()) "/" else node.display.directory,
                icon = Icons.Filled.Description,
                depth = node.display.depth,
                onClick = { onSelect(node.display.file) }
            )
        }
    }
}

@Composable
private fun DirectoryNode(
    node: ComposerFileTreeNode.Directory,
    onSelect: (ComposerFile) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (4 + node.depth * 12).dp, end = 4.dp, top = 4.dp, bottom = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.Folder,
            contentDescription = null,
            tint = CodexTheme.colors.textTertiary,
            modifier = Modifier.size(13.dp)
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = node.name.ifBlank { "/" },
            color = CodexTheme.colors.textSecondary,
            fontSize = ComposerMetaTextSize,
            lineHeight = ComposerMetaLineHeight,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
    node.children.forEach { child ->
        when (child) {
            is ComposerFileTreeNode.Directory -> DirectoryNode(child, onSelect)
            is ComposerFileTreeNode.File -> FileRow(
                title = child.display.name,
                supporting = if (child.display.directory.isBlank()) "/" else child.display.directory,
                icon = Icons.Filled.Description,
                depth = child.display.depth,
                onClick = { onSelect(child.display.file) }
            )
        }
    }
}

@Composable
private fun FileRow(
    title: String,
    supporting: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    depth: Int = 0,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CodexTheme.colors.surfaceSubtle)
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .defaultMinSize(minHeight = 46.dp)
            .padding(start = (12 + depth * 12).dp, end = 12.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = CodexTheme.colors.textSecondary,
            modifier = Modifier.size(17.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = CodexTheme.colors.textPrimary,
                fontSize = ComposerTextSize,
                lineHeight = ComposerTextLineHeight,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = supporting,
                color = CodexTheme.colors.textSecondary,
                fontSize = ComposerMetaTextSize,
                lineHeight = ComposerMetaLineHeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 260.dp)
            )
        }
    }
}

private class ComposerFileTreeBuilder {
    private val root = MutableComposerDirectoryNode(
        name = "/",
        depth = 0
    )

    fun add(display: ComposerFileDisplay) {
        val parts = display.directory.split('/').filter { it.isNotBlank() }
        root.add(display, parts)
    }

    fun build(): List<ComposerFileTreeNode> {
        return listOf(root.toImmutable())
    }
}

private class MutableComposerDirectoryNode(
    val name: String,
    val depth: Int
) {
    private val directories = linkedMapOf<String, MutableComposerDirectoryNode>()
    private val files = mutableListOf<ComposerFileDisplay>()

    fun add(display: ComposerFileDisplay, remainingPath: List<String>) {
        if (remainingPath.isEmpty()) {
            files += display
            return
        }
        val nextName = remainingPath.first()
        val child = directories.getOrPut(nextName) {
            MutableComposerDirectoryNode(
                name = nextName,
                depth = depth + 1
            )
        }
        child.add(display, remainingPath.drop(1))
    }

    fun toImmutable(): ComposerFileTreeNode.Directory {
        val children = buildList {
            addAll(directories.values.sortedBy { it.name }.map { it.toImmutable() })
            addAll(files.sortedWith(compareBy<ComposerFileDisplay> { it.directory }.thenBy { it.name }).map {
                ComposerFileTreeNode.File(it)
            })
        }
        return ComposerFileTreeNode.Directory(
            name = name,
            depth = depth,
            children = children
        )
    }
}
