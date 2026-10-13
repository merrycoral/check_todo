package com.merrycoral.checktodo.data

/** 카테고리에 지정할 수 있는 색상(ARGB) 목록과 기본 카테고리의 기본 색상. */
object CategoryPalette {
    val colors: List<Int> = listOf(
        0xFF43A047, // 초록
        0xFF2E7D6B, // 청록
        0xFF1E88E5, // 파랑
        0xFF5C6BC0, // 남색
        0xFF8E24AA, // 보라
        0xFFD81B60, // 분홍
        0xFFE53935, // 빨강
        0xFFFB8C00, // 주황
        0xFFF9A825, // 노랑
        0xFF6D4C41, // 갈색
        0xFF607D8B, // 회색
    ).map { it.toInt() }

    val DEFAULT_COLOR: Int = colors.first()

    /** 기본 제공 카테고리 (이름 → 색상) */
    val DEFAULT_CATEGORIES: List<Pair<String, Int>> = listOf(
        "건강" to 0xFF43A047.toInt(),
        "금융" to 0xFF1E88E5.toInt(),
        "업무" to 0xFF5C6BC0.toInt(),
        "운동" to 0xFFFB8C00.toInt(),
        "뷰티" to 0xFFD81B60.toInt(),
    )
}
