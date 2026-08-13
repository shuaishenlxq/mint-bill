package com.xl.bill.mint.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xl.bill.mint.R
import com.xl.bill.mint.di.ServiceLocator
import com.xl.bill.mint.parser.BillParseEngine.CustomKeywordScope
import com.xl.bill.mint.parser.BillParseEngine.CustomMatchGroup
import kotlinx.coroutines.launch

/**
 * 自定义匹配关键词弹窗（设置页入口）：系统预设词未命中时，用用户关键词组兜底匹配记账。
 *
 * 一组 = 多个关键词（英文分号分隔，输入时中文分号自动替换为英文）+ 一个作用范围（短信/通知/全部）。
 * 匹配语义：作用范围匹配且组内**全部**关键词都出现在内容中（AND）→ 视为账单记账，否则忽略。
 * 验证码/登录/退订类短信始终不记账（硬拦截，自定义组不可覆盖）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomMatchSheet(onDismiss: () -> Unit) {
    val settings = ServiceLocator.settingsRepository
    val groups by settings.observeCustomMatchGroups()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var selectedScope by remember { mutableStateOf(CustomKeywordScope.SMS) }

    val scopeOptions = listOf(
        CustomKeywordScope.SMS to stringResource(R.string.custom_match_scope_sms),
        CustomKeywordScope.NOTIFICATION to stringResource(R.string.custom_match_scope_notification),
        CustomKeywordScope.ALL to stringResource(R.string.custom_match_scope_all)
    )
    val scopeLabel: (String) -> String = { value -> scopeOptions.first { it.first == value }.second }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_custom_match),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Text(
                text = stringResource(R.string.custom_match_builtin_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.replace('；', ';') },
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.custom_match_input_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    shape = MaterialTheme.shapes.large
                )
                Spacer(Modifier.width(8.dp))
                ScopeDropdown(
                    selected = selectedScope,
                    options = scopeOptions,
                    onSelect = { selectedScope = it }
                )
                Spacer(Modifier.width(4.dp))
                IconButton(
                    onClick = {
                        val keywords = input.replace('；', ';')
                            .split(';')
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                        if (keywords.isNotEmpty()) {
                            val newGroup = CustomMatchGroup(keywords, selectedScope)
                            scope.launch {
                                val current = settings.getCustomMatchGroups()
                                val duplicated = current.any {
                                    it.scope == newGroup.scope &&
                                        it.keywords.toSet() == newGroup.keywords.toSet()
                                }
                                if (!duplicated) settings.setCustomMatchGroups(current + newGroup)
                            }
                            input = ""
                        }
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = stringResource(R.string.custom_match_add),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (groups.isEmpty()) {
                Text(
                    text = stringResource(R.string.custom_match_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 4.dp)
                ) {
                    itemsIndexed(groups) { _, group ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Text(
                                    text = scopeLabel(group.scope),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                            Text(
                                text = group.keywords.joinToString("；"),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = {
                                scope.launch {
                                    val current = settings.getCustomMatchGroups()
                                    settings.setCustomMatchGroups(
                                        current.filterNot {
                                            it.scope == group.scope &&
                                                it.keywords.toSet() == group.keywords.toSet()
                                        }
                                    )
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.custom_match_remove),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 作用范围下拉（短信/通知/全部） */
@Composable
private fun ScopeDropdown(
    selected: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            shape = MaterialTheme.shapes.large
        ) {
            Text(
                text = options.first { it.first == selected }.second,
                style = MaterialTheme.typography.bodyMedium
            )
            Icon(
                imageVector = Icons.Rounded.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onSelect(value)
                        expanded = false
                    }
                )
            }
        }
    }
}
