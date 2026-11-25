package io.github.onprs.universitytoolbox.feature.gpa.impl.domain

/**
 * 支持的 GPA 换算算法标准。
 */
enum class GpaScale(val maxGpa: Double) {
    /** 北大 4.0 算法：60~100 分采用连续曲线 `4 - 3 * (100 - x)^2 / 1600`。 */
    PKU_4_0(maxGpa = 4.0),

    /** 标准 4.0 四段式分级换算。 */
    STANDARD_4_0(maxGpa = 4.0),

    /** WES 常用中国高校 4.0 认证换算（85+ 为 4.0）。 */
    WES_4_0(maxGpa = 4.0),

    /** 常见 4.3 制精细分段换算。 */
    STANDARD_4_3(maxGpa = 4.3),

    /** 高校五分制连续换算：60~100 分映射为 1.0~5.0。 */
    FIVE_POINT_5_0(maxGpa = 5.0),
}

/**
 * 常用等级制成绩快捷预设（对应百分制代表分值）。
 */
enum class LetterGrade(val label: String, val representativeScore: Double) {
    A_PLUS("A+", 95.0),
    A("A", 92.0),
    A_MINUS("A-", 87.0),
    B_PLUS("B+", 83.0),
    B("B", 79.0),
    B_MINUS("B-", 76.0),
    C_PLUS("C+", 73.0),
    C("C", 69.0),
    C_MINUS("C-", 66.0),
    D("D", 62.0),
    F("F", 50.0),
}
