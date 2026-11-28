package com.provguard.web;

import java.util.List;
import java.util.Map;

/** Browser-local display preferences. Only predefined values reach the stylesheet. */
record WorkspaceSettings(String theme, boolean reduceMotion) {
    static final String COOKIE = "provguard_view";
    static final WorkspaceSettings DEFAULT = new WorkspaceSettings("violet", false);
    private static final List<String> THEMES = List.of("violet", "aurora", "projector");

    static WorkspaceSettings read(List<String> headers) {
        if (headers != null) {
            for (String header : headers) {
                for (String cookie : header.split(";")) {
                    String value = cookie.strip();
                    if (value.startsWith(COOKIE + "=")) {
                        String[] parts = value.substring(COOKIE.length() + 1).split(":", -1);
                        if (parts.length == 2 && THEMES.contains(parts[0])
                                && ("system".equals(parts[1]) || "reduce".equals(parts[1]))) {
                            return new WorkspaceSettings(parts[0], "reduce".equals(parts[1]));
                        }
                    }
                }
            }
        }
        return DEFAULT;
    }

    static WorkspaceSettings fromForm(Map<String, String> fields) {
        if ("reset".equals(fields.get("action"))) {
            return DEFAULT;
        }
        String theme = fields.getOrDefault("theme", "");
        String motion = fields.getOrDefault("motion", "");
        if (!THEMES.contains(theme) || !("system".equals(motion) || "reduce".equals(motion))) {
            throw new IllegalArgumentException("Choose a theme and motion preference.");
        }
        return new WorkspaceSettings(theme, "reduce".equals(motion));
    }

    String cookie() {
        return COOKIE + "=" + theme + (reduceMotion ? ":reduce" : ":system")
                + "; Path=/; Max-Age=31536000; SameSite=Lax; HttpOnly";
    }

    String classes() {
        return " theme-" + theme + (reduceMotion ? " reduce-motion" : "");
    }

    String render(boolean saved) {
        return """
                <section class="section settings-page">
                  <p class="eyebrow">WORKSPACE</p><h1>Settings</h1>
                  <p class="section-intro">Adjust the dashboard for your screen or for a classroom projector.
                    Your choices are saved in this browser.</p>
                  %s
                  <form method="post" action="/settings">
                    <fieldset><legend>Appearance</legend>
                      <div class="settings-themes">
                        %s
                        %s
                        %s
                      </div>
                    </fieldset>
                    <fieldset class="settings-motion"><legend>Motion</legend>
                      <label for="motion">Interface animation</label>
                      <select name="motion" id="motion">
                        <option value="system"%s>Follow system preference</option>
                        <option value="reduce"%s>Reduce motion</option>
                      </select>
                    </fieldset>
                    <div class="actions"><button type="submit" class="primary">Save preferences</button>
                      <button type="submit" class="button secondary" name="action" value="reset">Reset to defaults</button></div>
                  </form>
                </section>
                """.formatted(saved ? "<p class=\"saved-note\" role=\"status\">Preferences saved.</p>" : "",
                themeOption("violet", "Violet", "Bright purple with soft highlights."),
                themeOption("aurora", "Aurora", "Cool teal with a brighter accent."),
                themeOption("projector", "Projector", "Higher contrast and larger interface text."),
                reduceMotion ? "" : " selected", reduceMotion ? " selected" : "");
    }

    private String themeOption(String value, String label, String description) {
        return """
                <label class="theme-option">
                  <input type="radio" name="theme" value="%s"%s>
                  <span class="theme-swatch theme-%s" aria-hidden="true"><i></i><i></i><i></i></span>
                  <strong>%s</strong><span>%s</span>
                </label>
                """.formatted(value, theme.equals(value) ? " checked" : "", value, label, description);
    }
}
