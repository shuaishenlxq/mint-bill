package com.xl.bill.mint.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 全部账单列表页：多条件交集（时间范围 × 收支类型 × 支付渠道）+ 滚动加载分页。
 *
 * 筛选状态由 Screen 持有（rememberSaveable），通过 [refresh] 传入；
 * VM 只做分页控制器与账单操作（与现有 VM 无参构造风格一致）。
 */
class AllBillsViewModel : ViewModel() {

    data class UiState(
        val items: List<com.xl.bill.mint.data.db.TransactionEntity> = emptyList(),
        val total: Int = 0,
        val loading: Boolean = false,
        val loadingMore: Boolean = false,
        val hasMore: Boolean = false
    )

    private val repo = _root_ide_package_.com.xl.bill.mint.di.ServiceLocator.transactionRepository
    private val categoryDao = _root_ide_package_.com.xl.bill.mint.di.ServiceLocator.appDatabase.categoryDao()

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    val categories: StateFlow<List<com.xl.bill.mint.data.db.CategoryEntity>> = categoryDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var filters = _root_ide_package_.com.xl.bill.mint.ui.filter.BillFilters()
    private var loadJob: Job? = null

    // 单向事件流：备注保存成功提示（与首页一致）
    private val _noteSaved = MutableSharedFlow<Unit>()
    val noteSaved: SharedFlow<Unit> = _noteSaved.asSharedFlow()

    /** 重置并加载首页 + 总数（首次进入 / 筛选变化 / 账单变更后刷新） */
    fun refresh(f: com.xl.bill.mint.ui.filter.BillFilters) {
        filters = f
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.value = _state.value.copy(
                items = emptyList(), loading = true, loadingMore = false, hasMore = false
            )
            try {
                val start = f.range?.first
                val end = f.range?.second
                val page = repo.getFiltered(start, end, f.type, f.channel, f.sort?.sortMode ?: 0,
                    _root_ide_package_.com.xl.bill.mint.ui.filter.PAGE_SIZE, 0)
                val total = repo.countFiltered(start, end, f.type, f.channel)
                _state.value = _state.value.copy(
                    items = page, total = total, loading = false, hasMore = page.size < total
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false)
            }
        }
    }

    /** 追加下一页（滚动到底触发；防并发） */
    fun loadMore() {
        val s = _state.value
        if (s.loading || s.loadingMore || !s.hasMore) return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.value = _state.value.copy(loadingMore = true)
            try {
                val start = filters.range?.first
                val end = filters.range?.second
                val page = repo.getFiltered(
                    start, end, filters.type, filters.channel, filters.sort?.sortMode ?: 0,
                    _root_ide_package_.com.xl.bill.mint.ui.filter.PAGE_SIZE, s.items.size
                )
                _state.value = _state.value.copy(
                    items = _state.value.items + page,
                    loadingMore = false,
                    hasMore = _state.value.items.size < _state.value.total
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(loadingMore = false)
            }
        }
    }

    fun delete(id: Long) = viewModelScope.launch {
        repo.delete(id)
        refresh(filters)
    }

    fun updateCategory(id: Long, categoryId: Long) = viewModelScope.launch {
        repo.updateCategory(id, categoryId)
        refresh(filters)
    }

    fun updateNote(id: Long, note: String?) = viewModelScope.launch {
        repo.updateNote(id, note)
        _noteSaved.emit(Unit)
        refresh(filters)
    }

    /** 快捷新增分类并立刻把指定账单改到新分类（账单详情弹窗「＋ 新建」用） */
    fun addCategoryAndAssign(name: String, icon: String, type: Int, keywords: String, txId: Long) =
        viewModelScope.launch {
            val newId = _root_ide_package_.com.xl.bill.mint.di.ServiceLocator.categoryRepository.addCategory(name, icon, type, keywords)
            repo.updateCategory(txId, newId)
            refresh(filters)
        }
}
