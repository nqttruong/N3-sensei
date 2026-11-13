package com.n3sensei.app.domain.model

data class JlptKnowledge(val id: String, val type: String, val title: String, val content: String, val linkedQuestionIds: List<String>)
data class JlptQuestion(val id: String, val number: Int, val text: String, val options: List<String>, val verifiedAnswerIndex: Int?)
data class JlptReviewItem(val id: String, val serverId: String, val type: String, val questionId: String?, val knowledgeId: String?, val intervalMinutes: Long, val stepIndex: Int?)
data class JlptMistakeDetail(val question: JlptQuestion, val selectedIndex: Int?, val approvedExplanationJson: String?)
