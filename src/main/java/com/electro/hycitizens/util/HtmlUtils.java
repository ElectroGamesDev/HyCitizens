package com.electro.hycitizens.util;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import com.hypixel.hytale.logger.HytaleLogger;

public class HtmlUtils {

    /**
     * Sanitizes HTML for HyUI compatibility by converting inline style attributes on <p> and <label> tags
     * into dynamic CSS classes placed in a <style> block. This prevents client-side CustomUI.Set crashes.
     */
    public static String sanitizeHtmlForHyUI(String html) {
        if (html == null || html.isEmpty()) {
            return html;
        }

        // Only process if there's inline style attributes to convert
        if (!html.contains("style=")) {
            return html;
        }

        try {
            Document doc = Jsoup.parseBodyFragment(html);
            Elements styledElements = doc.select("p[style], label[style]");

            if (styledElements.isEmpty()) {
                return html;
            }

            StringBuilder dynamicStyles = new StringBuilder();
            int styleId = 1;

            for (Element element : styledElements) {
                String inlineStyle = element.attr("style");
                if (inlineStyle != null && !inlineStyle.trim().isEmpty()) {
                    // Generate unique class name for this specific element style
                    String className = "hyui-auto-style-" + styleId++;
                    element.addClass(className);
                    element.removeAttr("style");

                    dynamicStyles.append(".").append(className).append(" { ")
                            .append(inlineStyle)
                            .append(" }\n");
                }
            }

            if (dynamicStyles.length() > 0) {
                // Find or create a style element to inject our class definitions
                Element styleElement = doc.select("style").first();
                if (styleElement != null) {
                    styleElement.prepend(dynamicStyles.toString());
                } else {
                    doc.body().prepend("<style>\n" + dynamicStyles + "</style>\n");
                }
            }

            // doc.body().html() returns the body contents without html/head shell tags, perfect for fromHtml
            return doc.body().html();
        } catch (Exception e) {
            // Log and fallback to original html if anything goes wrong
            HytaleLogger.getLogger().atWarning()
                    .log("[HyCitizens] Failed to sanitize HTML template: " + e.getMessage());
            return html;
        }
    }
}
