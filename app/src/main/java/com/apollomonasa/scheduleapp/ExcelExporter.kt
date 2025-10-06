package com.apollomonasa.scheduleapp

import org.apache.poi.ss.usermodel.*
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.xssf.usermodel.XSSFCellStyle
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.OutputStream
import java.time.DayOfWeek

object ExcelExporter {

    private val sessionTimeMap = mapOf(
        "1-2节" to "8:00-9:40",
        "3-5节" to "10:00-12:00",
        "6-7节" to "13:30-15:00",
        "8-9节" to "15:20-17:00",
        "10-11节" to "18:00-20:00"
    )

    fun export(scheduleResult: ScheduleResult, outputStream: OutputStream) {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("值班表")

        // --- 核心修改点：从结果中动态获取所有涉及的星期和班次 ---
        val days = scheduleResult.keys.map { it.dayOfWeek }.distinct().sorted()
        val sessionStrings = scheduleResult.keys
            .map { "${it.sessions.first}-${it.sessions.last}节" }
            .distinct()
            .sortedBy { it.substringBefore('-').toInt() }

        if (days.isEmpty() || sessionStrings.isEmpty()) {
            // 如果没有排班结果，创建一个简单的提示并返回
            sheet.createRow(0).createCell(0).setCellValue("没有有效的排班结果可供导出。")
            workbook.write(outputStream)
            workbook.close()
            return
        }

        val titleStyle = createTitleStyle(workbook)
        val headerStyle = createHeaderStyle(workbook)
        val dataStyle = createDataStyle(workbook)

        // --- 创建标题行 ---
        val titleRow = sheet.createRow(0)
        titleRow.heightInPoints = 30f
        val titleCell = titleRow.createCell(0)
        titleCell.setCellValue("值班表")
        sheet.addMergedRegion(CellRangeAddress(0, 0, 0, days.size))
        titleCell.cellStyle = titleStyle

        // --- 创建表头行 ---
        val headerRow = sheet.createRow(1)
        headerRow.heightInPoints = 25f
        headerRow.createCell(0).apply {
            setCellValue("星期班序")
            cellStyle = headerStyle
        }
        days.forEachIndexed { index, day ->
            headerRow.createCell(index + 1).apply {
                setCellValue(day.toDisplayName())
                cellStyle = headerStyle
            }
        }

        // --- 填充数据行 ---
        var currentRowIndex = 2
        sessionStrings.forEach { sessionName -> // 使用动态的班次名称
            val startRow = currentRowIndex
            val dataRow = sheet.createRow(startRow)

            val maxPeopleInRow = days.map { day ->
                val sessions = sessionName.split('-').map { it.replace("节", "").toInt() }
                val sessionRange = sessions[0]..sessions.getOrElse(1) { sessions[0] }
                val shift = scheduleResult.keys.find { it.dayOfWeek == day && it.sessions == sessionRange }
                scheduleResult[shift]?.size ?: 1
            }.maxOrNull() ?: 1

            dataRow.heightInPoints = (maxPeopleInRow * 18f).coerceAtLeast(40f)

            val timeCellContent = "$sessionName\n${sessionTimeMap[sessionName] ?: ""}"
            dataRow.createCell(0).apply {
                setCellValue(timeCellContent)
                cellStyle = headerStyle
            }

            val sessions = sessionName.split('-').map { it.replace("节", "").toInt() }
            val sessionRange = sessions[0]..sessions.getOrElse(1) { sessions[0] }

            days.forEachIndexed { colIndex, day ->
                val shift = scheduleResult.keys.find { it.dayOfWeek == day && it.sessions == sessionRange }
                val people = if (shift != null) scheduleResult[shift] else null

                val cellContent = people?.joinToString("\n") { it.person.name } ?: ""

                val cell = dataRow.createCell(colIndex + 1)
                cell.setCellValue(cellContent)
                cell.cellStyle = dataStyle
            }
            currentRowIndex++
        }

        // --- 设置列宽 ---
        sheet.setColumnWidth(0, 14 * 256)
        for (i in 1..days.size) {
            sheet.setColumnWidth(i, 18 * 256)
        }

        workbook.write(outputStream)
        workbook.close()
    }

    private fun createHeaderStyle(workbook: XSSFWorkbook): XSSFCellStyle {
        return workbook.createCellStyle().apply {
            fillForegroundColor = IndexedColors.GREY_25_PERCENT.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
            alignment = HorizontalAlignment.CENTER
            verticalAlignment = VerticalAlignment.CENTER
            wrapText = true
        } as XSSFCellStyle
    }

    private fun createDataStyle(workbook: XSSFWorkbook): XSSFCellStyle {
        return workbook.createCellStyle().apply {
            alignment = HorizontalAlignment.CENTER
            verticalAlignment = VerticalAlignment.CENTER
            wrapText = true
        } as XSSFCellStyle
    }

    private fun createTitleStyle(workbook: XSSFWorkbook): XSSFCellStyle {
        return workbook.createCellStyle().apply {
            alignment = HorizontalAlignment.CENTER
            verticalAlignment = VerticalAlignment.CENTER
            val font = workbook.createFont()
            font.bold = true
            font.fontHeightInPoints = 16.toShort()
            setFont(font)
        } as XSSFCellStyle
    }
}