package com.xl.bill.mint.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xl.bill.mint.R
import com.xl.bill.mint.ui.theme.GlassStrokeDark
import com.xl.bill.mint.ui.theme.GlassStrokeLight
import com.xl.bill.mint.ui.theme.ShadowSoft
import java.time.LocalDate
import java.time.YearMonth

/**
 * 时间范围选择底部弹窗：不限 / 今日 / 本周 / 本月 / 今年 / 自定义。
 * - 今日：Material3 DatePicker 单日选择
 * - 本周：‹ › 导航切周（周一起始）
 * - 本月：年导航 + 12 个月网格（快速选某年某月）
 * - 今年：年份列表（快速选某年，选中年 = 1月1日~12月31日）
 * - 自定义：Material3 DateRangePicker 日历范围
 *
 * 确定才回调 [onConfirm]（null 表示不限）；取消/下滑关闭 = 丢弃。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TimeRangePickerSheet(
    currentStart: Long?,
    currentEnd: Long?,
    homeDefault: Boolean,
    onConfirm: (Pair<Long, Long>?) -> Unit,
    onDismiss: () -> Unit
) {
    val initialPreset = remember(currentStart, currentEnd, homeDefault) {
        _root_ide_package_.com.xl.bill.mint.util.TimeRange.decode(currentStart, currentEnd, homeDefault)
    }
    var preset by rememberSaveable { mutableStateOf(initialPreset) }

    // 各模式的初值（按当前生效区间回显）
    val initialDay = remember(currentStart, currentEnd) { _root_ide_package_.com.xl.bill.mint.util.TimeRange.singleDayOf(currentStart, currentEnd) }
    val initialWeekAnchor = remember(currentStart, currentEnd) {
        _root_ide_package_.com.xl.bill.mint.util.TimeRange.weekAnchorOf(currentStart, currentEnd) ?: LocalDate.now()
    }
    val initialMonth = remember(currentStart, currentEnd) {
        _root_ide_package_.com.xl.bill.mint.util.TimeRange.singleDayOf(currentStart, currentEnd)
            ?.let { YearMonth.from(it) } ?: YearMonth.now()
    }
    // 整月/整年初值：单日兜底（整月由 decode 命中，此处精确还原）
    val initialMonthExact = remember(currentStart, currentEnd) { monthOfRange(currentStart, currentEnd) }
    val initialYearExact = remember(currentStart, currentEnd) { yearOfRange(currentStart, currentEnd) }
    val initialYear = initialYearExact ?: initialMonthExact?.year ?: LocalDate.now().year

    var weekAnchor by rememberSaveable { mutableStateOf(initialWeekAnchor) }
    var selectedMonth by rememberSaveable { mutableStateOf(initialMonthExact ?: initialMonth) }
    var selectedYear by rememberSaveable { mutableStateOf(initialYear) }

    // picker 状态提升到弹窗级：切换快捷模式时保留已选值
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialDay?.let { _root_ide_package_.com.xl.bill.mint.util.TimeRange.toPickerUtc(it) }
    )
    // 防御非法初始选择（material3 setSelection 会 require 崩溃）：
    // start > end 时交换；end 无 start 时丢弃
    val rangeStartUtc = currentStart?.let { _root_ide_package_.com.xl.bill.mint.util.TimeRange.toPickerUtc(
        _root_ide_package_.com.xl.bill.mint.util.TimeRange.startDateOf(it)) }
    val rangeEndUtc = currentEnd?.let { _root_ide_package_.com.xl.bill.mint.util.TimeRange.toPickerUtc(
        _root_ide_package_.com.xl.bill.mint.util.TimeRange.inclusiveEndDateOf(it)) }
    val safeRangeStart = if (rangeStartUtc != null && rangeEndUtc != null && rangeStartUtc > rangeEndUtc) {
        rangeEndUtc
    } else {
        rangeStartUtc
    }
    val safeRangeEnd = rangeEndUtc?.takeIf { safeRangeStart != null && it >= safeRangeStart }
    val rangePickerState = rememberDateRangePickerState(
        initialSelectedStartDateMillis = safeRangeStart,
        initialSelectedEndDateMillis = safeRangeEnd
    )

    // 当前选中结果（随 preset 与各模式值实时计算，用于摘要与确定）
    val result: Pair<Long, Long>? = when (preset) {
        _root_ide_package_.com.xl.bill.mint.ui.filter.TimeRangePreset.NONE -> null
        _root_ide_package_.com.xl.bill.mint.ui.filter.TimeRangePreset.TODAY -> datePickerState.selectedDateMillis?.let { utc ->
            _root_ide_package_.com.xl.bill.mint.util.TimeRange.dayRangeOf(_root_ide_package_.com.xl.bill.mint.util.TimeRange.fromPickerUtc(utc))
        }
        _root_ide_package_.com.xl.bill.mint.ui.filter.TimeRangePreset.THIS_WEEK -> _root_ide_package_.com.xl.bill.mint.util.TimeRange.weekRangeOf(weekAnchor)
        _root_ide_package_.com.xl.bill.mint.ui.filter.TimeRangePreset.THIS_MONTH -> _root_ide_package_.com.xl.bill.mint.util.TimeRange.monthRangeOf(selectedMonth)
        _root_ide_package_.com.xl.bill.mint.ui.filter.TimeRangePreset.THIS_YEAR -> _root_ide_package_.com.xl.bill.mint.util.TimeRange.yearRangeOf(selectedYear)
        _root_ide_package_.com.xl.bill.mint.ui.filter.TimeRangePreset.CUSTOM -> {
            val s = rangePickerState.selectedStartDateMillis
            val e = rangePickerState.selectedEndDateMillis
            when {
                s != null && e != null -> {
                    val (a, _) = _root_ide_package_.com.xl.bill.mint.util.TimeRange.dayRangeOf(
                        _root_ide_package_.com.xl.bill.mint.util.TimeRange.fromPickerUtc(s))
                    val (_, b) = _root_ide_package_.com.xl.bill.mint.util.TimeRange.dayRangeOf(
                        _root_ide_package_.com.xl.bill.mint.util.TimeRange.fromPickerUtc(e))
                    a to b
                }
                s != null -> _root_ide_package_.com.xl.bill.mint.util.TimeRange.dayRangeOf(
                    _root_ide_package_.com.xl.bill.mint.util.TimeRange.fromPickerUtc(s))
                else -> null
            }
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.time_range_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )

            // 快捷模式
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PresetChip(preset, _root_ide_package_.com.xl.bill.mint.ui.filter.TimeRangePreset.NONE, stringResource(R.string.time_range_none)) { preset = it }
                PresetChip(preset, _root_ide_package_.com.xl.bill.mint.ui.filter.TimeRangePreset.TODAY, stringResource(R.string.time_range_today)) { preset = it }
                PresetChip(preset, _root_ide_package_.com.xl.bill.mint.ui.filter.TimeRangePreset.THIS_WEEK, stringResource(R.string.time_range_week)) { preset = it }
                PresetChip(preset, _root_ide_package_.com.xl.bill.mint.ui.filter.TimeRangePreset.THIS_MONTH, stringResource(R.string.time_range_month)) { preset = it }
                PresetChip(preset, _root_ide_package_.com.xl.bill.mint.ui.filter.TimeRangePreset.THIS_YEAR, stringResource(R.string.time_range_year)) { preset = it }
                PresetChip(preset, _root_ide_package_.com.xl.bill.mint.ui.filter.TimeRangePreset.CUSTOM, stringResource(R.string.time_range_custom)) { preset = it }
            }

            // 内容区（方圆：卡片 + 大间距，柔和凸起承载卡）
            _root_ide_package_.com.xl.bill.mint.ui.components.SoftSurface(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 20.dp,
                elevation = 4.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    when (preset) {
                        _root_ide_package_.com.xl.bill.mint.ui.filter.TimeRangePreset.NONE -> Text(
                            text = stringResource(
                                if (homeDefault) R.string.time_range_none_hint_home else R.string.time_range_none_hint_all
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // 关键：DatePicker/DateRangePicker 的日历是 LazyColumn（1900-2100 共 2412 个月），
                        // 弹窗外层 verticalScroll 会给出无限高度约束导致 LazyColumn 组合全部月份而 OOM/ANR 崩溃。
                        // 必须用 heightIn 限定有界高度，让 LazyColumn 恢复虚拟化只组合可见月份。
                        _root_ide_package_.com.xl.bill.mint.ui.filter.TimeRangePreset.TODAY -> DatePicker(
                            state = datePickerState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 400.dp),
                            colors = DatePickerDefaults.colors(containerColor = Color.Transparent)
                        )

                        _root_ide_package_.com.xl.bill.mint.ui.filter.TimeRangePreset.THIS_WEEK -> WeekNavigator(
                            anchor = weekAnchor,
                            onAnchorChange = { weekAnchor = it }
                        )

                        _root_ide_package_.com.xl.bill.mint.ui.filter.TimeRangePreset.THIS_MONTH -> MonthGrid(
                            selected = selectedMonth,
                            onSelect = { selectedMonth = it }
                        )

                        _root_ide_package_.com.xl.bill.mint.ui.filter.TimeRangePreset.THIS_YEAR -> YearList(
                            selectedYear = selectedYear,
                            onSelect = { selectedYear = it }
                        )

                        _root_ide_package_.com.xl.bill.mint.ui.filter.TimeRangePreset.CUSTOM -> DateRangePicker(
                            state = rangePickerState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 400.dp),
                            colors = DatePickerDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }
            }

            // 摘要
            Text(
                text = stringResource(
                    R.string.time_range_selected,
                    _root_ide_package_.com.xl.bill.mint.util.TimeRange.label(
                        result?.first,
                        result?.second,
                        homeDefault,
                        recentLabel = stringResource(R.string.time_range_recent3),
                        allTimeLabel = stringResource(R.string.time_range_all_time),
                        todayLabel = stringResource(R.string.time_range_today)
                    )
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 操作
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
                Button(
                    onClick = { onConfirm(result) },
                    shape = MaterialTheme.shapes.large
                ) {
                    Text(stringResource(R.string.confirm))
                }
            }
        }
    }
}

@Composable
private fun PresetChip(
    current: com.xl.bill.mint.ui.filter.TimeRangePreset,
    target: com.xl.bill.mint.ui.filter.TimeRangePreset,
    label: String,
    onSelect: (com.xl.bill.mint.ui.filter.TimeRangePreset) -> Unit
) {
    TimeChip(selected = current == target, label = label, onClick = { onSelect(target) })
}

/**
 * 微立体快捷 chip（方圆手法）：
 * 未选中 = 薄荷 surface 凸起 pill（柔和阴影 + 细描边）；选中 = 薄荷青填充、阴影归零（近似内凹）。
 */
