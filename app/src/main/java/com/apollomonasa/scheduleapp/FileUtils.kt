package com.apollomonasa.scheduleapp

import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.InputStream

// 这是一个顶层函数，可以在App的任何地方调用
suspend fun processSingleExcelFile(inputStream: InputStream): List<Person> {
    return try {
        val workbook = WorkbookFactory.create(inputStream)
        val sheet = workbook.getSheetAt(0)
        (sheet.firstRowNum + 1..sheet.lastRowNum).mapNotNull { i ->
            val row = sheet.getRow(i)
            if (row != null) {
                val formatter = DataFormatter()
                val name = formatter.formatCellValue(row.getCell(0)).trim()
                val studentId = formatter.formatCellValue(row.getCell(1)).trim()
                val classTime = formatter.formatCellValue(row.getCell(2)).trim()
                if (name.isNotBlank() || studentId.isNotBlank()) {
                    RawScheduleEntry(name, studentId, classTime)
                } else null
            } else null
        }
            .also { workbook.close() }
            .filter { it.studentId.isNotBlank() }
            .groupBy { it.studentId }
            .map { (studentId, entries) ->
                Person(
                    studentId = studentId,
                    name = entries.first().name,
                    allClassTimes = entries.joinToString(";") { it.classTimeString }
                )
            }
    } catch (e: Exception) {
        e.printStackTrace()
        emptyList()
    }
}