package br.com.soloibiapaba.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "interpretation_parameters", uniqueConstraints = @UniqueConstraint(columnNames = {"crop", "indicator"}))
public class InterpretationParameter {
    @Id private UUID id;
    @Column(nullable = false, length = 120) private String crop;
    @Column(nullable = false, length = 120) private String indicator;
    @Column(nullable = false, precision = 24, scale = 10) private BigDecimal minimum;
    @Column(nullable = false, precision = 24, scale = 10) private BigDecimal maximum;

    protected InterpretationParameter() {}
    public InterpretationParameter(String crop, String indicator, BigDecimal minimum, BigDecimal maximum) {
        this.id = UUID.randomUUID(); this.crop = crop; this.indicator = indicator;
        this.minimum = minimum; this.maximum = maximum;
    }
    public String getIndicator() { return indicator; }
    public BigDecimal getMinimum() { return minimum; }
    public BigDecimal getMaximum() { return maximum; }
}