@Composable
private fun TimeChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val strokeColor = if (isDark) GlassStrokeDark else GlassStrokeLight
    val shape = RoundedCornerShape(16.dp)
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        modifier = Modifier.shadow(
            if (selected) 0.dp else 4.dp,
            shape,
            ambientColor = ShadowSoft,
            spotColor = ShadowSoft
        ),
        shape = shape,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surface,
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
        ),
        border = BorderStroke(1.dp, if (selected) Color.Transparent else strokeColor)
    )
}

/** 本周选择：‹ 2026年8月3日 - 8月9日 ›，±7 天导航（保持周一起始对齐） */
@Composable
private fun WeekNavigator(anchor: LocalDate, onAnchorChange: (LocalDate) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { onAnchorChange(anchor.minusDays(7)) }) {
            Icon(Icons.Rounded.ChevronLeft, contentDescription = stringResource(R.string.stat_prev_week))
        }
        Text(
            text = _root_ide_package_.com.xl.bill.mint.util.TimeRange.weekRangeLabel(anchor),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = { onAnchorChange(anchor.plusDays(7)) }) {
            Icon(Icons.Rounded.ChevronRight, contentDescription = stringResource(R.string.stat_next_week))
        }
    }
}

/** 某年某月：年导航 ‹ 2025 › + 12 个月 FilterChip 网格 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MonthGrid(selected: YearMonth, onSelect: (YearMonth) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { onSelect(selected.minusYears(1)) }) {
                Icon(Icons.Rounded.ChevronLeft, contentDescription = stringResource(R.string.stat_prev_year))
            }
            Text(
                text = "${selected.year}年",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { onSelect(selected.plusYears(1)) }) {
                Icon(Icons.Rounded.ChevronRight, contentDescription = stringResource(R.string.stat_next_year))
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..12).forEach { m ->
                TimeChip(
                    selected = m == selected.monthValue,
                    label = "${m}月",
                    onClick = { onSelect(YearMonth.of(selected.year, m)) }
                )
            }
        }
    }
}

/** 年份选择：当前年 ±30 滚动列表，选中高亮并自动定位 */
@Composable
private fun YearList(selectedYear: Int, onSelect: (Int) -> Unit) {
    val nowYear = LocalDate.now().year
    val years = remember(nowYear) { (nowYear - 30)..(nowYear + 10) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (selectedYear - (nowYear - 30)).coerceAtLeast(0))
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
    ) {
        items(count = years.count(), key = { years.elementAt(it) }) { index ->
            val year = years.elementAt(index)
            val selected = year == selectedYear
            Surface(
                onClick = { onSelect(year) },
                shape = RoundedCornerShape(12.dp),
                color = if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.0f)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
            ) {
                Text(
                    text = "${year}年",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onBackground
                    },
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 10.dp)
                )
            }
        }
    }
}

