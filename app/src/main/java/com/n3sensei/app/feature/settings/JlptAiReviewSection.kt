package com.n3sensei.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.n3sensei.app.domain.repository.SyncRepository
import com.n3sensei.app.domain.model.JlptReviewItem
import com.n3sensei.app.domain.model.JlptQuestion
import com.n3sensei.app.domain.model.JlptMistakeDetail
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class JlptAiReviewViewModel(private val repository: SyncRepository) : ViewModel() {
    val knowledge = repository.observeKnowledge().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val mistakes = repository.observeMistakes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val reviewItems = repository.observeReviewItems().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val pendingCount = repository.observePendingEventCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    private val _detail = MutableStateFlow<JlptMistakeDetail?>(null)
    val detail = _detail.asStateFlow()

    fun showMistake(question: JlptQuestion) = viewModelScope.launch {
        _detail.value = repository.mistakeDetail(question)
    }

    fun review(item: JlptReviewItem, rating: String) = viewModelScope.launch {
        repository.recordOfflineReview(item, rating)
    }
}

@Composable
fun JlptAiReviewSection(viewModel: JlptAiReviewViewModel) {
    val knowledge by viewModel.knowledge.collectAsStateWithLifecycle()
    val mistakes by viewModel.mistakes.collectAsStateWithLifecycle()
    val reviews by viewModel.reviewItems.collectAsStateWithLifecycle()
    val pending by viewModel.pendingCount.collectAsStateWithLifecycle()
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text("JLPT AI Review", style = MaterialTheme.typography.titleLarge)
        Text("Kho kiến thức yếu · ${knowledge.size} mục")
        knowledge.forEach { item ->
            val linkedMistakes = item.linkedQuestionIds.count { linkedId -> mistakes.any { it.id == linkedId } }
            Text("${item.type}: ${item.title} — ${item.content} · $linkedMistakes câu sai liên quan", Modifier.padding(vertical = 4.dp))
        }
        Text("Câu đã làm sai · ${mistakes.size} câu", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleMedium)
        mistakes.forEach { question ->
            Column(Modifier.fillMaxWidth().clickable { viewModel.showMistake(question) }.padding(vertical = 6.dp)) {
            Text("${question.number}. ${question.text}")
                question.options.forEachIndexed { i, option -> Text("${'A' + i}. $option") }
            }
        }
        detail?.let { item ->
            val answerIndex = item.selectedIndex
            val correctIndex = item.question.verifiedAnswerIndex
            Text("Bạn chọn: ${answerIndex?.let { item.question.options.getOrNull(it) } ?: "—"} · Đáp án đã xác minh: ${correctIndex?.let { item.question.options.getOrNull(it) } ?: "Chưa xác minh"}")
            item.approvedExplanationJson?.let { Text("Lời giải đã duyệt: $it", Modifier.padding(top = 4.dp)) }
        }
        Text("Ôn tập · ${reviews.size} mục · $pending event đang chờ đồng bộ", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleMedium)
        val questionById = mistakes.associateBy { it.id }
        val knowledgeById = knowledge.associateBy { it.id }
        reviews.forEach { item ->
            Text("${item.type} · ${item.questionId?.let { questionById[it]?.text } ?: item.knowledgeId?.let { knowledgeById[it]?.title } ?: item.id}", Modifier.padding(top = 6.dp))
            item.questionId?.let(questionById::get)?.options?.forEachIndexed { i, option -> Text("${'A' + i}. $option") }
            Row {
                listOf("AGAIN", "HARD", "GOOD", "EASY").forEach { rating ->
                    Button(onClick = { viewModel.review(item, rating) }, Modifier.padding(end = 4.dp)) { Text(rating) }
                }
            }
        }
    }
}
