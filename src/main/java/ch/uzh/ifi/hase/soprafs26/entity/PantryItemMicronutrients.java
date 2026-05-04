package ch.uzh.ifi.hase.soprafs26.entity;

import java.io.Serializable;
import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "pantry_item_micronutrients")
public class PantryItemMicronutrients implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pantry_item_id", nullable = false, unique = true)
    private PantryItem pantryItem;

    @Column
    private String packageQuantity;

    @Column(precision = 24, scale = 6)
    private BigDecimal packageGrams;

    @Column(precision = 24, scale = 6)
    private BigDecimal biotin;

    @Column(precision = 24, scale = 6)
    private BigDecimal calcium;

    @Column(precision = 24, scale = 6)
    private BigDecimal chloride;

    @Column(precision = 24, scale = 6)
    private BigDecimal choline;

    @Column(precision = 24, scale = 6)
    private BigDecimal chromium;

    @Column(precision = 24, scale = 6)
    private BigDecimal copper;

    @Column(precision = 24, scale = 6)
    private BigDecimal fluoride;

    @Column(precision = 24, scale = 6)
    private BigDecimal folate;

    @Column(precision = 24, scale = 6)
    private BigDecimal iodine;

    @Column(precision = 24, scale = 6)
    private BigDecimal iron;

    @Column(precision = 24, scale = 6)
    private BigDecimal magnesium;

    @Column(precision = 24, scale = 6)
    private BigDecimal manganese;

    @Column(precision = 24, scale = 6)
    private BigDecimal molybdenum;

    @Column(precision = 24, scale = 6)
    private BigDecimal niacin;

    @Column(precision = 24, scale = 6)
    private BigDecimal pantothenicAcid;

    @Column(precision = 24, scale = 6)
    private BigDecimal phosphorus;

    @Column(precision = 24, scale = 6)
    private BigDecimal potassium;

    @Column(precision = 24, scale = 6)
    private BigDecimal riboflavin;

    @Column(precision = 24, scale = 6)
    private BigDecimal selenium;

    @Column(precision = 24, scale = 6)
    private BigDecimal sodium;

    @Column(precision = 24, scale = 6)
    private BigDecimal thiamin;

    @Column(precision = 24, scale = 6)
    private BigDecimal vitaminA;

    @Column(precision = 24, scale = 6)
    private BigDecimal vitaminB12;

    @Column(precision = 24, scale = 6)
    private BigDecimal vitaminB6;

    @Column(precision = 24, scale = 6)
    private BigDecimal vitaminC;

    @Column(precision = 24, scale = 6)
    private BigDecimal vitaminD;

    @Column(precision = 24, scale = 6)
    private BigDecimal vitaminE;

    @Column(precision = 24, scale = 6)
    private BigDecimal vitaminK;

    @Column(precision = 24, scale = 6)
    private BigDecimal zinc;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public PantryItem getPantryItem() { return pantryItem; }
    public void setPantryItem(PantryItem pantryItem) { this.pantryItem = pantryItem; }

    public String getPackageQuantity() { return packageQuantity; }
    public void setPackageQuantity(String packageQuantity) { this.packageQuantity = packageQuantity; }

    public BigDecimal getPackageGrams() { return packageGrams; }
    public void setPackageGrams(BigDecimal packageGrams) { this.packageGrams = packageGrams; }

    public BigDecimal getBiotin() { return biotin; }
    public void setBiotin(BigDecimal biotin) { this.biotin = biotin; }

    public BigDecimal getCalcium() { return calcium; }
    public void setCalcium(BigDecimal calcium) { this.calcium = calcium; }

    public BigDecimal getChloride() { return chloride; }
    public void setChloride(BigDecimal chloride) { this.chloride = chloride; }

    public BigDecimal getCholine() { return choline; }
    public void setCholine(BigDecimal choline) { this.choline = choline; }

    public BigDecimal getChromium() { return chromium; }
    public void setChromium(BigDecimal chromium) { this.chromium = chromium; }

    public BigDecimal getCopper() { return copper; }
    public void setCopper(BigDecimal copper) { this.copper = copper; }

    public BigDecimal getFluoride() { return fluoride; }
    public void setFluoride(BigDecimal fluoride) { this.fluoride = fluoride; }

    public BigDecimal getFolate() { return folate; }
    public void setFolate(BigDecimal folate) { this.folate = folate; }

    public BigDecimal getIodine() { return iodine; }
    public void setIodine(BigDecimal iodine) { this.iodine = iodine; }

    public BigDecimal getIron() { return iron; }
    public void setIron(BigDecimal iron) { this.iron = iron; }

    public BigDecimal getMagnesium() { return magnesium; }
    public void setMagnesium(BigDecimal magnesium) { this.magnesium = magnesium; }

    public BigDecimal getManganese() { return manganese; }
    public void setManganese(BigDecimal manganese) { this.manganese = manganese; }

    public BigDecimal getMolybdenum() { return molybdenum; }
    public void setMolybdenum(BigDecimal molybdenum) { this.molybdenum = molybdenum; }

    public BigDecimal getNiacin() { return niacin; }
    public void setNiacin(BigDecimal niacin) { this.niacin = niacin; }

    public BigDecimal getPantothenicAcid() { return pantothenicAcid; }
    public void setPantothenicAcid(BigDecimal pantothenicAcid) { this.pantothenicAcid = pantothenicAcid; }

    public BigDecimal getPhosphorus() { return phosphorus; }
    public void setPhosphorus(BigDecimal phosphorus) { this.phosphorus = phosphorus; }

    public BigDecimal getPotassium() { return potassium; }
    public void setPotassium(BigDecimal potassium) { this.potassium = potassium; }

    public BigDecimal getRiboflavin() { return riboflavin; }
    public void setRiboflavin(BigDecimal riboflavin) { this.riboflavin = riboflavin; }

    public BigDecimal getSelenium() { return selenium; }
    public void setSelenium(BigDecimal selenium) { this.selenium = selenium; }

    public BigDecimal getSodium() { return sodium; }
    public void setSodium(BigDecimal sodium) { this.sodium = sodium; }

    public BigDecimal getThiamin() { return thiamin; }
    public void setThiamin(BigDecimal thiamin) { this.thiamin = thiamin; }

    public BigDecimal getVitaminA() { return vitaminA; }
    public void setVitaminA(BigDecimal vitaminA) { this.vitaminA = vitaminA; }

    public BigDecimal getVitaminB12() { return vitaminB12; }
    public void setVitaminB12(BigDecimal vitaminB12) { this.vitaminB12 = vitaminB12; }

    public BigDecimal getVitaminB6() { return vitaminB6; }
    public void setVitaminB6(BigDecimal vitaminB6) { this.vitaminB6 = vitaminB6; }

    public BigDecimal getVitaminC() { return vitaminC; }
    public void setVitaminC(BigDecimal vitaminC) { this.vitaminC = vitaminC; }

    public BigDecimal getVitaminD() { return vitaminD; }
    public void setVitaminD(BigDecimal vitaminD) { this.vitaminD = vitaminD; }

    public BigDecimal getVitaminE() { return vitaminE; }
    public void setVitaminE(BigDecimal vitaminE) { this.vitaminE = vitaminE; }

    public BigDecimal getVitaminK() { return vitaminK; }
    public void setVitaminK(BigDecimal vitaminK) { this.vitaminK = vitaminK; }

    public BigDecimal getZinc() { return zinc; }
    public void setZinc(BigDecimal zinc) { this.zinc = zinc; }
}
