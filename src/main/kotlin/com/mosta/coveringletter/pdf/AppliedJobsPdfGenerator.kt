package com.mosta.coveringletter.pdf

import com.mosta.coveringletter.appliedjobs.AppliedJob
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.font.PDType0Font
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.springframework.stereotype.Component
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.format.DateTimeFormatter
import java.text.Normalizer

@Component
class AppliedJobsPdfGenerator {

    private val dateFormat = DateTimeFormatter.ofPattern("dd MMM yyyy")

    fun createSingleJob(job: AppliedJob): ByteArray {
        PDDocument().use { document ->
            val font = loadFont(document)
            val pageSize = PDRectangle.A4
            val margin = 72f
            val fontSize = 11f
            val lineHeight = fontSize * 1.4f
            val maxWidth = pageSize.width - margin * 2

            var page = PDPage(pageSize)
            document.addPage(page)
            var stream: PDPageContentStream? = PDPageContentStream(document, page)
            var y = pageSize.height - margin

            fun closeCurrentStream() {
                stream?.close()
                stream = null
            }

            fun newPage() {
                closeCurrentStream()
                val newPage = PDPage(pageSize)
                document.addPage(newPage)
                stream = PDPageContentStream(document, newPage)
                y = pageSize.height - margin
            }

            fun checkPageBreak(requiredHeight: Float = lineHeight) {
                if (y - requiredHeight < margin) {
                    newPage()
                }
            }

            fun writeTextLine(text: String, size: Float = fontSize, extraSpace: Float = 0f) {
                val lineH = size * 1.4f
                checkPageBreak(lineH + extraSpace)
                val s = stream!!
                s.beginText()
                s.setFont(font, size)
                s.newLineAtOffset(margin, y)
                s.showText(toSupportedText(font, text))
                s.endText()
                y -= (lineH + extraSpace)
            }

            try {
                writeTextLine("Applied Job", 16f, 15f)
                writeTextLine("Job Title: ${job.jobTitle}")
                writeTextLine("Company: ${job.company}")
                writeTextLine("Applied Date: ${job.appliedDate.format(dateFormat)}", fontSize, lineHeight)

                if (job.jobDescription.isNotBlank()) {
                    writeTextLine("Job Description:")
                    val lines = wrapText(font, job.jobDescription, maxWidth, fontSize)
                    lines.forEach { line ->
                        if (line.isEmpty()) {
                            checkPageBreak(lineHeight * 0.6f)
                            y -= lineHeight * 0.6f
                        } else {
                            writeTextLine(line)
                        }
                    }
                }
            } finally {
                closeCurrentStream()
            }

            return ByteArrayOutputStream().use { output ->
                document.save(output)
                output.toByteArray()
            }
        }
    }

    fun createJobList(jobs: List<AppliedJob>): ByteArray {
        PDDocument().use { document ->
            val font = loadFont(document)
            val pageSize = PDRectangle.A4
            val margin = 40f
            val headerFontSize = 14f
            val bodyFontSize = 9f
            val lineHeight = bodyFontSize * 1.4f

            val colTitleX = margin
            val colCompanyX = margin + 160f
            val colDateX = pageSize.width - margin - 120f

            var page = PDPage(pageSize)
            document.addPage(page)
            var stream: PDPageContentStream? = PDPageContentStream(document, page)
            var y = pageSize.height - margin

            fun closeCurrentStream() {
                stream?.close()
                stream = null
            }

            fun drawTableHeader(s: PDPageContentStream) {
                s.beginText()
                s.setFont(font, bodyFontSize)
                s.newLineAtOffset(colTitleX, y)
                s.showText(toSupportedText(font, "Job Title"))
                s.newLineAtOffset(colCompanyX - colTitleX, 0f)
                s.showText(toSupportedText(font, "Company"))
                s.newLineAtOffset(colDateX - colCompanyX, 0f)
                s.showText(toSupportedText(font, "Date Applied"))
                s.endText()
                y -= 5f

                s.moveTo(margin, y)
                s.lineTo(pageSize.width - margin, y)
                s.stroke()
                y -= 10f
            }

            fun newPage() {
                closeCurrentStream()
                val newPage = PDPage(pageSize)
                document.addPage(newPage)
                val newStream = PDPageContentStream(document, newPage)
                stream = newStream
                y = pageSize.height - margin
                drawTableHeader(newStream)
            }

            try {
                val currentStream = stream!!
                currentStream.beginText()
                currentStream.setFont(font, headerFontSize)
                currentStream.newLineAtOffset(margin, y)
                currentStream.showText(toSupportedText(font, "Applied Jobs (${jobs.size})"))
                currentStream.endText()
                y -= 30f

                drawTableHeader(currentStream)

                jobs.forEach { job ->
                    if (y - lineHeight < margin) {
                        newPage()
                    }

                    val s = stream!!
                    val titleText = toSupportedText(font, job.jobTitle)
                    val companyText = toSupportedText(font, job.company)
                    val dateText = toSupportedText(font, job.appliedDate.format(dateFormat))

                    s.beginText()
                    s.setFont(font, bodyFontSize)
                    s.newLineAtOffset(colTitleX, y)
                    s.showText(titleText)
                    s.newLineAtOffset(colCompanyX - colTitleX, 0f)
                    s.showText(companyText)
                    s.newLineAtOffset(colDateX - colCompanyX, 0f)
                    s.showText(dateText)
                    s.endText()
                    y -= lineHeight
                }
            } finally {
                closeCurrentStream()
            }

            return ByteArrayOutputStream().use { output ->
                document.save(output)
                output.toByteArray()
            }
        }
    }