/** 区间是否为整月：是则返回该 YearMonth，否则 null */
private fun monthOfRange(start: Long?, end: Long?): YearMonth? {
    if (start == null || end == null) return null
    val s = _root_ide_package_.com.xl.bill.mint.util.TimeRange.singleDayOf(start, end)
    val sd = _root_ide_package_.com.xl.bill.mint.util.TimeRange.startDateOf(start)
    val ed = _root_ide_package_.com.xl.bill.mint.util.TimeRange.inclusiveEndDateOf(end)
    return if (s == null && sd.dayOfMonth == 1 && sd.plusMonths(1).minusDays(1) == ed) {
        YearMonth.from(sd)
    } else {
        null
    }
}

/** 区间是否为整年：是则返回该年份，否则 null */
private fun yearOfRange(start: Long?, end: Long?): Int? {
    if (start == null || end == null) return null
    val s = _root_ide_package_.com.xl.bill.mint.util.TimeRange.singleDayOf(start, end)
    val sd = _root_ide_package_.com.xl.bill.mint.util.TimeRange.startDateOf(start)
    val ed = _root_ide_package_.com.xl.bill.mint.util.TimeRange.inclusiveEndDateOf(end)
    return if (s == null && sd.dayOfMonth == 1 && sd.dayOfYear == 1 && sd.plusYears(1).minusDays(1) == ed) {
        sd.year
    } else {
        null
    }
}
