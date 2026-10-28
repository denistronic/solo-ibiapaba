package br.com.soloibiapaba.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "interpretation_cultures")
public class InterpretationCulture {
    @Id
    @Column(name = "normalized_name", nullable = false, length = 120)
    private String normalizedName;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    protected InterpretationCulture() {}

    public InterpretationCulture(String normalizedName, String displayName) {
        this.normalizedName = normalizedName;
        this.displayName = displayName;
    }

    public String getNormalizedName() { return normalizedName; }
    public String getDisplayName() { return displayName; }
}