    private fun wrapText(font: PDFont, text: String, maxWidth: Float, fontSize: Float): List<String> {
        val lines = mutableListOf<String>()
        text.lineSequence().forEach { rawLine ->
            val trimmedLine = rawLine.trim()
            if (trimmedLine.isEmpty()) {
                if (lines.isNotEmpty() && lines.last().isNotEmpty()) {
                    lines.add("")
                }
            } else {
                var currentLine = ""
                trimmedLine.split(Regex("\\s+")).forEach { word ->
                    val candidate = if (currentLine.isEmpty()) word else "$currentLine $word"
                    if (getWidth(font, candidate, fontSize) <= maxWidth) {
                        currentLine = candidate
                    } else {
                        if (currentLine.isNotEmpty()) lines.add(currentLine)
                        if (getWidth(font, word, fontSize) > maxWidth) {
                            var brokenWord = ""
                            word.forEach { character ->
                                val extended = brokenWord + character
                                if (getWidth(font, extended, fontSize) > maxWidth && brokenWord.isNotEmpty()) {
                                    lines.add(brokenWord)
                                    brokenWord = character.toString()
                                } else {
                                    brokenWord = extended
                                }
                            }
                            currentLine = brokenWord
                        } else {
                            currentLine = word
                        }
                    }
                }
                if (currentLine.isNotEmpty()) lines.add(currentLine)
            }
        }
        return lines
    }

    private fun getWidth(font: PDFont, text: String, fontSize: Float): Float =
        font.getStringWidth(toSupportedText(font, text)) / 1000 * fontSize

    private fun loadFont(document: PDDocument): PDFont {
        val fontFiles = listOf(
            "C:\\Windows\\Fonts\\arial.ttf",
            "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
            "/usr/share/fonts/truetype/liberation2/LiberationSans-Regular.ttf",
            "/System/Library/Fonts/Supplemental/Arial.ttf"
        )
        val fontFile = fontFiles.map(::File).firstOrNull(File::isFile)
        return if (fontFile != null) {
            PDType0Font.load(document, fontFile)
        } else {
            PDType1Font(Standard14Fonts.FontName.HELVETICA)
        }
    }

    private fun toSupportedText(font: PDFont, text: String): String {
        var candidate = text
        if (font is PDType1Font) {
            candidate = Normalizer.normalize(text, Normalizer.Form.NFKD)
                .replace(Regex("\\p{M}+"), "")
                .replace("’", "'")
                .replace("‘", "'")
                .replace("“", "\"")
                .replace("”", "\"")
                .replace("–", "-")
                .replace("—", "-")
                .replace("•", "-")
        }
        return buildString {
            candidate.forEach { character ->
                val value = character.toString()
                append(if (runCatching { font.encode(value) }.isSuccess) value else "?")
            }
        }
    }
}
