package com.monotron.support.widget.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Create or update request for widget help-center content.
 */
public record WidgetContentRequest(
        @NotBlank(message = "Choose a content type")
        @Pattern(regexp = "BLOG|FAQ|ANNOUNCEMENT",
                message = "Content type must be BLOG, FAQ or ANNOUNCEMENT")
        String kind,

        @NotBlank(message = "Title is required")
        @Size(max = 300, message = "Title must be 300 characters or fewer")
        String title,

        @Size(max = 500, message = "Summary must be 500 characters or fewer")
        String summary,

        @NotBlank(message = "Body is required")
        @Size(max = 20000, message = "Body must be 20,000 characters or fewer")
        String body,

        Boolean published) {
}
