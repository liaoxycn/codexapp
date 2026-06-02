package com.codexapp.ui

import com.codexapp.ui.state.clearComposerDraft
import com.codexapp.ui.state.restoreComposerDraft
import com.codexapp.ui.state.updateComposerDrafts
import org.junit.Assert.assertEquals
import org.junit.Test

class ComposerDraftsTest {
    @Test
    fun restoreComposerDraftReturnsThreadDraft() {
        val drafts = mapOf("thread-1" to "draft text")
        assertEquals("draft text", restoreComposerDraft(drafts, "thread-1"))
    }

    @Test
    fun updateComposerDraftsRemovesBlankText() {
        val drafts = mapOf("thread-1" to "draft text")
        assertEquals(emptyMap<String, String>(), updateComposerDrafts(drafts, "thread-1", ""))
    }

    @Test
    fun updateComposerDraftsStoresNonBlankText() {
        val drafts = emptyMap<String, String>()
        assertEquals(mapOf("thread-1" to "next draft"), updateComposerDrafts(drafts, "thread-1", "next draft"))
    }

    @Test
    fun clearComposerDraftKeepsMapWhenThreadMissing() {
        val drafts = mapOf("thread-1" to "draft text")
        assertEquals(drafts, clearComposerDraft(drafts, "thread-2"))
    }
}
