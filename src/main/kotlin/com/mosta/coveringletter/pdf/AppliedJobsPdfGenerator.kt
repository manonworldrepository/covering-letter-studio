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

            document.addPage(PDPage(pageSize))
            PDPageContentStream(document, document.getPage(0)).use { stream ->
                var y = pageSize.height - margin

                stream.beginText()
                stream.setFont(font, 16f)
                stream.newLineAtOffset(margin, y)
                stream.showText(toSupportedText(font, "Applied Job"))
                stream.endText()
                y -= 30f

                stream.beginText()
                stream.setFont(font, fontSize)
                stream.newLineAtOffset(margin, y)
                stream.showText(toSupportedText(font, "Job Title: ${job.jobTitle}"))
                stream.endText()
                y -= lineHeight

                stream.beginText()
                stream.setFont(font, fontSize)
                stream.newLineAtOffset(margin, y)
                stream.showText(toSupportedText(font, "Company: ${job.company}"))
                stream.endText()
                y -= lineHeight

                stream.beginText()
                stream.setFont(font, fontSize)
                stream.newLineAtOffset(margin, y)
                stream.showText(toSupportedText(font, "Applied Date: ${job.appliedDate.format(dateFormat)}"))
                stream.endText()
                y -= lineHeight * 2

                if (job.jobDescription.isNotBlank()) {
                    stream.beginText()
                    stream.setFont(font, fontSize)
                    stream.newLineAtOffset(margin, y)
                    stream.showText(toSupportedText(font, "Job Description:"))
                    stream.endText()
                    y -= lineHeight

                    val maxWidth = pageSize.width - margin * 2
                    val lines = wrapText(font, job.jobDescription, maxWidth, fontSize)
                    lines.forEach { line ->
                        stream.beginText()
                        stream.setFont(font, fontSize)
                        stream.newLineAtOffset(margin, y)
                        stream.showText(toSupportedText(font, line))
                        stream.endText()
                        y -= lineHeight
                    }
                }
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
            val colTitleWidth = colCompanyX - colTitleX
            val colCompanyWidth = colDateX - colCompanyX
            val colDateWidth = pageSize.width - margin - colDateX

            document.addPage(PDPage(pageSize))
            PDPageContentStream(document, document.getPage(0)).use { stream ->
                var y = pageSize.height - margin

                stream.beginText()
                stream.setFont(font, headerFontSize)
                stream.newLineAtOffset(margin, y)
                stream.showText(toSupportedText(font, "Applied Jobs (${jobs.size})"))
                stream.endText()
                y -= 30f

                stream.beginText()
                stream.setFont(font, bodyFontSize)
                stream.newLineAtOffset(colTitleX, y)
                stream.showText(toSupportedText(font, "Job Title"))
                stream.newLineAtOffset(colCompanyX - colTitleX, 0f)
                stream.showText(toSupportedText(font, "Company"))
                stream.newLineAtOffset(colDateX - colCompanyX, 0f)
                stream.showText(toSupportedText(font, "Date Applied"))
                stream.endText()
                y -= 5f

                stream.moveTo(margin, y)
                stream.lineTo(pageSize.width - margin, y)
                stream.stroke()
                y -= 10f

                jobs.forEach { job ->
                    if (y < margin) {
                        document.addPage(PDPage(pageSize))
                        y = pageSize.height - margin
                    }

                    val titleText = toSupportedText(font, job.jobTitle)
                    val companyText = toSupportedText(font, job.company)
                    val dateText = toSupportedText(font, job.appliedDate.format(dateFormat))

                    stream.beginText()
                    stream.setFont(font, bodyFontSize)
                    stream.newLineAtOffset(colTitleX, y)
                    stream.showText(titleText)
                    stream.newLineAtOffset(colCompanyX - colTitleX, 0f)
                    stream.showText(companyText)
                    stream.newLineAtOffset(colDateX - colCompanyX, 0f)
                    stream.showText(dateText)
                    stream.endText()
                    y -= lineHeight
                }
            }
            return ByteArrayOutputStream().use { output ->
                document.save(output)
                output.toByteArray()
            }
        }
    }

    private fun wrapText(font: PDFont, text: String, maxWidth: Float, fontSize: Float): List<String> {
        val lines = mutableListOf<String>()
        var currentLine = ""
        text.split(Regex("\\s+")).forEach { word ->
            val candidate = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (getWidth(font, candidate, fontSize) <= maxWidth) {
                currentLine = candidate
            } else {
                if (currentLine.isNotEmpty()) lines.add(currentLine)
                currentLine = word
            }
        }
        if (currentLine.isNotEmpty()) lines.add(currentLine)
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
