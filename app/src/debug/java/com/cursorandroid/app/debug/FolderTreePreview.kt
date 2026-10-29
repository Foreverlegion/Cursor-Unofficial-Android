package com.cursorandroid.app.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.cursorandroid.app.data.repo.LocalFolder
import com.cursorandroid.app.ui.inbox.FolderHeader
import com.cursorandroid.app.ui.inbox.RepoGroupHeader
import com.cursorandroid.app.ui.theme.HomeLayout
import com.cursorandroid.app.ui.theme.LocalHomeLayout

@Preview(name = "Folder tree nested (after)", showBackground = true, widthDp = 400, heightDp = 320)
@Composable
private fun FolderTreeNestedPreview() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxWidth()) {
                val layout = HomeLayout()
                androidx.compose.runtime.CompositionLocalProvider(LocalHomeLayout provides layout) {
                    FolderHeader(
                        folder = LocalFolder(id = "f1", name = "stocky", color = 0xFF84CC16.toInt()),
                        count = 13,
                        iconStore = null,
                        depth = 0,
                        onToggle = {},
                        onRename = {},
                        onDelete = {},
                        onToggleFavorite = {},
                        onColor = {},
                        onIcon = {},
                    )
                    RepoGroupHeader(
                        label = "stocks",
                        subtitle = null,
                        realName = "org/stocks",
                        count = 11,
                        running = 0,
                        expanded = false,
                        favorite = false,
                        color = 0,
                        renamed = false,
                        canMoveUp = false,
                        canMoveDown = false,
                        manualOrder = false,
                        depth = 1,
                        parentFolderName = "stocky",
                        onClick = {},
                        onRename = {},
                        onFavorite = {},
                        onColor = {},
                        onMove = {},
                        onResetOrder = {},
                        onMoveToFolder = {},
                    )
                    RepoGroupHeader(
                        label = "stocky-android",
                        subtitle = null,
                        realName = "org/stocky-android",
                        count = 2,
                        running = 0,
                        expanded = false,
                        favorite = false,
                        color = 0,
                        renamed = false,
                        canMoveUp = false,
                        canMoveDown = false,
                        manualOrder = false,
                        depth = 1,
                        parentFolderName = "stocky",
                        onClick = {},
                        onRename = {},
                        onFavorite = {},
                        onColor = {},
                        onMove = {},
                        onResetOrder = {},
                        onMoveToFolder = {},
                    )
                    RepoGroupHeader(
                        label = "tmp-c1bdd96b0d7f3023",
                        subtitle = null,
                        realName = "tmp-c1bdd96b0d7f3023",
                        count = 1,
                        running = 0,
                        expanded = false,
                        favorite = false,
                        color = 0,
                        renamed = false,
                        canMoveUp = false,
                        canMoveDown = false,
                        manualOrder = false,
                        depth = 0,
                        onClick = {},
                        onRename = {},
                        onFavorite = {},
                        onColor = {},
                        onMove = {},
                        onResetOrder = {},
                        onMoveToFolder = {},
                    )
                }
            }
        }
    }
}

@Preview(name = "Folder tree flat (before bug)", showBackground = true, widthDp = 400, heightDp = 320)
@Composable
private fun FolderTreeFlatPreview() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxWidth()) {
                val layout = HomeLayout()
                androidx.compose.runtime.CompositionLocalProvider(LocalHomeLayout provides layout) {
                    FolderHeader(
                        folder = LocalFolder(id = "f1", name = "stocky", color = 0xFF84CC16.toInt()),
                        count = 13,
                        iconStore = null,
                        depth = 0,
                        onToggle = {},
                        onRename = {},
                        onDelete = {},
                        onToggleFavorite = {},
                        onColor = {},
                        onIcon = {},
                    )
                    RepoGroupHeader(
                        label = "stocks",
                        subtitle = null,
                        realName = "org/stocks",
                        count = 11,
                        running = 0,
                        expanded = false,
                        favorite = false,
                        color = 0,
                        renamed = false,
                        canMoveUp = false,
                        canMoveDown = false,
                        manualOrder = false,
                        depth = 0,
                        onClick = {},
                        onRename = {},
                        onFavorite = {},
                        onColor = {},
                        onMove = {},
                        onResetOrder = {},
                        onMoveToFolder = {},
                    )
                    RepoGroupHeader(
                        label = "stocky-android",
                        subtitle = null,
                        realName = "org/stocky-android",
                        count = 2,
                        running = 0,
                        expanded = false,
                        favorite = false,
                        color = 0,
                        renamed = false,
                        canMoveUp = false,
                        canMoveDown = false,
                        manualOrder = false,
                        depth = 0,
                        onClick = {},
                        onRename = {},
                        onFavorite = {},
                        onColor = {},
                        onMove = {},
                        onResetOrder = {},
                        onMoveToFolder = {},
                    )
                    RepoGroupHeader(
                        label = "tmp-c1bdd96b0d7f3023",
                        subtitle = null,
                        realName = "tmp-c1bdd96b0d7f3023",
                        count = 1,
                        running = 0,
                        expanded = false,
                        favorite = false,
                        color = 0,
                        renamed = false,
                        canMoveUp = false,
                        canMoveDown = false,
                        manualOrder = false,
                        depth = 0,
                        onClick = {},
                        onRename = {},
                        onFavorite = {},
                        onColor = {},
                        onMove = {},
                        onResetOrder = {},
                        onMoveToFolder = {},
                    )
                }
            }
        }
    }
}
