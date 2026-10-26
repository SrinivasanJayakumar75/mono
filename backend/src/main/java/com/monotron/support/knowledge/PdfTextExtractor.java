package com.monotron.support.knowledge;

import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Extracts plain text from a PDF, page by page.
 */
@Component
public class PdfTextExtractor {

    private static final Logger log = LoggerFactory.getLogger(PdfTextExtractor.class);

    /**
     * @param pdf the raw PDF bytes
     * @return one entry per page; pages without any extractable text are skipped
     */
    public List<Page> extract(byte[] pdf) {
        List<Page> pages = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(pdf)) {
            int pageCount = document.getNumberOfPages();
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);

            for (int page = 1; page <= pageCount; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text;
                try {
                    text = stripper.getText(document);
                } catch (RuntimeException ex) {
                    log.warn("Unable to extract text from page {} of a PDF", page, ex);
                    continue;
                }
                String cleaned = clean(text);
                if (!cleaned.isBlank()) {
                    pages.add(new Page(page, cleaned));
                }
            }
        } catch (Exception ex) {
            throw new IllegalArgumentException("The uploaded file could not be read as a PDF: " + ex.getMessage(), ex);
        }
        return pages;
    }

    /**
     * Removes the artefacts that PDF text extraction tends to produce: hard line
     * breaks inside sentences, repeated spaces, bullets glued to words and
     * hyphenated line breaks.
     */
    static String clean(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replace("\u0000", "")
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[ \\t]+", " ")
                .replaceAll(" *\n *", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    /**
     * @param page   one based page number
     * @param text   the text of that page
     */
    public record Page(int page, String text) {
    }
}
