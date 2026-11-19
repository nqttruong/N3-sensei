package com.n3sensei.app.data.importing

/**
 * DEBUG BUILD ONLY. The Contrast Lab preview pack: the AI-written drafts that passed validation but
 * were NOT reviewed by anyone (tools/content/contrast/drafts_tool.py debug-preview), so the whole
 * Contrast Lab can be tried before review. Installed only when the app ships no reviewed Contrast
 * pack; every Contrast screen then shows "NỘI DUNG THỬ NGHIỆM — CHƯA KIỂM DUYỆT".
 *
 * The release source set declares an empty list and has no such asset, so a release APK can never
 * contain or install it. The drafts themselves stay AI_GENERATED.
 */
internal object ContrastPreviewAssets {
    val PATHS: List<String> = listOf("contrast-preview/contrast-n3-preview.json")
}
