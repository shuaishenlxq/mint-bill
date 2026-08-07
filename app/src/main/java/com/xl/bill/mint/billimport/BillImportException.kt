package com.xl.bill.mint.billimport

/** 账单导入链路统一异常（message 为用户可读文案） */
class BillImportException(message: String, cause: Throwable? = null) : Exception(message, cause)
