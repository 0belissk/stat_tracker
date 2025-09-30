package com.vsm.api.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.Map;

public class ReportRequest {

    @NotBlank
    private String playerId;

    @Email
    @NotBlank
    private String playerEmail;

    @NotEmpty
    private Map<@NotBlank String, @NotBlank String> categories;

    public String getPlayerId() { return playerId; }
    public void setPlayerId(String playerId) { this.playerId = playerId; }

    public String getPlayerEmail() { return playerEmail; }
    public void setPlayerEmail(String playerEmail) { this.playerEmail = playerEmail; }

    public Map<String, String> getCategories() { return categories; }
    public void setCategories(Map<String, String> categories) { this.categories = categories; }
}
